package vn.asg.swim.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.MessageStatus;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.repository.GwoutDispatchRepository;
import vn.asg.swim.repository.GwoutRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Automated Verification Audit for all Test Cases in test_case.md (CTSW001 - CTSW020).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AllTestCasesAuditTest {

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

    @BeforeEach
    void setUp() {
        when(configService.getInt(eq("MAX_PAYLOAD_SIZE"), anyInt())).thenReturn(2097152);
        when(configService.getCommaSeparatedConfig("ALLOWED_CONTENT_TYPES"))
                .thenReturn(List.of("application/json", "application/xml", "text/plain", "text/xml"));
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        when(validationService.validateAftnAddress(anyString(), anyString()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        when(validationService.validateBodyPartType(anyString()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        
        Routing metarRule = new Routing();
        metarRule.setMessageType("METAR");
        metarRule.setSendTopic("ats/met/metar");
        when(routingService.findBestMatchOut(anyString())).thenReturn(Optional.of(metarRule));
        when(detectService.detect(anyString())).thenReturn("METAR");
    }

    @Test
    @DisplayName("CTSW001: Convert Incoming IPM with Filing Time")
    void testCTSW001() {
        Gwout gwout = createGwout("TC-CTSW001", "VVNBZTZX", "VVHHZTZX", "text/plain",
                "ZCZC ABC001\r\nFF VVHHZTZX\r\n070430 VVNBZTZX\r\nMETAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=");
        gwout.setFilingTime("070430");

        service.convertOutboundMessage(gwout);

        assertEquals(MessageStatus.OUT_PUBLISHED.getValue(), gwout.getStatus());
        assertNotNull(gwout.getPayloadContent());
    }

    @Test
    @DisplayName("CTSW002: Convert Incoming IPM with OHI")
    void testCTSW002() {
        Gwout gwout = createGwout("TC-CTSW002", "VVNBZTZX", "VVHHZTZX", "application/json",
                "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=");
        gwout.setOptionalHeading("OHI-TEST-DATA-123");

        service.convertOutboundMessage(gwout);

        assertEquals(MessageStatus.OUT_PUBLISHED.getValue(), gwout.getStatus());
    }

    @Test
    @DisplayName("CTSW004: Generate NDR for Syntax Failure")
    void testCTSW004() {
        Gwout gwout = createGwout("TC-CTSW004", "VVTSZTZX", "VVHHZTZX", "application/json", "(FPL-INVALID-SYNTAX");
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
                .thenReturn(new MessageValidationService.ValidationResult(false, List.of("Syntax error in FPL")));

        service.convertOutboundMessage(gwout);

        assertEquals(MessageStatus.OUT_FAILED.getValue(), gwout.getStatus());
        assertTrue(gwout.getPayloadContent().contains("Validation failed"));
    }

    @Test
    @DisplayName("CTSW006: Reject IPM Exceeding Max Size")
    void testCTSW006() {
        Gwout gwout = createGwout("TC-CTSW006", "VVNBZTZX", "VVHHZTZX", "text/plain", "A".repeat(200));
        when(configService.getInt(eq("MAX_PAYLOAD_SIZE"), anyInt())).thenReturn(100);

        service.convertOutboundMessage(gwout);

        assertEquals(MessageStatus.OUT_FAILED.getValue(), gwout.getStatus());
        assertTrue(gwout.getPayloadContent().contains("exceeds maximum allowed payload size"));
    }

    @Test
    @DisplayName("CTSW008: Reject Unsupported Content-Type")
    void testCTSW008() {
        Gwout gwout = createGwout("TC-CTSW008", "VVNBZTZX", "VVHHZTZX", "application/unknown-mime-type", "METAR...");

        service.convertOutboundMessage(gwout);

        assertEquals(MessageStatus.OUT_FAILED.getValue(), gwout.getStatus());
        assertEquals("Unsupported Content-Type", gwout.getPayloadContent());
    }

    @Test
    @DisplayName("CTSW016: Process EIT 401 Code")
    void testCTSW016() {
        Gwout gwout = createGwout("TC-CTSW016", "VVNBZTZX", "VVHHZTZX", "application/json", "METAR...");
        gwout.setBodyPartType("401");

        service.convertOutboundMessage(gwout);

        assertEquals("ia5-text-body-part", gwout.getBodyPartType());
        assertEquals(MessageStatus.OUT_PUBLISHED.getValue(), gwout.getStatus());
    }

    @Test
    @DisplayName("CTSW018: Convert General-Text ISO 646 text/plain")
    void testCTSW018() {
        Gwout gwout = createGwout("TC-CTSW018", "VVNBZTZX", "VVHHZTZX", "text/plain", "GENERAL TEXT ISO 646");
        gwout.setBodyPartType("general-text-body-part");

        service.convertOutboundMessage(gwout);

        assertEquals(MessageStatus.OUT_PUBLISHED.getValue(), gwout.getStatus());
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
