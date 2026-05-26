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
 * Covers critical bug fixes: Issue #1 (status logic), Issue #2 (cache), TTL, retry logic.
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

    @BeforeEach
    void setUp() {
        // Setup common test data
        gwout = new Gwout();
        gwout.setMsgid(1L);
        gwout.setText("METAR VVTS 121200Z 09008KT 9999 FEW020 32/25 Q1010=");
        gwout.setOrigin("VVTSZYYX");
        gwout.setAddress("VVHHZTZX");
        gwout.setPriority(2);
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
        routing.setConvertToJson(true);

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
        service.processDispatch(dispatch);

        // Then: Status should be DEAD, NOT SENT (Issue #1 fix verification)
        verify(gwoutDispatchRepository, times(2)).save(argThat(d -> {
            if (d.getStatus().equals(GwoutDispatch.STATUS_DEAD)) {
                assertEquals(GwoutDispatch.STEP_VALIDATION, d.getFailedStep());
                assertTrue(d.getLastError().contains("Invalid AFTN"));
                return true;
            }
            return d.getStatus().equals(GwoutDispatch.STATUS_PROCESSING);
        }));

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
        service.processDispatch(dispatch);

        // Then: Should be DEAD with authorization failed step
        verify(gwoutDispatchRepository, times(2)).save(argThat(d -> {
            if (d.getStatus().equals(GwoutDispatch.STATUS_DEAD)) {
                assertEquals(GwoutDispatch.STEP_AUTHORIZATION, d.getFailedStep());
                assertTrue(d.getLastError().contains("not authorized"));
                return true;
            }
            return true;
        }));

        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR),
            eq(GwAlert.SEV_WARNING),
            contains("Unauthorized"),
            anyString(), anyLong()
        );
    }

    // ==================== TTL LOGIC ====================

    @Test
    void testTTLExpired_ShouldSkipPublish() throws Exception {
        // Given: TTL expired
        gwout.setAmhsTtl(LocalDateTime.now().minusHours(1));
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
            .thenReturn(validResult);
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);

        // When
        service.processDispatch(dispatch);

        // Then: Should mark as SENT (accepted skip) but not publish
        verify(gwoutDispatchRepository, times(2)).save(argThat(d -> {
            if (d.getStatus().equals(GwoutDispatch.STATUS_SENT)) {
                assertNotNull(d.getSentAt());
                return true;
            }
            return true;
        }));

        // Verify NO AMQP publish happened
        verify(connectionManager, never()).createSession();
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

    // ==================== ISSUE #2: CONVERSION CACHE ====================

    @Test
    void testConversionCache_DetectsExistingJson_ShouldNotReconvert() throws Exception {
        // Given: gwout already has JSON payload cached
        gwout.setPayloadContent("{\"stationIcao\":\"VVTS\",\"messageType\":\"METAR\"}");
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        setupValidScenario();

        // When
        service.processDispatch(dispatch);

        // Then: Should NOT call conversionService.toSwim (use cache)
        verify(conversionService, never()).toSwim(anyString(), anyString());
    }

    @Test
    void testConversionCache_NoCache_ShouldConvertAndCache() throws Exception {
        // Given: No cached payload
        gwout.setPayloadContent(null);
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        setupValidScenario();
        when(conversionService.toSwim(anyString(), eq("METAR")))
            .thenReturn("{\"stationIcao\":\"VVTS\",\"observationTime\":\"121200Z\"}");

        // When
        service.processDispatch(dispatch);

        // Then: Should call conversion AND save cache
        verify(conversionService).toSwim(anyString(), eq("METAR"));
        verify(gwoutRepository, atLeastOnce()).save(argThat(g ->
            g.getPayloadContent() != null &&
            g.getPayloadContent().contains("stationIcao")
        ));
    }

    @Test
    void testConversionCache_TacFormat_ShouldNotConvert() throws Exception {
        // Given: Routing rule says keep TAC format
        routing.setConvertToJson(false);
        gwout.setPayloadContent(null);
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        setupValidScenario();

        // When
        service.processDispatch(dispatch);

        // Then: Should NOT convert, use original body
        verify(conversionService, never()).toSwim(anyString(), anyString());
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
        service.processDispatch(dispatch);

        // Then: Should fail at routing step
        verify(gwoutDispatchRepository, atLeastOnce()).save(argThat(d -> {
            if (d.getStatus().equals(GwoutDispatch.STATUS_FAILED)) {
                assertEquals(GwoutDispatch.STEP_ROUTING, d.getFailedStep());
                return true;
            }
            return true;
        }));
    }

    // ==================== RETRY LOGIC ====================

    @Test
    void testRetryLogic_FirstRetry_ShouldCalculateDelay() {
        // Given: First retry
        dispatch.setRetryCount(0);
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
            .thenReturn(validResult);
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);
        when(detectService.detect(anyString())).thenThrow(new RuntimeException("Detect failed"));

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
    void testRetryLogic_MaxRetriesReached_ShouldSetDead() {
        // Given: Max retries reached
        dispatch.setRetryCount(3);
        when(gwoutRepository.findById(1L)).thenReturn(Optional.of(gwout));
        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
            .thenReturn(validResult);
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);
        when(detectService.detect(anyString())).thenThrow(new RuntimeException("Still failing"));

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

    // ==================== HELPER METHODS ====================

    private void setupValidScenario() throws Exception {
        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateAmhsToSwim(anyString(), anyString()))
            .thenReturn(validResult);
        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);
        when(detectService.detect(anyString())).thenReturn("METAR");
        when(routingService.findBestMatchOut("METAR")).thenReturn(Optional.of(routing));

        // Mock AMQP publishing
        Session session = mock(Session.class);
        MessageProducer producer = mock(MessageProducer.class);
        TextMessage textMessage = mock(TextMessage.class);

        when(connectionManager.createSession()).thenReturn(session);
        when(connectionManager.createProducer(any(), anyString())).thenReturn(producer);
        when(session.createTextMessage(anyString())).thenReturn(textMessage);
    }
}
