package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwin;
import vn.asg.swim.model.ResolvedAddressing;
import vn.asg.swim.repository.GwinRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for AMQPSubscriberService - SWIM → AMHS direction.
 * Covers critical bug fixes: Issue #3 (race condition), Issue #11 (conversion alert),
 * loopback prevention, authorization, validation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AMQPSubscriberServiceTest {

    @Mock private ConnectionManagerService connectionManager;
    @Mock private RoutingService routingService;
    @Mock private MessageConversionService conversionService;
    @Mock private AddressingResolverService addressingResolver;
    @Mock private AlertService alertService;
    @Mock private GwinRepository gwinRepository;
    @Mock private MessageValidationService validationService;
    @Mock private AuthorizationService authorizationService;
    @Mock private AtsmhsServiceLevelResolver atsmhsResolver;
    @Mock private ConfigService configService;
    @Mock private MessageDetectService detectService;

    @InjectMocks
    private AMQPSubscriberService service;

    private Message amqpMessage;
    private TextMessage textMessage;
    private String jsonPayload;
    private ResolvedAddressing resolvedAddressing;

    @BeforeEach
    void setUp() throws Exception {
        textMessage = mock(TextMessage.class);
        amqpMessage = textMessage;

        jsonPayload = """
            {
                "messageType": "METAR",
                "stationIcao": "VVTS",
                "observationTime": "121200Z",
                "wind": {"direction": 90, "speed": 8, "unit": "KT"},
                "visibility": 9999,
                "clouds": [{"amount": "FEW", "height": 2000}],
                "temperature": 32,
                "dewpoint": 25,
                "qnh": 1010
            }
            """;

        resolvedAddressing = new ResolvedAddressing(
            "VVHHZPZX",
            "VVHHZTZX VVTSZDYX",
            ResolvedAddressing.SOURCE_ROUTING_RULE
        );

        // Default mocks
        when(amqpMessage.getJMSMessageID()).thenReturn("test-msg-123");
        when(textMessage.getText()).thenReturn(jsonPayload);
        when(amqpMessage.getJMSPriority()).thenReturn(2);
        when(amqpMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("application/json");
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn("METAR");
        when(amqpMessage.getStringProperty("amhs_gateway_id")).thenReturn(null); // Default: no loopback

        when(configService.getGatewayId()).thenReturn("ASG-GW-01");
        when(configService.isStrictComplianceMode()).thenReturn(false);

        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateSwimToAmhs(anyString(), any(), anyString()))
            .thenReturn(validResult);

        when(authorizationService.isSwimUserAuthorized(any())).thenReturn(true);
        when(addressingResolver.resolve(any(), anyString(), anyString()))
            .thenReturn(resolvedAddressing);
        when(atsmhsResolver.resolve(anyString(), anyString())).thenReturn("ENHANCED");
        when(atsmhsResolver.validateContent(anyString(), anyString(), anyBoolean())).thenReturn(true);
        when(detectService.detect(anyString())).thenReturn("METAR");
        when(conversionService.toAmhs(anyString(), anyString())).thenReturn("METAR VVTS 121200Z=");
    }

    // ==================== DEDUPLICATION ====================

    @Test
    void testDeduplication_ExistingMessage_ShouldIgnore() throws JMSException {
        // Given: Message already exists in database
        when(gwinRepository.existsByMessageId("test-msg-123")).thenReturn(true);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should not save again
        verify(gwinRepository, never()).save(any());
    }

    @Test
    void testDeduplication_NewMessage_ShouldSave() throws JMSException {
        // Given: Message does not exist
        when(gwinRepository.existsByMessageId("test-msg-123")).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should save
        verify(gwinRepository).save(any(Gwin.class));
    }

    // ==================== ISSUE #3: RACE CONDITION ====================

    @Test
    void testRaceCondition_DuplicateInsert_ShouldHandleGracefully() throws JMSException {
        // Given: Two threads process same message, second one hits unique constraint
        when(gwinRepository.existsByMessageId("test-msg-123")).thenReturn(false);
        when(gwinRepository.save(any(Gwin.class)))
            .thenThrow(new DataIntegrityViolationException("Duplicate entry for message_id"));

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should NOT crash, should log and return gracefully
        verify(gwinRepository).save(any(Gwin.class));
        // No exception should be thrown
    }

    // ==================== LOOPBACK PREVENTION ====================

    @Test
    void testLoopbackPrevention_SameGatewayId_ShouldDrop() throws JMSException {
        // Given: Message came from this gateway (loopback)
        when(amqpMessage.getStringProperty("amhs_gateway_id")).thenReturn("ASG-GW-01");

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should drop message without saving
        verify(gwinRepository, never()).save(any());
    }

    @Test
    void testLoopbackPrevention_DifferentGatewayId_ShouldProcess() throws JMSException {
        // Given: Message from different gateway
        when(amqpMessage.getStringProperty("amhs_gateway_id")).thenReturn("OTHER-GW-99");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should process normally
        verify(gwinRepository).save(any(Gwin.class));
    }

    // ==================== AUTHORIZATION ====================

    @Test
    void testAuthorization_Unauthorized_ShouldReject() throws JMSException {
        // Given: User not authorized
        when(authorizationService.isSwimUserAuthorized(any())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should reject and create alert
        verify(gwinRepository, never()).save(any());
        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR),
            eq(GwAlert.SEV_WARNING),
            contains("Unauthorized"),
            eq("gwin"),
            isNull()
        );
        verify(conversionService).logSwimToAmhs(
            eq("test-msg-123"),
            isNull(),
            eq("REJECTED"),
            eq("unauthorized"),
            anyString()
        );
    }

    @Test
    void testAuthorization_Authorized_ShouldProcess() throws JMSException {
        // Given: User authorized
        when(authorizationService.isSwimUserAuthorized(any())).thenReturn(true);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should process
        verify(gwinRepository).save(any(Gwin.class));
    }

    // ==================== VALIDATION ====================

    @Test
    void testValidation_Invalid_ShouldReject() throws JMSException {
        // Given: Validation fails
        MessageValidationService.ValidationResult invalidResult =
            new MessageValidationService.ValidationResult(false, List.of("Invalid XML schema"));
        when(validationService.validateSwimToAmhs(anyString(), any(), anyString()))
            .thenReturn(invalidResult);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should reject with alert
        verify(gwinRepository, never()).save(any());
        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR),
            eq(GwAlert.SEV_ERROR),
            contains("validation failed"),
            eq("gwin"),
            isNull()
        );
    }

    // ==================== MISSING MESSAGE-ID ====================

    @Test
    void testMissingMessageId_StrictMode_ShouldReject() throws JMSException {
        // Given: No message-id in strict mode
        when(amqpMessage.getJMSMessageID()).thenReturn(null);
        when(configService.isStrictComplianceMode()).thenReturn(true);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should reject
        verify(gwinRepository, never()).save(any());
        verify(alertService).create(
            eq("VALIDATION_ERROR"),
            eq("ERROR"),
            contains("message-id"),
            eq("gwin"),
            isNull()
        );
    }

    @Test
    void testMissingMessageId_NonStrictMode_ShouldGenerateSynthetic() throws JMSException {
        // Given: No message-id in non-strict mode
        when(amqpMessage.getJMSMessageID()).thenReturn(null);
        when(configService.isStrictComplianceMode()).thenReturn(false);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should generate synthetic ID and process
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getMessageId() != null && gwin.getMessageId().startsWith("GW-GEN-")
        ));
    }

    // ==================== ISSUE #11: CONVERSION FAILURE ALERT ====================

    @Test
    void testConversionFailure_ShouldCreateAlert() throws Exception {
        // Given: Conversion fails
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        when(conversionService.toAmhs(anyString(), anyString()))
            .thenThrow(new RuntimeException("Conversion failed: Invalid format"));

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should save with UNROUTED status and create alert
        verify(gwinRepository).save(argThat(gwin -> {
            assertEquals(Gwin.STATUS_UNROUTED, gwin.getStatus());
            assertTrue(gwin.getText().contains("CONVERSION_FAILED"));
            return true;
        }));
        verify(alertService).create(
            eq(GwAlert.TYPE_CONVERT_ERROR),
            eq(GwAlert.SEV_WARNING),
            contains("Conversion failed"),
            eq("gwin"),
            isNull()
        );
    }

    // ==================== ATSMHS SERVICE LEVEL ====================

    @Test
    void testAtsmhsBasicMode_BinaryContent_ShouldReject() throws JMSException {
        // Given: BASIC mode cannot handle binary
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        when(atsmhsResolver.resolve(anyString(), anyString())).thenReturn("BASIC");
        when(atsmhsResolver.validateContent(eq("BASIC"), anyString(), eq(true)))
            .thenReturn(false);

        // Setup binary message
        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("test-binary-123");
        when(bytesMessage.getBodyLength()).thenReturn(100L);
        when(bytesMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE"))
            .thenReturn("application/octet-stream");
        when(bytesMessage.getJMSPriority()).thenReturn(2);
        when(authorizationService.isSwimUserAuthorized(any())).thenReturn(true);

        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateSwimToAmhs(anyString(), any(), anyString()))
            .thenReturn(validResult);

        // When
        service.handleMessage(bytesMessage, "swim.test.queue");

        // Then: Should reject
        verify(gwinRepository, never()).save(any());
        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR),
            eq(GwAlert.SEV_ERROR),
            contains("Binary content rejected"),
            eq("gwin"),
            isNull()
        );
    }

    // ==================== PRIORITY MAPPING ====================

    @Test
    void testPriorityMapping_AtsPriorityProperty_ShouldOverride() throws JMSException {
        // Given: ats_priority property present
        when(amqpMessage.getStringProperty("ats_priority")).thenReturn("SS");
        when(amqpMessage.getJMSPriority()).thenReturn(2); // This should be overridden
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should use ats_priority (SS = 0, Flash/highest priority in this system)
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getPriority() == 0
        ));
    }

    // ==================== AMQP PROPERTIES PRESERVATION ====================

    @Test
    void testAmqpProperties_ShouldBePreserved() throws JMSException {
        // Given: Various AMQP properties
        when(amqpMessage.getStringProperty("ats_priority")).thenReturn("FF");
        when(amqpMessage.getStringProperty("amhs_ats_ft")).thenReturn("121200");
        when(amqpMessage.getStringProperty("amhs_ats_ohi")).thenReturn("TEST OHI");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should save all properties in JSON
        verify(gwinRepository).save(argThat(gwin -> {
            String props = gwin.getAmqpProperties();
            return props.contains("ats_priority") &&
                   props.contains("FF") &&
                   props.contains("amhs_ats_ft") &&
                   props.contains("121200");
        }));
    }

    // ==================== SUCCESSFUL PROCESSING ====================

    @Test
    void testSuccessfulProcessing_WithResolvedAddressing() throws JMSException {
        // Given: All valid, addressing resolved
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should save with PENDING status
        verify(gwinRepository).save(argThat(gwin -> {
            assertEquals(Gwin.STATUS_PENDING, gwin.getStatus());
            assertEquals("VVHHZPZX", gwin.getOrigin());
            assertEquals("VVHHZTZX VVTSZDYX", gwin.getAddress());
            assertEquals(ResolvedAddressing.SOURCE_ROUTING_RULE, gwin.getAddressingSource());
            assertTrue(gwin.getText().contains("METAR VVTS"));
            return true;
        }));
    }

    @Test
    void testUnresolvedAddressing_ShouldSetUnrouted() throws JMSException {
        // Given: Addressing cannot be resolved
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        ResolvedAddressing unresolved = new ResolvedAddressing(
            null, null, ResolvedAddressing.SOURCE_UNRESOLVED
        );
        when(addressingResolver.resolve(any(), anyString(), anyString()))
            .thenReturn(unresolved);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should save with UNROUTED status
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getStatus().equals(Gwin.STATUS_UNROUTED)
        ));
    }
}
