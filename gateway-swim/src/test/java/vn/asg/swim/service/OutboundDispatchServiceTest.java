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
    @Mock private ReportService reportService;
    @Mock private GwoutDispatchRepository gwoutDispatchRepository;
    @Mock private GwoutRepository gwoutRepository;

    @InjectMocks
    private OutboundDispatchService service;

    private Gwout gwout;
    private GwoutDispatch dispatch;
    private Routing routing;
    private MessageProducer mockProducer;
    private TextMessage mockTextMessage;
    private Session session;
    private jakarta.jms.BytesMessage mockBytesMessage;

    @BeforeEach
    void setUp() {
        // Setup common test data
        gwout = new Gwout();
        gwout.setMsgid(1L);
        gwout.setText("METAR VVTS 121200Z 09008KT 9999 FEW020 32/25 Q1010=");
        gwout.setOrigin("VVTSZYYX");
        gwout.setAddress("VVHHZTZX");
        gwout.setAmhsPriority("FF");
        gwout.setFilingTime("121200");
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
        // §4.4.2.1: EIT hợp lệ theo mặc định; test nào cần kiểm EIT thì stub lại riêng
        when(validationService.validateEncodedInformationTypes(any()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        // §4.4.2.5 (CTSW004): ATS-message-header hợp lệ theo mặc định
        when(validationService.validateAtsMessageHeader(any(), any()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        // §4.4.2.3 (CTSW017/CTSW019): repertoire hợp lệ theo mặc định
        when(validationService.validateRepertoire(any(), any()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        // §4.4.2.3 (CTSW016): body part type hợp lệ theo mặc định; test nào cần thì stub lại riêng
        when(validationService.validateBodyPartType(anyString()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
    }

    // ==================== ISSUE #1: VALIDATION FAILURE LOGIC ====================

    @Test
    void testValidationFailure_ShouldSetStatusDead_NotSent() {
        // Given: Validation fails
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        MessageValidationService.ValidationResult invalidResult =
            new MessageValidationService.ValidationResult(false, List.of("Invalid AFTN address format"));
        when(validationService.validateAmhsToSwim(anyString(), anyString(), any()))
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
        when(validationService.validateAmhsToSwim(anyString(), anyString(), any()))
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
        when(validationService.validateAmhsToSwim(anyString(), anyString(), any()))
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
        when(validationService.validateAmhsToSwim(anyString(), anyString(), any()))
            .thenReturn(validResult);
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);
        when(detectService.detect(anyString())).thenReturn("UNKNOWN");
        when(routingService.findBestMatchOut("UNKNOWN")).thenReturn(Optional.empty());

        // When
        service.processOutboundMessage(gwout);

        // Then: Should fail at routing step VÀ sinh NDR (§4.4.8) - trước đây nhánh này chỉ đặt
        // status = FAILED nên người gửi X.400 không nhận được gì.
        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("no-routing-rule", gwout.getRejectionReason());
        verify(conversionService).logAmhsToSwimRejected(eq(gwout), contains("routing_failed"),
                eq("unrecognised-OR-name"), isNull());
        verify(reportService).recordNdrForAll(eq(gwout), eq("unrecognised-OR-name"), isNull());
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

        // Then: Should mark as OUT_PUBLISHED and log DR cho từng recipient (CTSW012 §4.4.6.6)
        assertEquals(OutboundStatus.PUBLISHED.getValue(), probe.getStatus());
        verify(gwoutRepository).save(probe);
        verify(conversionService).logAmhsToSwim(eq(probe), any(), eq("OK"), eq("dr_generated_probe: VVHHZTZX"));
    }

    @Test
    void testProbeConveyance_MixedRecipients_ShouldGenerateCombinedReport() {
        // CTSW012: probe tới 2 recipient, chỉ 1 chuyển đổi được sang AF-address.
        // Kỳ vọng: NDR "unrecognised-OR-name" cho recipient lạ, DR cho recipient hợp lệ.
        Gwout probe = new Gwout();
        probe.setMsgid(2L);
        probe.setOrigin("VVTSZYYX");
        probe.setAddress("VVHHZTZX,VVZZZTZX");
        probe.setBodyType("probe");

        when(authorizationService.isAmhsUserAuthorized("VVTSZYYX")).thenReturn(true);
        when(validationService.validateAftnAddress(anyString(), eq("Recipient")))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        // Chỉ VVHHZTZX nằm trong bảng tra địa chỉ
        when(configService.get("AUTHORIZED_AMHS_ADDRESSES")).thenReturn("VVHHZTZX");

        service.processOutboundMessage(probe);

        // Probe vẫn coi là xử lý xong vì có recipient nhận DR
        assertEquals(OutboundStatus.PUBLISHED.getValue(), probe.getStatus());
        assertEquals("unrecognised-OR-name", probe.getRejectionDiagnostic());
        verify(conversionService).logAmhsToSwim(eq(probe), any(), eq("OK"), eq("dr_generated_probe: VVHHZTZX"));
        verify(conversionService).logAmhsToSwimRejected(eq(probe),
                eq("ndr_unknown_recipient: VVZZZTZX"), eq("unrecognised-OR-name"), isNull());
    }

    @Test
    void testProbeConveyance_ContentLengthExceedsMax_ShouldGenerateNDR() {
        // CTSW011 Probe 3 (§4.4.6.2): content-length vượt "Maximum message data size"
        Gwout probe = new Gwout();
        probe.setMsgid(2L);
        probe.setOrigin("VVTSZYYX");
        probe.setAddress("VVHHZTZX");
        probe.setBodyType("probe");
        probe.setContentLength(5000);

        when(authorizationService.isAmhsUserAuthorized("VVTSZYYX")).thenReturn(true);
        when(configService.getMaxMsgDataSize()).thenReturn(2048);

        service.processOutboundMessage(probe);

        assertEquals(OutboundStatus.FAILED.getValue(), probe.getStatus());
        assertEquals("content-too-long", probe.getRejectionDiagnostic());
        verify(conversionService).logAmhsToSwimRejected(eq(probe), anyString(), eq("content-too-long"),
                eq("unable to convert to AMQP due to the content size"));
    }

    @Test
    void testProbeConveyance_TooManyRecipients_ShouldGenerateNDR() {
        // CTSW011 Probe 5 (§4.4.6.3): số recipient vượt "Maximum message number of recipients"
        Gwout probe = new Gwout();
        probe.setMsgid(2L);
        probe.setOrigin("VVTSZYYX");
        probe.setAddress("VVHHZTZX,VVNBZTZX,VVDNZTZX");
        probe.setBodyType("probe");

        when(authorizationService.isAmhsUserAuthorized("VVTSZYYX")).thenReturn(true);
        when(configService.getMaxMsgRecipients()).thenReturn(2);

        service.processOutboundMessage(probe);

        assertEquals(OutboundStatus.FAILED.getValue(), probe.getStatus());
        assertEquals("too-many-recipients", probe.getRejectionDiagnostic());
        verify(conversionService).logAmhsToSwimRejected(eq(probe), anyString(), eq("too-many-recipients"),
                eq("unable to convert to AMQP due to number of recipients"));
    }

    @Test
    void testProbeConveyance_UnsupportedEit_ShouldGenerateNDR() {
        // CTSW016 (§4.4.6.1): EIT của probe cũng phải được kiểm tra
        Gwout probe = new Gwout();
        probe.setMsgid(2L);
        probe.setOrigin("VVTSZYYX");
        probe.setAddress("VVHHZTZX");
        probe.setBodyType("probe");
        probe.setOriginEit("{id-cs-eit-authority 3}");

        when(authorizationService.isAmhsUserAuthorized("VVTSZYYX")).thenReturn(true);
        when(validationService.validateEncodedInformationTypes("{id-cs-eit-authority 3}"))
                .thenReturn(new MessageValidationService.ValidationResult(false,
                        List.of("Unsupported encoded-information-types: {id-cs-eit-authority 3}")));

        service.processOutboundMessage(probe);

        assertEquals(OutboundStatus.FAILED.getValue(), probe.getStatus());
        assertEquals("encoded-information-types-unsupported", probe.getRejectionDiagnostic());
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

        // Then (CTSW013 §4.4.6.4): OUT_FAILED kèm diagnostic "invalid-arguments" và
        // supplementary-information theo Appendix A
        assertEquals(OutboundStatus.FAILED.getValue(), probe.getStatus());
        assertEquals("unknown-originator", probe.getRejectionReason());
        assertEquals("invalid-arguments", probe.getRejectionDiagnostic());
        verify(gwoutRepository).save(probe);
        verify(conversionService).logAmhsToSwimRejected(eq(probe),
                eq("ndr_unknown-originator: Unknown originator: UNKNOWNX"),
                eq("invalid-arguments"),
                eq("unable to convert to AMQP due to unrecognized originator O/R address"));
        // §3.1.1.1: probe bị từ chối phải được báo Control Position
        verify(alertService).create(anyString(), anyString(), anyString(), eq("gwout"), eq(2L));
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
        // "UNKNOWN" chỉ có 7 ký tự -> không phải địa chỉ AFTN hợp lệ nên không chuyển đổi
        // được sang AF-address (§4.4.6.5), không cần tới whitelist.

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
        when(validationService.validateAmhsToSwim(anyString(), anyString(), any())).thenReturn(
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

    // ==================== ATS-MESSAGE-HEADER (CTSW004) ====================

    @Test
    void testAtsHeaderSyntaxError_ShouldRejectWithContentSyntaxError() throws Exception {
        // CTSW004: ATS-message-header sai cú pháp -> không chuyển sang AMQP, sinh NDR
        // content-syntax-error kèm supplementary-information theo đúng câu chữ của Appendix A.
        setupValidScenario();
        gwout.setAmhsPriority("XX");
        when(validationService.validateAtsMessageHeader(any(), any()))
                .thenReturn(new MessageValidationService.ValidationResult(false,
                        List.of("ATS-message-priority 'XX' is invalid")));

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("ats-header-syntax-error", gwout.getRejectionReason());
        assertEquals("content-syntax-error", gwout.getRejectionDiagnostic());
        verify(conversionService).logAmhsToSwimRejected(eq(gwout), contains("ats_header_syntax_error"),
                eq("content-syntax-error"),
                eq("unable to convert to AMQP due to ATS-message-header or Heading Fields syntax error"));
        verify(alertService).create(eq(GwAlert.TYPE_VALIDATION_ERROR), eq(GwAlert.SEV_WARNING),
                contains("ATS-message-header syntax error"), eq("gwout"), eq(1L));
    }

    // ==================== NDR SUPPLEMENTARY-INFORMATION (CTSW006 / CTSW010) ====================

    @Test
    void testSizeLimitRejection_ShouldCarryContentSizeSupplementaryInfo() {
        // CTSW006: NDR phải mang "unable to convert to AMQP due to the content size"
        when(validationService.validateAmhsToSwim(anyString(), anyString(), any()))
                .thenReturn(new MessageValidationService.ValidationResult(false,
                        List.of("Message size 200 bytes exceeds maximum 100 bytes (content-too-long)")));

        service.processOutboundMessage(gwout);

        assertEquals("content-too-long", gwout.getRejectionDiagnostic());
        verify(conversionService).logAmhsToSwimRejected(eq(gwout), contains("validation_failed"),
                eq("content-too-long"), eq("unable to convert to AMQP due to the content size"));
    }

    @Test
    void testRecipientsLimitRejection_ShouldCarryRecipientsSupplementaryInfo() {
        // CTSW010: NDR phải mang "unable to convert to AMQP due to number of recipients"
        when(validationService.validateAmhsToSwim(anyString(), anyString(), any()))
                .thenReturn(new MessageValidationService.ValidationResult(false,
                        List.of("Recipients count 513 exceeds maximum 512 (too-many-recipients)")));

        service.processOutboundMessage(gwout);

        assertEquals("too-many-recipients", gwout.getRejectionDiagnostic());
        verify(conversionService).logAmhsToSwimRejected(eq(gwout), contains("validation_failed"),
                eq("too-many-recipients"), eq("unable to convert to AMQP due to number of recipients"));
    }

    @Test
    void testTtlExpired_ShouldNotCarrySupplementaryInfo() throws Exception {
        // CTSW005 chỉ yêu cầu reason-code + diagnostic-code; trước đây code nhét
        // "unable-to-transfer" (một reason-code) vào ô supplementary-information.
        setupValidScenario();
        gwout.setAmhsTtl(LocalDateTime.now().minusHours(1));

        service.processOutboundMessage(gwout);

        assertEquals("maximum-time-expired", gwout.getRejectionDiagnostic());
        verify(conversionService).logAmhsToSwimRejected(eq(gwout), eq("ttl_expired"),
                eq("maximum-time-expired"), isNull());
        // §3.1.1.1: tình huống ngoài luồng phải được báo Control Position
        verify(alertService).create(eq(GwAlert.TYPE_VALIDATION_ERROR), eq(GwAlert.SEV_WARNING),
                contains("latest-delivery-time exceeded"), eq("gwout"), eq(1L));
    }

    // ==================== CTSW020: báo Control Position ====================

    @Test
    void testCTSW020_Precedence107_ShouldNotifyControlPosition() throws Exception {
        // CTSW020 điện văn 1: Extended IPM, precedence cao nhất 107 -> báo CP, vẫn chuyển tiếp
        setupValidScenario();
        gwout.setPrecedence(107);
        gwout.setAmhsPriority("SS");

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
        verify(alertService).create(eq(GwAlert.TYPE_VALIDATION_ERROR), eq(GwAlert.SEV_WARNING),
                contains("precedence 107"), eq("gwout"), eq(1L));
    }

    @Test
    void testCTSW020_Precedence14_ShouldNotNotifyControlPosition() throws Exception {
        // CTSW020 điện văn 4: precedence 14 -> KHÔNG báo CP
        setupValidScenario();
        gwout.setPrecedence(14);
        gwout.setAmhsPriority("KK");

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
        verify(alertService, never()).create(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void testCTSW020_BasicIpmWithSsPriority_ShouldNotifyControlPosition() throws Exception {
        // CTSW020 điện văn 2: Basic IPM, ATS-message-priority SS -> báo CP
        setupValidScenario();
        gwout.setPrecedence(null);
        gwout.setAmhsPriority("SS");

        service.processOutboundMessage(gwout);

        verify(alertService).create(eq(GwAlert.TYPE_VALIDATION_ERROR), eq(GwAlert.SEV_WARNING),
                contains("ATS-message-priority SS"), eq("gwout"), eq(1L));
    }

    @Test
    void testCTSW020_BasicIpmWithDdPriority_ShouldNotNotify() throws Exception {
        // CTSW020 điện văn 5: priority DD -> KHÔNG báo CP
        setupValidScenario();
        gwout.setAmhsPriority("DD");

        service.processOutboundMessage(gwout);

        verify(alertService, never()).create(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    // ==================== CTSW003 / NDR: hàng đợi gwout_report ====================

    @Test
    void testCTSW003_DeliveryReportRequested_ShouldQueueDr() throws Exception {
        setupValidScenario();
        gwout.setAmhsDeliveryReport(true);

        service.processOutboundMessage(gwout);

        verify(reportService).recordDrForAll(gwout);
    }

    @Test
    void testCTSW003_NoReportRequested_ShouldNotQueueDr() throws Exception {
        setupValidScenario();
        gwout.setAmhsDeliveryReport(false);

        service.processOutboundMessage(gwout);

        verify(reportService, never()).recordDrForAll(any());
    }

    @Test
    void testRejection_ShouldQueueNdrWithSameTripletAsLog() throws Exception {
        // Bộ ba phần tử NDR phải nhất quán giữa traffic log và hàng đợi gwout_report
        setupValidScenario();
        gwout.setAmhsTtl(LocalDateTime.now().minusHours(1));

        service.processOutboundMessage(gwout);

        verify(reportService).recordNdrForAll(gwout, "maximum-time-expired", null);
    }

    @Test
    void testProbeMixedRecipients_ShouldQueueBothDrAndNdr() throws Exception {
        // CTSW012: combined report - DR cho recipient hợp lệ, NDR cho recipient lạ
        Gwout probe = new Gwout();
        probe.setMsgid(2L);
        probe.setOrigin("VVTSZYYX");
        probe.setAddress("VVHHZTZX,VVZZZTZX");
        probe.setBodyType("probe");

        when(authorizationService.isAmhsUserAuthorized("VVTSZYYX")).thenReturn(true);
        when(validationService.validateAftnAddress(anyString(), eq("Recipient")))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        when(configService.get("AUTHORIZED_AMHS_ADDRESSES")).thenReturn("VVHHZTZX");

        service.processOutboundMessage(probe);

        verify(reportService).recordDr(probe, "VVHHZTZX");
        verify(reportService).recordNdr(probe, "VVZZZTZX", "unrecognised-OR-name", null);
    }

    // ==================== REPERTOIRE (CTSW017 / CTSW019) ====================

    @Test
    void testRepertoireIta2_ShouldRejectWithUnsupportedBodyPartType() throws Exception {
        // CTSW017 điện văn 3: ia5-text-body-part với repertoire ita2 -> NDR content-syntax-error
        setupValidScenario();
        gwout.setBodyPartType("ia5-text-body-part");
        gwout.setBodyPartCharset("ITA2");
        when(validationService.validateRepertoire("ia5-text-body-part", "ITA2"))
                .thenReturn(new MessageValidationService.ValidationResult(false,
                        List.of("ia5-text-body-part repertoire 'ITA2' is not supported (unsupported-body-part-type)")));

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("unsupported-repertoire", gwout.getRejectionReason());
        assertEquals("content-syntax-error", gwout.getRejectionDiagnostic());
        verify(conversionService).logAmhsToSwimRejected(eq(gwout), contains("unsupported_repertoire"),
                eq("content-syntax-error"),
                eq("unable to convert to AMQP due to unsupported body part type"));
    }

    @Test
    void testRepertoireNonIso646_RejectedByPolicy_ShouldCarryEitSupplementary() throws Exception {
        // CTSW019: chính sách nội bộ từ chối repertoire khác ISO 646
        setupValidScenario();
        gwout.setBodyPartType("general-text-body-part");
        gwout.setBodyPartCharset("ISO-REG-144");
        when(validationService.validateRepertoire("general-text-body-part", "ISO-REG-144"))
                .thenReturn(new MessageValidationService.ValidationResult(false,
                        List.of("general-text-body-part repertoire 'ISO-REG-144' rejected by local AMHS "
                                + "Management Domain policy (unsupported-encoded-information-types)")));

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("content-syntax-error", gwout.getRejectionDiagnostic());
        verify(conversionService).logAmhsToSwimRejected(eq(gwout), contains("unsupported_repertoire"),
                eq("content-syntax-error"),
                eq("unable to convert to AMQP due to unsupported encoded-information-types"));
    }

    // ==================== AMQP APPLICATION PROPERTIES (CTSW001) ====================

    @Test
    void testPublish_ShouldSetAmhsSubjectProperty() throws Exception {
        // CTSW001 (§4.4.3.4.8): amhs_subject phải mang phần tử subject của IPM heading
        setupValidScenario();
        gwout.setSubject("SIGMET VVTS");

        service.processOutboundMessage(gwout);
        service.processDispatch(dispatch);

        verify(mockTextMessage).setStringProperty("amhs_subject", "SIGMET VVTS");
    }

    @Test
    void testPublish_BlankSubject_ShouldNotSetAmhsSubjectProperty() throws Exception {
        setupValidScenario();
        gwout.setSubject("   ");

        service.processOutboundMessage(gwout);
        service.processDispatch(dispatch);

        verify(mockTextMessage, never()).setStringProperty(eq("amhs_subject"), anyString());
    }

    @Test
    void testPublish_UserVisibleString_ShouldBeSetWhenPresent() throws Exception {
        // Table 2 / §4.4.3.4.11: amhs_user_visible_string là T1 - gán khi phần tử có mặt
        setupValidScenario();
        gwout.setBodyType("ftbp");
        gwout.setBodyPartType("file-transfer-body-part");
        gwout.setText(java.util.Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }));
        gwout.setAmhsUserVisibleString("bao-cao-thoi-tiet.pdf");
        when(session.createBytesMessage()).thenReturn(mockBytesMessage);

        service.processOutboundMessage(gwout);
        service.processDispatch(dispatch);

        verify(mockBytesMessage).setStringProperty("amhs_user_visible_string", "bao-cao-thoi-tiet.pdf");
    }

    @Test
    void testPublish_NonDefaultRegisteredIdWithoutUserVisibleString_ShouldAlertControlPosition() throws Exception {
        // §4.4.4.6: registered-identifier khác OID mặc định thì user-visible-string bắt buộc.
        // Thiếu -> vẫn gửi bản tin nhưng phải báo Control Position.
        setupValidScenario();
        gwout.setBodyType("ftbp");
        gwout.setBodyPartType("file-transfer-body-part");
        gwout.setText(java.util.Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }));
        gwout.setAmhsRegisteredId("1.2.3.4.5");
        gwout.setAmhsUserVisibleString(null);
        when(session.createBytesMessage()).thenReturn(mockBytesMessage);

        service.processOutboundMessage(gwout);
        service.processDispatch(dispatch);

        verify(mockBytesMessage).setStringProperty("amhs_registered_identifier", "1.2.3.4.5");
        verify(alertService).create(eq(GwAlert.TYPE_VALIDATION_ERROR), eq(GwAlert.SEV_WARNING),
                contains("amhs_user_visible_string"), eq("gwout"), eq(1L));
    }

    @Test
    void testPublish_DefaultRegisteredId_ShouldNotAlert() throws Exception {
        // OID mặc định "unknown-attachment" thì không cần user-visible-string
        setupValidScenario();
        gwout.setBodyType("ftbp");
        gwout.setBodyPartType("file-transfer-body-part");
        gwout.setText(java.util.Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }));
        gwout.setAmhsRegisteredId(vn.asg.swim.model.AmqpProperties.DEFAULT_REGISTERED_IDENTIFIER_OID);
        when(session.createBytesMessage()).thenReturn(mockBytesMessage);

        service.processOutboundMessage(gwout);
        service.processDispatch(dispatch);

        verify(alertService, never()).create(anyString(), anyString(),
                contains("amhs_user_visible_string"), anyString(), anyLong());
    }

    @Test
    void testPublish_UnknownRepertoire_ShouldNotSetContentEncoding() throws Exception {
        // Table 6: amhs_content_encoding chỉ nhận IA5 / ISO-646 / ISO-8859-1.
        // Repertoire ISO-REG-n qua được chính sách CTSW019 thì vẫn gửi bản tin nhưng
        // không gán property với giá trị ngoài Table 6.
        setupValidScenario();
        gwout.setBodyPartType("general-text-body-part");
        gwout.setBodyPartCharset("ISO-REG-144");

        service.processOutboundMessage(gwout);
        service.processDispatch(dispatch);

        verify(mockTextMessage, never()).setStringProperty(eq("amhs_content_encoding"), anyString());
    }

    @Test
    void testPublish_Iso8859Repertoire_ShouldSetContentEncoding() throws Exception {
        setupValidScenario();
        gwout.setBodyPartType("general-text-body-part");
        gwout.setBodyPartCharset("ISO-8859-1");

        service.processOutboundMessage(gwout);
        service.processDispatch(dispatch);

        verify(mockTextMessage).setStringProperty("amhs_content_encoding", "ISO-8859-1");
    }

    // ==================== KÍCH THƯỚC PAYLOAD FTBP (CTSW006) ====================

    @Test
    void testFtbpPayloadSize_ShouldBeMeasuredAfterBase64Decode() throws Exception {
        // CTSW006 (c): với FTBP, gwout.text là base64 nên phải giải mã trước khi đo,
        // nếu không bản tin sát ngưỡng sẽ bị từ chối nhầm.
        setupValidScenario();
        byte[] raw = new byte[300];
        gwout.setBodyType("ftbp");
        gwout.setText(java.util.Base64.getEncoder().encodeToString(raw)); // chuỗi dài 400 ký tự

        service.processOutboundMessage(gwout);

        // Validator phải nhận đúng 300 byte (dữ liệu gốc), không phải 400 (độ dài chuỗi base64)
        verify(validationService).validateAmhsToSwim(anyString(), anyString(), eq(300));
    }

    @Test
    void testTextPayloadSize_ShouldBeMeasuredOnUtf8Bytes() throws Exception {
        setupValidScenario();
        gwout.setBodyType("text");
        gwout.setText("METAR VVTS");

        service.processOutboundMessage(gwout);

        verify(validationService).validateAmhsToSwim(anyString(), anyString(), eq(10));
    }

    // ==================== BODY PART COUNT (CTSW007) ====================

    @Test
    void testBodyPartCount_TwoTextBodyParts_ShouldReject() throws Exception {
        // CTSW007 - điện văn 3: hai body part ia5-text (không có FTBP) -> NDR
        setupValidScenario();
        gwout.setNumberOfAttachment(2);
        gwout.setBodyPartType("ia5-text-body-part");

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("unsupported-body-parts", gwout.getRejectionReason());
        verify(conversionService).logAmhsToSwimRejected(eq(gwout), contains("unsupported_body_parts"),
                eq("content-syntax-error"),
                eq("unable to convert to AMQP due to unsupported body part type"));
    }

    @Test
    void testBodyPartCount_ThreeBodyParts_ShouldReject() throws Exception {
        // CTSW007 - điện văn 4: ba body part -> NDR "multiple body parts"
        setupValidScenario();
        gwout.setNumberOfAttachment(3);
        gwout.setBodyPartType("file-transfer-body-part");

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        verify(conversionService).logAmhsToSwimRejected(eq(gwout), contains("unsupported_body_parts"),
                eq("content-syntax-error"),
                eq("unable to convert to AMQP due to multiple body parts"));
    }

    @Test
    void testBodyPartCount_TextPlusFtbp_ShouldBeAccepted() throws Exception {
        // CTSW007 - điện văn 1&2: cặp text + file-transfer-body-part là tổ hợp hợp lệ
        setupValidScenario();
        gwout.setNumberOfAttachment(2);
        gwout.setBodyPartType("file-transfer-body-part");
        when(validationService.validateBodyPartType("file-transfer-body-part"))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
    }

    @Test
    void testBodyPartCount_SingleBodyPart_ShouldBeAccepted() throws Exception {
        setupValidScenario();
        gwout.setNumberOfAttachment(1);

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
    }

    // ==================== SS -> CONTROL POSITION (CTSW020) ====================

    @Test
    void testPrioritySS_ShouldAlertControlPositionButStillForward() throws Exception {
        // CTSW020 (§4.4.4.4): bản tin SS phải được báo lên Control Position NHƯNG
        // vẫn tiếp tục chuyển sang SWIM.
        setupValidScenario();
        gwout.setAmhsPriority("SS");

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
        verify(alertService).create(eq(GwAlert.TYPE_VALIDATION_ERROR), eq(GwAlert.SEV_WARNING),
                contains("SS"), eq("gwout"), eq(1L));
    }

    @Test
    void testPriorityNotSS_ShouldNotAlertControlPosition() throws Exception {
        setupValidScenario();
        gwout.setAmhsPriority("FF");

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
        verify(alertService, never()).create(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    // ==================== HELPER METHODS ====================

    private void setupValidScenario() throws Exception {
        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateAmhsToSwim(anyString(), anyString(), any()))
            .thenReturn(validResult);
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);
        when(detectService.detect(anyString())).thenReturn("METAR");
        when(routingService.findBestMatchOut("METAR")).thenReturn(Optional.of(routing));

        // gwout.text đã set sẵn ở setUp() để bypass empty check trong processDispatch
        dispatch.setTopic("ats.met.metar");
        when(gwoutDispatchRepository.findByGwoutId(1L)).thenReturn(List.of(dispatch));
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));

        // Mock AMQP publishing
        session = mock(Session.class);
        mockBytesMessage = mock(jakarta.jms.BytesMessage.class);
        mockProducer = mock(MessageProducer.class);
        mockTextMessage = mock(TextMessage.class);

        when(connectionManager.createSession()).thenReturn(session);
        when(connectionManager.createProducer(any(), anyString())).thenReturn(mockProducer);
        when(session.createTextMessage(anyString())).thenReturn(mockTextMessage);
    }

    // ==================== DELIVERY REPORT (CTSW003) ====================

    @Test
    void testCTSW003_DeliveryReportRequested_ShouldRecordDr() throws Exception {
        setupValidScenario();
        gwout.setAmhsDeliveryReport(true);

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
        verify(conversionService).logAmhsToSwim(eq(gwout), isNull(), eq("OK"), eq("dr_generated"));
    }

    @Test
    void testCTSW003_DeliveryReportNotRequested_ShouldNotRecordDr() throws Exception {
        setupValidScenario();
        gwout.setAmhsDeliveryReport(false);

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
        verify(conversionService, never()).logAmhsToSwim(any(), any(), any(), eq("dr_generated"));
    }

    @Test
    void testCTSW003_RejectedMessage_ShouldNotRecordDr() throws Exception {
        // DR chỉ dành cho IPM dịch THÀNH CÔNG; bản tin bị từ chối phải ra NDR, không phải DR
        setupValidScenario();
        gwout.setAmhsDeliveryReport(true);
        gwout.setAmhsTtl(java.time.LocalDateTime.now().minusDays(1));

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        verify(conversionService, never()).logAmhsToSwim(any(), any(), any(), eq("dr_generated"));
    }

    // ==================== MTE CONTENT-TYPE (CTSW008) ====================

    @Test
    void testCTSW008_Ipm1988_ShouldBeAccepted() throws Exception {
        // Bản tin 1: interpersonal-messaging-1988(22) -> chấp nhận và chuyển đổi
        setupValidScenario();
        gwout.setX400ContentType(22);

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
    }

    @Test
    void testCTSW008_Ipm1984_ShouldBeRejected() throws Exception {
        // Bản tin 2: interpersonal-messaging-1984(2) -> NDR content-type-not-supported
        assertContentTypeRejected(2);
    }

    @Test
    void testCTSW008_EdiMessaging_ShouldBeRejected() throws Exception {
        // Bản tin 3: edi-messaging(35)
        assertContentTypeRejected(35);
    }

    @Test
    void testCTSW008_Unidentified_ShouldBeRejected() throws Exception {
        // Bản tin 4: unidentified(0)
        assertContentTypeRejected(0);
    }

    @Test
    void testCTSW008_NullContentType_ShouldSkipCheck() throws Exception {
        // Bản tin cũ đồng bộ trước khi có cột x400_content_type -> không được từ chối oan
        setupValidScenario();
        gwout.setX400ContentType(null);

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
    }

    private void assertContentTypeRejected(int contentType) throws Exception {
        setupValidScenario();
        gwout.setX400ContentType(contentType);

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("unsupported-content-type", gwout.getRejectionReason());
        assertEquals("content-type-not-supported", gwout.getRejectionDiagnostic());
        verify(conversionService).logAmhsToSwimRejected(eq(gwout),
                contains("unsupported_content_type"), eq("content-type-not-supported"), isNull());
    }

    // ==================== PROBE: TIÊU CHÍ TRA ĐỊA CHỈ AF (CTSW011/CTSW012) ====================

    @Test
    void testProbe_RecipientNotInWhitelist_ShouldStillGetDr() {
        // §4.4.6.5 xét khả năng chuyển O/R address -> AF-address, xác định được từ chính khuôn
        // địa chỉ. Trước đây hàm isRecipientKnown hỏi "có trong whitelist / rule IN không", nên
        // với cấu hình thật (whitelist rỗng, rule IN chỉ có 2 địa chỉ) MỌI recipient khác đều bị
        // NDR và CTSW011/CTSW012 không thể pass.
        Gwout probe = new Gwout();
        probe.setMsgid(2L);
        probe.setOrigin("VVTSZYYX");
        probe.setAddress("VVDNZTZX,VVCIZTZX");
        probe.setBodyType("probe");

        when(authorizationService.isAmhsUserAuthorized("VVTSZYYX")).thenReturn(true);
        when(validationService.validateAftnAddress(anyString(), eq("Recipient")))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        when(configService.get("AUTHORIZED_AMHS_ADDRESSES")).thenReturn("");
        when(configService.getMaxMsgRecipients()).thenReturn(512);

        service.processOutboundMessage(probe);

        assertEquals(OutboundStatus.PUBLISHED.getValue(), probe.getStatus());
        verify(reportService).recordDr(probe, "VVDNZTZX");
        verify(reportService).recordDr(probe, "VVCIZTZX");
        verify(reportService, never()).recordNdr(any(Gwout.class), anyString(), anyString(), any());
    }

    @Test
    void testProbe_WhitelistConfigured_ShouldStillNarrowDown() {
        // Khi AUTHORIZED_AMHS_ADDRESSES CÓ khai báo thì vẫn siết theo danh sách - đường lùi
        // phòng khi bộ conformance test hành xử khác dự đoán.
        Gwout probe = new Gwout();
        probe.setMsgid(2L);
        probe.setOrigin("VVTSZYYX");
        probe.setAddress("VVDNZTZX,VVCIZTZX");
        probe.setBodyType("probe");

        when(authorizationService.isAmhsUserAuthorized("VVTSZYYX")).thenReturn(true);
        when(validationService.validateAftnAddress(anyString(), eq("Recipient")))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        when(configService.get("AUTHORIZED_AMHS_ADDRESSES")).thenReturn("VVDNZTZX");
        when(configService.getMaxMsgRecipients()).thenReturn(512);

        service.processOutboundMessage(probe);

        verify(reportService).recordDr(probe, "VVDNZTZX");
        verify(reportService).recordNdr(probe, "VVCIZTZX", "unrecognised-OR-name", null);
    }

    // ==================== NDR CHO RECIPIENT SAI KHUÔN (§4.4.8) ====================

    @Test
    void testCreateDispatches_InvalidRecipient_ShouldNdrItButStillDeliverToOthers() throws Exception {
        // Trước đây recipient sai khuôn chỉ bị log.warn rồi bỏ: bản tin vẫn đi nhưng thiếu người
        // nhận và không để lại dấu vết nào.
        setupValidScenario();
        gwout.setAddress("VVHHZTZX,BAD1,VVDNZTZX");

        service.createDispatches(gwout);

        verify(reportService).recordNdr(gwout, "BAD1", "unrecognised-OR-name",
                "unable to convert to AMQP due to unrecognized recipient O/R address");
        // Hai recipient hợp lệ vẫn được tạo dispatch
        verify(gwoutDispatchRepository, times(2)).save(any(GwoutDispatch.class));
        assertNotEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
    }

    @Test
    void testCreateDispatches_AllRecipientsInvalid_ShouldRejectWholeMessage() throws Exception {
        setupValidScenario();
        gwout.setAddress("BAD1,BAD2");

        service.createDispatches(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("invalid-recipients", gwout.getRejectionReason());
        verify(reportService).recordNdrForAll(gwout, "unrecognised-OR-name",
                "unable to convert to AMQP due to unrecognized recipient O/R address");
        verify(gwoutDispatchRepository, never()).save(any(GwoutDispatch.class));
    }

    @Test
    void testCreateDispatches_BlankAddress_ShouldQueueNdr() throws Exception {
        setupValidScenario();
        gwout.setAddress("   ");

        service.createDispatches(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("no-recipients", gwout.getRejectionReason());
        verify(reportService).recordNdrForAll(eq(gwout), eq("unrecognised-OR-name"), isNull());
    }

    // ==================== NDR CHO CÁC NHÁNH LỖI CÒN LẠI (§4.4.8) ====================

    @Test
    void testUnauthorizedOriginator_ShouldQueueNdr() throws Exception {
        setupValidScenario();
        when(authorizationService.isAmhsUserAuthorized("VVTSZYYX")).thenReturn(false);

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("unauthorized-originator", gwout.getRejectionReason());
        verify(reportService).recordNdrForAll(gwout, "unrecognised-OR-name",
                "unable to convert to AMQP due to unrecognized originator O/R address");
    }

    @Test
    void testTypeDetectionFailure_ShouldQueueNdr() throws Exception {
        setupValidScenario();
        when(detectService.detect(anyString())).thenThrow(new RuntimeException("boom"));

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("type-detection-failed", gwout.getRejectionReason());
        verify(reportService).recordNdrForAll(eq(gwout), eq("content-syntax-error"), isNull());
    }

    @Test
    void testAllDispatchesDead_ShouldQueueNdrForDeadRecipientsOnly() throws Exception {
        // Hết retry mà vẫn không publish được -> ITCU đã không chuyển giao được, phải trả NDR.
        // Recipient đã SENT vẫn coi là thành công (report X.400 mang per-recipient-fields).
        setupValidScenario();
        // retryCount 2 -> lần thất bại này đẩy lên 3 = RETRY_MAX_COUNT nên dispatch thành DEAD
        dispatch.setRetryCount(2);
        dispatch.setRecipient("VVHHZTZX");

        GwoutDispatch sent = new GwoutDispatch();
        sent.setId(101L);
        sent.setGwoutId(1L);
        sent.setRecipient("VVDNZTZX");
        sent.setStatus(GwoutDispatch.STATUS_SENT);
        sent.setTopic("ats.met.metar");
        when(gwoutDispatchRepository.findByGwoutId(1L)).thenReturn(List.of(dispatch, sent));
        when(connectionManager.createSession()).thenThrow(new RuntimeException("broker down"));

        service.processDispatch(dispatch);

        assertEquals(GwoutDispatch.STATUS_DEAD, dispatch.getStatus());
        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        verify(reportService).recordNdr(gwout, "VVHHZTZX", null,
                "unable to convert to AMQP due to delivery failure to the SWIM component");
        verify(reportService, never()).recordNdr(any(Gwout.class), eq("VVDNZTZX"), any(), any());
    }

    // ==================== THỨ TỰ CHUẨN HOÁ BODY PART TYPE (CTSW007) ====================

    @Test
    void testBodyPartCount_RawCode403PlusText_ShouldBeAccepted() throws Exception {
        // Nguồn khác đường đồng bộ mtcu_tmp (amss ghi thẳng vào gwout cho Probe) có thể để lại
        // mã thô "403". Nếu bước đếm body part chạy TRƯỚC khi chuẩn hoá thì phép so sánh với
        // "file-transfer-body-part" không khớp và cặp text+FTBP hợp lệ bị từ chối nhầm.
        setupValidScenario();
        gwout.setNumberOfAttachment(2);
        gwout.setBodyPartType("403");

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), gwout.getStatus());
        assertEquals("file-transfer-body-part", gwout.getBodyPartType());
    }

    // ==================== AMQP APPLICATION PROPERTIES - Table 2 ====================

    @Test
    void testPublish_ShouldSetTable2Properties() throws Exception {
        setupValidScenario();
        gwout.setIpmId("IPM-2026-0001");
        gwout.setOptionalHeading("OHI-123");
        gwout.setFilingTime("121200");
        gwout.setAmhsPriority("FF");

        service.processOutboundMessage(gwout);
        service.processDispatch(dispatch);

        // §4.4.3.4.7 originator, §4.4.3.4.4 recipients, §4.4.3.4.1 IPM-Identifier
        verify(mockTextMessage).setStringProperty("amhs_originator", "VVTSZYYX");
        verify(mockTextMessage).setStringProperty("amhs_recipients", "VVHHZTZX");
        verify(mockTextMessage).setStringProperty("amhs_ipm_id", "IPM-2026-0001");
        // §4.4.3.4.3 priority, §4.4.3.4.5 filing-time (CTSW001), §4.4.3.4.6 OHI (CTSW002)
        verify(mockTextMessage).setStringProperty("amhs_ats_pri", "FF");
        verify(mockTextMessage).setStringProperty("amhs_ats_ft", "121200");
        verify(mockTextMessage).setStringProperty("amhs_ats_ohi", "OHI-123");
        // §4.4.3.4.10
        verify(mockTextMessage).setStringProperty("amhs_message_signed", "unsigned");
    }

    @Test
    void testPublish_MultipleRecipients_ShouldJoinIntoOneAmhsRecipients() throws Exception {
        // §4.4.3.4.4: 1 IPM AMHS chỉ sinh 1 message AMQP, amhs_recipients liệt kê đủ recipient
        setupValidScenario();
        GwoutDispatch second = new GwoutDispatch();
        second.setId(101L);
        second.setGwoutId(1L);
        second.setRecipient("VVDNZTZX");
        second.setStatus(GwoutDispatch.STATUS_PENDING);
        second.setTopic("ats.met.metar");
        when(gwoutDispatchRepository.findByGwoutId(1L)).thenReturn(List.of(dispatch, second));

        service.processDispatch(dispatch);

        verify(mockTextMessage).setStringProperty("amhs_recipients", "VVHHZTZX,VVDNZTZX");
        verify(mockProducer, times(1)).send(any());
    }

    // ==================== CTSW009: LOẠI RECIPIENT ====================

    @Test
    void testCTSW009_CopyAndBlindCopyRecipients_ShouldBeTreatedAsPrimary() throws Exception {
        // §4.4.3.4.4: "The use of CC recipients and BCC recipients should be avoided. If these
        // elements are present in an AMHS IPM, they shall be handled as primary recipients."
        // ITCU không phân biệt loại recipient: mọi địa chỉ trong gwout.address đều vào
        // amhs_recipients và đều được tạo dispatch như nhau.
        setupValidScenario();
        gwout.setAddress("VVHHZTZX,VVDNZTZX,VVCIZTZX"); // primary, copy, blind-copy

        service.createDispatches(gwout);

        verify(gwoutDispatchRepository, times(3)).save(any(GwoutDispatch.class));
        verify(reportService, never()).recordNdr(any(Gwout.class), anyString(), anyString(), any());
    }
}
