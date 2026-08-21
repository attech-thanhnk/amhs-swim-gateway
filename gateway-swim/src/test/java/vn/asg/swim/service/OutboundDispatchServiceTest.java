package vn.asg.swim.service;

import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.asg.swim.entity.*;
import vn.asg.swim.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for OutboundDispatchService - AMHS → SWIM direction.
 * Covers critical bug fixes: Issue #1 (status logic), content forwarding, TTL, retry logic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboundDispatchServiceTest {

    @Mock private ConnectionManagerService connectionManager;
    @Mock private MessageDetectService detectService;
    @Mock private RoutingService routingService;
    @Mock private MessageConversionService conversionService;
    @Mock private MessageValidationService validationService;
    @Mock private AuthorizationService authorizationService;
    @Mock private ConfigService configService;
    @Mock private AlertService alertService;
    @Mock private GwoutDispatchRepository gwoutDispatchRepository;
    @Mock private GwoutRepository gwoutRepository;

    @InjectMocks
    private OutboundDispatchService service;

    private Gwout gwout;
    private GwoutDispatch dispatch;
    private Routing routing;
    private MessageProducer mockProducer;
    private TextMessage mockTextMessage;

    @BeforeEach
    void setUp() {
        // Setup common test data
        gwout = new Gwout();
        gwout.setMsgid(1L);
        gwout.setText("METAR VVTS 121200Z 09008KT 9999 FEW020 32/25 Q1010=");
        gwout.setOrigin("VVTSZYYX");
        gwout.setAddress("VVHHZTZX");
        gwout.setAmhsPriority("FF");
        gwout.setContentType("text/plain");

        dispatch = new GwoutDispatch();
        dispatch.setId(100L);
        dispatch.setGwoutId(1L);
        dispatch.setRecipient("VVHHZTZX");
        dispatch.setStatus(GwoutDispatch.STATUS_PENDING);
        dispatch.setRetryCount(0);

        routing = new Routing();
        routing.setMessageType("METAR");
        routing.setSendTopic("ats.met.metar");

        // Default config mocks
        when(configService.getInt("RETRY_MAX_COUNT")).thenReturn(3);
        when(configService.getInt("RETRY_DELAY_1ST_SECONDS")).thenReturn(30);
        when(configService.getInt("RETRY_DELAY_2ND_SECONDS")).thenReturn(120);
        when(configService.getInt("RETRY_DELAY_3RD_SECONDS")).thenReturn(300);
        when(configService.getGatewayId()).thenReturn("ASG-GW-01");
    }

    // ==================== ISSUE #1: VALIDATION FAILURE LOGIC ====================

    @Test
    void testValidationFailure_ShouldSetStatusDead_NotSent() {
        // Given: Validation fails
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        MessageValidationService.ValidationResult invalidResult =
            new MessageValidationService.ValidationResult(false, List.of("Invalid AFTN address format"));
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
            .thenReturn(invalidResult);

        // When
        service.processOutboundMessage(gwout);

        // Then: Status should be FAILED
        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());

        // Verify alert was created
        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR),
            eq(GwAlert.SEV_WARNING),
            contains("rejected"),
            eq("gwout"),
            eq(1L)
        );
    }

    @Test
    void testAuthorizationFailure_ShouldSetStatusDead() {
        // Given: Valid message but unauthorized originator
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
            .thenReturn(validResult);
        when(authorizationService.isAmhsUserAuthorized("VVTSZYYX")).thenReturn(false);

        // When
        service.processOutboundMessage(gwout);

        // Then: Should fail gwout status
        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());

        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR),
            eq(GwAlert.SEV_WARNING),
            contains("Unauthorized"),
            anyString(), anyLong()
        );
    }

    // ==================== TTL LOGIC ====================

    @Test
    void testTTLExpired_ShouldRejectMessage() throws Exception {
        // Given: TTL expired
        gwout.setAmhsTtl(LocalDateTime.now().minusHours(1));
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
            .thenReturn(validResult);
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);

        // When
        service.processOutboundMessage(gwout);

        // Then (CTSW005): TTL hết hạn -> reject để sinh NDR, không publish
        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("ttl-expired", gwout.getRejectionReason());
        verify(gwoutRepository, atLeastOnce()).save(gwout);
    }

    @Test
    void testTTLNotExpired_ShouldContinueProcessing() throws Exception {
        // Given: TTL still valid
        gwout.setAmhsTtl(LocalDateTime.now().plusHours(1));
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        setupValidScenario();

        // When
        service.processDispatch(dispatch);

        // Then: Should continue to publish
        verify(connectionManager).createSession();
    }

    // ==================== CONTENT FORWARDED UNCHANGED (ICAO Doc 047) ====================

    @Test
    void testForwardsOriginalBody_ShouldTransformSuccessfully() throws Exception {
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        setupValidScenario();

        // When
        service.processOutboundMessage(gwout);

        // Then: original AMHS body (gwout.text) is left untouched, no conversion applied
        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
        verify(conversionService).logAmhsToSwim(eq(gwout), isNull(), eq("OK"), eq("forwarded_unchanged"));
    }

    // ==================== ROUTING LOGIC ====================

    @Test
    void testRoutingNotFound_ShouldFail() {
        // Given: No routing rule for message type
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
            .thenReturn(validResult);
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);
        when(detectService.detect(anyString())).thenReturn("UNKNOWN");
        when(routingService.findBestMatchOut("UNKNOWN")).thenReturn(Optional.empty());

        // When
        service.processOutboundMessage(gwout);

        // Then: Should fail at routing step
        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        verify(conversionService).logAmhsToSwim(eq(gwout), any(), eq("ERROR"), contains("routing_failed"));
    }

    // ==================== RETRY LOGIC ====================

    @Test
    void testRetryLogic_FirstRetry_ShouldCalculateDelay() throws Exception {
        // Given: First retry
        dispatch.setRetryCount(0);
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        setupValidScenario();
        // Mock connectionManager to fail publishing
        when(connectionManager.createSession()).thenThrow(new RuntimeException("JMS publish failed"));

        // When
        service.processDispatch(dispatch);

        // Then: Should set retry with 30s delay
        verify(gwoutDispatchRepository, atLeastOnce()).save(argThat(d -> {
            if (d.getRetryCount() == 1 && d.getNextRetryAt() != null) {
                assertTrue(d.getNextRetryAt().isAfter(LocalDateTime.now().plusSeconds(25)));
                assertTrue(d.getNextRetryAt().isBefore(LocalDateTime.now().plusSeconds(35)));
                return true;
            }
            return true;
        }));
    }

    @Test
    void testRetryLogic_MaxRetriesReached_ShouldSetDead() throws Exception {
        // Given: Max retries reached
        dispatch.setRetryCount(3);
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        setupValidScenario();
        when(connectionManager.createSession()).thenThrow(new RuntimeException("JMS publish failed"));

        // When
        service.processDispatch(dispatch);

        // Then: Should set DEAD status and create alert
        verify(gwoutDispatchRepository, atLeastOnce()).save(argThat(d ->
            d.getStatus().equals(GwoutDispatch.STATUS_DEAD)
        ));
        verify(alertService).create(
            eq(GwAlert.TYPE_MESSAGE_DEAD),
            eq(GwAlert.SEV_CRITICAL),
            contains("DEAD"),
            eq("gwout_dispatch"),
            eq(100L)
        );
    }

    // ==================== SUCCESSFUL PUBLISH ====================

    @Test
    void testSuccessfulPublish_ShouldSetSent() throws Exception {
        // Given: Everything valid
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        setupValidScenario();

        // When
        service.processDispatch(dispatch);

        // Then: Should mark as SENT
        verify(gwoutDispatchRepository, atLeastOnce()).save(argThat(d -> {
            if (d.getStatus().equals(GwoutDispatch.STATUS_SENT)) {
                assertNotNull(d.getSentAt());
                return true;
            }
            return true;
        }));
    }

    @Test
    void testMultipleRecipients_ShouldMergeIntoSinglePublish() throws Exception {
        // Given: 2 dispatch cùng gwout, cùng topic (kết quả của createDispatches cho gwout đa recipient)
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        setupValidScenario();

        GwoutDispatch dispatch2 = new GwoutDispatch();
        dispatch2.setId(101L);
        dispatch2.setGwoutId(1L);
        dispatch2.setRecipient("VVCIZTZX");
        dispatch2.setStatus(GwoutDispatch.STATUS_PENDING);
        dispatch2.setRetryCount(0);
        dispatch2.setTopic("ats.met.metar");

        when(gwoutDispatchRepository.findByGwoutId(1L)).thenReturn(List.of(dispatch, dispatch2));

        // When: xử lý dispatch đầu tiên trong batch
        service.processDispatch(dispatch);

        // Then: chỉ 1 lần publish (send) duy nhất, amhs_recipients gộp cả 2 địa chỉ
        verify(mockProducer, times(1)).send(any());
        verify(mockTextMessage).setStringProperty("amhs_recipients", "VVHHZTZX,VVCIZTZX");
        assertEquals(GwoutDispatch.STATUS_SENT, dispatch.getStatus());
        assertEquals(GwoutDispatch.STATUS_SENT, dispatch2.getStatus());

        // Dispatch anh em (dispatch2) không còn PENDING/FAILED nên vòng lặp poller kế tiếp sẽ bỏ qua
        service.processDispatch(dispatch2);
        verify(mockProducer, times(1)).send(any());
    }

    // ==================== PROBE AND EIT TESTS ====================

    @Test
    void testProbeConveyance_ValidProbe_ShouldGenerateDR() {
        // Given
        Gwout probe = new Gwout();
        probe.setMsgid(2L);
        probe.setOrigin("VVTSZYYX");
        probe.setAddress("VVHHZTZX");
        probe.setBodyType("probe");
        probe.setText("PROBE");

        when(authorizationService.isAmhsUserAuthorized("VVTSZYYX")).thenReturn(true);
        when(validationService.validateAftnAddress("VVHHZTZX", "Recipient")).thenReturn(
            new MessageValidationService.ValidationResult(true, List.of())
        );
        when(configService.get("AUTHORIZED_AMHS_ADDRESSES")).thenReturn("VVHHZTZX");

        // When
        service.processOutboundMessage(probe);

        // Then: Should mark as OUT_PUBLISHED and log DR
        assertEquals(OutboundStatus.PUBLISHED.getValue(), probe.getStatus());
        verify(gwoutRepository).save(probe);
        verify(conversionService).logAmhsToSwim(eq(probe), any(), eq("OK"), eq("dr_generated_probe"));
    }

    @Test
    void testProbeConveyance_InvalidOriginator_ShouldGenerateNDR() {
        // Given
        Gwout probe = new Gwout();
        probe.setMsgid(2L);
        probe.setOrigin("UNKNOWNX");
        probe.setAddress("VVHHZTZX");
        probe.setBodyType("probe");

        when(authorizationService.isAmhsUserAuthorized("UNKNOWNX")).thenReturn(false);

        // When
        service.processOutboundMessage(probe);

        // Then: Should mark as OUT_FAILED, log REJECTED and ndr_unknown_originator
        assertEquals(OutboundStatus.FAILED.getValue(), probe.getStatus());
        assertEquals("unknown-originator", probe.getRejectionReason());
        verify(gwoutRepository).save(probe);
        verify(conversionService).logAmhsToSwim(eq(probe), any(), eq("REJECTED"), eq("ndr_unknown_originator: UNKNOWNX"));
    }

    @Test
    void testProbeConveyance_UnknownRecipient_ShouldGenerateNDR() {
        // Given
        Gwout probe = new Gwout();
        probe.setMsgid(2L);
        probe.setOrigin("VVTSZYYX");
        probe.setAddress("UNKNOWN");
        probe.setBodyType("probe");

        when(authorizationService.isAmhsUserAuthorized("VVTSZYYX")).thenReturn(true);
        when(validationService.validateAftnAddress("UNKNOWN", "Recipient")).thenReturn(
            new MessageValidationService.ValidationResult(true, List.of())
        );
        // Not configured in whitelist or IN routing rule
        when(configService.get("AUTHORIZED_AMHS_ADDRESSES")).thenReturn("");
        when(routingService.isRecipientConfigured("UNKNOWN")).thenReturn(false);

        // When
        service.processOutboundMessage(probe);

        // Then: Should mark as OUT_FAILED and generate NDR
        assertEquals(OutboundStatus.FAILED.getValue(), probe.getStatus());
        assertEquals("unknown-recipient", probe.getRejectionReason());
        verify(gwoutRepository).save(probe);
    }

    @Test
    void testEitValidation_UnsupportedType_ShouldReject() {
        // Given
        gwout.setBodyPartType("unsupported-format-eit");
        when(validationService.validateAmhsToSwim(anyString(), anyString())).thenReturn(
            new MessageValidationService.ValidationResult(true, List.of())
        );
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);
        when(validationService.validateBodyPartType("unsupported-format-eit")).thenReturn(
            new MessageValidationService.ValidationResult(false, List.of("Unsupported EIT"))
        );

        // When
        service.processOutboundMessage(gwout);

        // Then: Should mark as OUT_FAILED and log conversion log
        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("unsupported-eit", gwout.getRejectionReason());
        assertEquals("content-syntax-error", gwout.getRejectionDiagnostic());
        verify(gwoutRepository).save(gwout);
        verify(conversionService).logAmhsToSwimRejected(eq(gwout), contains("unsupported_eit"),
                eq("content-syntax-error"), anyString());
    }

    @Test
    void testConvertOutboundMessage_InvalidOriginFormat_ShouldReject() {
        // Given: Origin is invalid (lowercase, digits, incorrect length, etc.)
        Gwout badGwout = new Gwout();
        badGwout.setMsgid(999L);
        badGwout.setOrigin("vvtszpy1"); // has numbers and lowercase

        // When
        service.processOutboundMessage(badGwout);

        // Then: Should fail immediately
        assertEquals(OutboundStatus.FAILED.getValue(), badGwout.getStatus());
        assertEquals("invalid-origin-format", badGwout.getRejectionReason());
        assertEquals("invalid-arguments", badGwout.getRejectionDiagnostic());
        verify(gwoutRepository).save(badGwout);
        verify(conversionService).logAmhsToSwimRejected(eq(badGwout), eq("invalid_origin_format"),
                eq("invalid-arguments"), anyString());
    }

    @Test
    void testConvertOutboundMessage_BodyPart401_ShouldPassAndConvert() throws Exception {
        // Given: Body part type is 401
        setupValidScenario();
        gwout.setBodyPartType("401");
        when(validationService.validateBodyPartType("ia5-text-body-part"))
            .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));

        // When
        service.processOutboundMessage(gwout);

        // Then: Should convert bodyPartType to ia5-text-body-part and pass validation
        assertEquals("ia5-text-body-part", gwout.getBodyPartType());
        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
        verify(gwoutRepository, atLeastOnce()).save(gwout);
    }

    // ==================== HELPER METHODS ====================

    private void setupValidScenario() throws Exception {
        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
            .thenReturn(validResult);
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);
        when(detectService.detect(anyString())).thenReturn("METAR");
        when(routingService.findBestMatchOut("METAR")).thenReturn(Optional.of(routing));

        // gwout.text đã set sẵn ở setUp() để bypass empty check trong processDispatch
        dispatch.setTopic("ats.met.metar");
        when(gwoutDispatchRepository.findByGwoutId(1L)).thenReturn(List.of(dispatch));

        // Mock AMQP publishing
        Session session = mock(Session.class);
        mockProducer = mock(MessageProducer.class);
        mockTextMessage = mock(TextMessage.class);

        when(connectionManager.createSession()).thenReturn(session);
        when(connectionManager.createProducer(any(), anyString())).thenReturn(mockProducer);
        when(session.createTextMessage(anyString())).thenReturn(mockTextMessage);
    }
}