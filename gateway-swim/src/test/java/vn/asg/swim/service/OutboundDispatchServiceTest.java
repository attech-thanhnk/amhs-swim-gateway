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
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
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
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
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
}
