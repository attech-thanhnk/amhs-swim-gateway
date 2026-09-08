package vn.asg.swim.service;

import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.GwoutDispatch;
import vn.asg.swim.entity.OutboundStatus;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.repository.GwoutDispatchRepository;
import vn.asg.swim.repository.GwoutRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm chứng đầu-cuối một số test case của Appendix A: chạy TRỌN pipeline chiều AMHS → SWIM
 * (transform → tạo dispatch → publish) thay vì từng bước rời rạc như các test khác.
 * <p>
 * Phạm vi hiện tại: CTSW001, CTSW002, CTSW004, CTSW005, CTSW006, CTSW008, CTSW016, CTSW018.
 * Đây KHÔNG phải bản audit đủ CTSW001–CTSW020 — các case còn lại được phủ ở
 * {@code OutboundDispatchServiceTest}, {@code AmhsToGwoutSyncSchedulerTest},
 * {@code AmhsFeedbackServiceTest} và {@code MessageValidationServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AllTestCasesAuditTest {

    @Mock private ConnectionManagerService connectionManager;
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

    @BeforeEach
    void setUp() {
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);
        when(validationService.validateAmhsToSwim(anyString(), anyString(), any()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        when(validationService.validateAftnAddress(anyString(), anyString()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        when(validationService.validateEncodedInformationTypes(any()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        when(validationService.validateBodyPartType(anyString()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        when(validationService.validateAtsMessageHeader(any(), any()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        when(validationService.validateRepertoire(any(), any()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));

        Routing metarRule = new Routing();
        metarRule.setRecipients("*");
        metarRule.setSendTopic("ats/met/metar");
        when(routingService.findTopicForRecipient(anyString())).thenReturn(Optional.of(metarRule));

        Session session = mock(Session.class);
        MessageProducer producer = mock(MessageProducer.class);
        TextMessage textMessage = mock(TextMessage.class);
        try {
            when(connectionManager.createSession()).thenReturn(session);
            when(connectionManager.createProducer(any(), anyString())).thenReturn(producer);
            when(session.createTextMessage(anyString())).thenReturn(textMessage);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Runs the full outbound pipeline (transform -> create dispatches -> publish),
     * matching how GwoutPollerScheduler drives it in production across its 3 scheduled steps.
     */
    private void runFullOutboundPipeline(Gwout gwout) {
        when(gwoutRepository.findById(gwout.getMsgid())).thenReturn(Optional.of(gwout));

        service.processOutboundMessage(gwout);
        if (gwout.getStatus() == null || !gwout.getStatus().equals(OutboundStatus.TRANSFORMED.getValue())) {
            return; // transform step failed; nothing left to dispatch/publish
        }

        service.createDispatches(gwout);

        ArgumentCaptor<GwoutDispatch> dispatchCaptor = ArgumentCaptor.forClass(GwoutDispatch.class);
        verify(gwoutDispatchRepository, atLeastOnce()).save(dispatchCaptor.capture());
        // Lấy TOÀN BỘ dispatch đã tạo, không chỉ dòng cuối: chỉ trả về một dòng thì nhánh gộp
        // nhiều recipient thành một lần publish (§4.4.3.4.4) không bao giờ được đi qua.
        List<GwoutDispatch> dispatches = dispatchCaptor.getAllValues();
        when(gwoutDispatchRepository.findByGwoutId(gwout.getMsgid())).thenReturn(dispatches);

        service.processDispatch(dispatches.get(0));
    }

    @Test
    @DisplayName("CTSW001: Convert Incoming IPM with Filing Time")
    void testCTSW001() {
        Gwout gwout = createGwout("TC-CTSW001", "VVNBZTZX", "VVHHZTZX", "text/plain",
                "ZCZC ABC001\r\nFF VVHHZTZX\r\n070430 VVNBZTZX\r\nMETAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=");
        gwout.setFilingTime("070430");

        runFullOutboundPipeline(gwout);

        assertEquals(OutboundStatus.PUBLISHED.getValue(), gwout.getStatus());
        assertNotNull(gwout.getText());
    }

    @Test
    @DisplayName("CTSW002: Convert Incoming IPM with OHI")
    void testCTSW002() {
        Gwout gwout = createGwout("TC-CTSW002", "VVNBZTZX", "VVHHZTZX", "application/json",
                "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=");
        gwout.setOptionalHeading("OHI-TEST-DATA-123");

        runFullOutboundPipeline(gwout);

        assertEquals(OutboundStatus.PUBLISHED.getValue(), gwout.getStatus());
    }

    @Test
    @DisplayName("CTSW004: Generate NDR for Syntax Failure")
    void testCTSW004() {
        Gwout gwout = createGwout("TC-CTSW004", "VVTSZTZX", "VVHHZTZX", "application/json", "(FPL-INVALID-SYNTAX");
        when(validationService.validateAmhsToSwim(anyString(), anyString(), any()))
                .thenReturn(new MessageValidationService.ValidationResult(false, List.of("Syntax error in FPL")));

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("validation-failed", gwout.getRejectionReason());
    }

    @Test
    @DisplayName("CTSW005: Generate NDR for Expired TTL (latest-delivery-time)")
    void testCTSW005() {
        Gwout gwout = createGwout("TC-CTSW005", "VVNBZTZX", "VVHHZTZX", "text/plain", "METAR VVNB...");
        gwout.setAmhsTtl(java.time.LocalDateTime.now().minusDays(1));

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("ttl-expired", gwout.getRejectionReason());
        assertEquals("maximum-time-expired", gwout.getRejectionDiagnostic());
    }

    @Test
    @DisplayName("CTSW006: Reject IPM Exceeding Max Size")
    void testCTSW006() {
        Gwout gwout = createGwout("TC-CTSW006", "VVNBZTZX", "VVHHZTZX", "text/plain", "A".repeat(200));
        when(validationService.validateAmhsToSwim(anyString(), anyString(), any()))
                .thenReturn(new MessageValidationService.ValidationResult(false,
                        List.of("Message size 200 bytes exceeds maximum 100 bytes (content-too-long)")));

        service.processOutboundMessage(gwout);

        assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus());
        assertEquals("content-too-long", gwout.getRejectionDiagnostic());
    }

    @Test
    @DisplayName("CTSW008: content-type là abstract-value X.400 (gwout.x400_content_type), KHÔNG phải "
            + "chuỗi MIME — gwout.content_type không được dùng để lọc")
    void testCTSW008_MimeContentTypeIsNotAFilter() {
        // MIME content_type lạ không phải là căn cứ từ chối theo §4.4.1.1
        Gwout gwout = createGwout("TC-CTSW008", "VVNBZTZX", "VVHHZTZX", "application/unknown-mime-type", "METAR...");
        gwout.setX400ContentType(22); // interpersonal-messaging-1988

        runFullOutboundPipeline(gwout);

        assertEquals(OutboundStatus.PUBLISHED.getValue(), gwout.getStatus());
    }

    @Test
    @DisplayName("CTSW008: content-type khác interpersonal-messaging-1988(22) phải sinh NDR "
            + "content-type-not-supported")
    void testCTSW008_UnsupportedAbstractValueShouldBeRejected() {
        // Appendix A/CTSW008: điện văn 2 (1984), 3 (edi-messaging 35), 4 (unidentified 0) đều bị từ chối
        for (int abstractValue : new int[] { 2, 35, 0 }) {
            Gwout gwout = createGwout("TC-CTSW008-" + abstractValue, "VVNBZTZX", "VVHHZTZX",
                    "text/plain", "METAR...");
            gwout.setX400ContentType(abstractValue);

            service.processOutboundMessage(gwout);

            assertEquals(OutboundStatus.FAILED.getValue(), gwout.getStatus(),
                    "content-type " + abstractValue + " phải bị từ chối");
            assertEquals("content-type-not-supported", gwout.getRejectionDiagnostic());
        }
    }

    @Test
    @DisplayName("CTSW016: Process EIT 401 Code")
    void testCTSW016() {
        Gwout gwout = createGwout("TC-CTSW016", "VVNBZTZX", "VVHHZTZX", "application/json", "METAR...");
        gwout.setBodyPartType("401");

        runFullOutboundPipeline(gwout);

        assertEquals("ia5-text-body-part", gwout.getBodyPartType());
        assertEquals(OutboundStatus.PUBLISHED.getValue(), gwout.getStatus());
    }

    @Test
    @DisplayName("CTSW018: Convert General-Text ISO 646 text/plain")
    void testCTSW018() {
        Gwout gwout = createGwout("TC-CTSW018", "VVNBZTZX", "VVHHZTZX", "text/plain", "GENERAL TEXT ISO 646");
        gwout.setBodyPartType("general-text-body-part");

        runFullOutboundPipeline(gwout);

        assertEquals(OutboundStatus.PUBLISHED.getValue(), gwout.getStatus());
    }

    private Gwout createGwout(String amhsid, String origin, String address, String contentType, String text) {
        Gwout g = new Gwout();
        g.setMsgid(100L);
        g.setAmhsid(amhsid);
        g.setOrigin(origin);
        g.setAddress(address);
        g.setContentType(contentType);
        g.setText(text);
        g.setStatus(0);
        return g;
    }
}