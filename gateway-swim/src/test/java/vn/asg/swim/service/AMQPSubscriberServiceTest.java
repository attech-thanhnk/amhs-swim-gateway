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
import vn.asg.swim.entity.InboundStatus;
import vn.asg.swim.model.ResolvedAddressing;
import vn.asg.swim.repository.GwinRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for AMQPSubscriberService - SWIM → AMHS direction.
 * Covers critical bug fixes: Issue #3 (race condition), loopback prevention,
 * authorization, validation.
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
    @org.mockito.Spy private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

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
        when(amqpMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("text/plain; charset=\"utf-8\"");
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn("METAR");
        when(amqpMessage.getStringProperty("amhs_gateway_id")).thenReturn(null); // Default: no loopback

        when(configService.getGatewayId()).thenReturn("ASG-GW-01");
        when(configService.isStrictComplianceMode()).thenReturn(false);

        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateSwimToAmhs(anyString(), any(), anyString()))
            .thenReturn(validResult);
        when(validationService.validateAftnAddress(anyString(), anyString()))
            .thenReturn(validResult);

        when(authorizationService.isSwimUserAuthorized(any())).thenReturn(true);
        when(addressingResolver.resolve(any(), anyString()))
            .thenReturn(resolvedAddressing);
        when(atsmhsResolver.resolve(any(), any(), any())).thenReturn("ENHANCED");
        when(atsmhsResolver.validateContent(any(), any(), anyBoolean())).thenReturn(true);
        when(configService.getMaxMsgRecipients()).thenReturn(20);
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

        // Then: Should reject but still persist the failed message for audit
        verify(gwinRepository).save(argThat(gwin -> gwin.getStatus().equals(InboundStatus.FAILED.getValue())));
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

        // Then: Should reject but still persist the failed message for audit
        verify(gwinRepository).save(argThat(gwin -> gwin.getStatus().equals(InboundStatus.FAILED.getValue())));
        verify(alertService).create(
            eq("VALIDATION_ERROR"),
            eq("ERROR"),
            contains("Missing messageId"),
            eq("gwin"),
            isNull()
        );
    }

    @Test
    void testMissingMessageId_NonStrictMode_StillRejected() throws JMSException {
        // Given: No message-id, non-strict mode (EUR Doc 047 S-06: message-id is mandatory
        // regardless of compliance mode -> isStrictComplianceMode() has no effect here)
        when(amqpMessage.getJMSMessageID()).thenReturn(null);
        when(configService.isStrictComplianceMode()).thenReturn(false);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Rejected but still persisted for audit, same as strict mode
        verify(gwinRepository).save(argThat(gwin -> gwin.getStatus().equals(InboundStatus.FAILED.getValue())));
    }

    // ==================== ATSMHS SERVICE LEVEL ====================

    @Test
    void testAtsmhsBasicMode_BinaryContent_ShouldReject() throws JMSException {
        // Given: BASIC mode cannot handle binary
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        when(atsmhsResolver.resolve(any(), any(), any())).thenReturn("BASIC");
        when(atsmhsResolver.validateContent(eq("BASIC"), any(), eq(true)))
            .thenReturn(false);

        // Setup binary message
        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("test-binary-123");
        when(bytesMessage.getBodyLength()).thenReturn(100L);
        byte[] fakeBinaryContent = new byte[100];
        java.util.Arrays.fill(fakeBinaryContent, (byte) 0x01); // control byte -> correctly detected as non-text
        when(bytesMessage.readBytes(any(byte[].class))).thenAnswer(inv -> {
            byte[] buf = inv.getArgument(0);
            System.arraycopy(fakeBinaryContent, 0, buf, 0, fakeBinaryContent.length);
            return fakeBinaryContent.length;
        });
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

        // Then: Should reject but still persist the failed message for audit
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getStatus().equals(InboundStatus.FAILED.getValue())
        ));
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

        // Then: Should use ats_priority (SS -> AMQP priority 6, per EUR Doc 047 v3.0 Table 3/Table 9)
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getPriority() == 6
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

    @Test
    void testCreationTime_EpochMillis_ShouldBeConvertedToDDhhmm() throws JMSException {
        // Given: creation-time as epoch millis (1787285680974 -> 2026-08-21 04:14:40 UTC -> 210414)
        when(amqpMessage.getStringProperty("creation-time")).thenReturn("1787285680974");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should convert epoch millis to DDhhmm UTC format
        verify(gwinRepository).save(argThat(gwin -> {
            String props = gwin.getAmqpProperties();
            return props.contains("amhs_ats_ft") && props.contains("210414");
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
            assertEquals(InboundStatus.PENDING.getValue(), gwin.getStatus());
            assertEquals("VVHHZPZX", gwin.getOrigin());
            assertEquals("VVHHZTZX VVTSZDYX", gwin.getAddress());
            assertEquals(ResolvedAddressing.SOURCE_ROUTING_RULE, gwin.getAddressingSource());
            assertTrue(gwin.getPayloadContent().contains("\"messageType\": \"METAR\""));
            assertTrue(gwin.getPayloadContent().contains("\"stationIcao\": \"VVTS\""));
            return true;
        }));
    }

    @Test
    void testAmhsUnaware_OctetStreamContentType_ShouldMapToFtbpBodyType() throws JMSException {
        // Given: bản tin SWIM "AMHS-unaware" - không có amhs_bodypart_type, chỉ có content-type
        when(amqpMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("application/octet-stream");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: EUR Doc 047 §4.5.2.4(b) - suy luận bodyType từ content-type khi thiếu amhs_bodypart_type
        verify(gwinRepository).save(argThat(gwin -> {
            assertEquals("ftbp", gwin.getBodyType());
            return true;
        }));
    }

    @Test
    void testPartiallyInvalidRecipients_ShouldDropInvalidOnesNotRejectWhole() throws JMSException {
        // Given: amhs_recipients có 1 địa chỉ hợp lệ + 1 địa chỉ sai định dạng (EUR Doc 047 §4.5.2.9)
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        when(amqpMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX,BADADDR");
        when(validationService.validateAftnAddress(eq("VVHHZTZX"), anyString()))
            .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));
        when(validationService.validateAftnAddress(eq("BADADDR"), anyString()))
            .thenReturn(new MessageValidationService.ValidationResult(false, List.of("bad format")));

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: bản tin KHÔNG bị từ chối cả gói, chỉ loại recipient sai, và báo Control Position
        verify(gwinRepository).save(argThat(gwin -> {
            assertEquals(InboundStatus.PENDING.getValue(), gwin.getStatus());
            assertEquals("VVHHZTZX", gwin.getAddress());
            return true;
        }));
        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR), eq(GwAlert.SEV_WARNING),
            contains("BADADDR"), eq("gwin"), isNull()
        );
    }

    @Test
    void testAllRecipientsInvalid_ShouldRejectWhole() throws JMSException {
        // Given: TẤT CẢ recipient đều sai định dạng -> phải từ chối cả bản tin (EUR Doc 047 §4.5.2.9c)
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        when(amqpMessage.getStringProperty("amhs_recipients")).thenReturn("BADADDR1,BADADDR2");
        when(validationService.validateAftnAddress(anyString(), anyString()))
            .thenReturn(new MessageValidationService.ValidationResult(false, List.of("bad format")));

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getStatus().equals(InboundStatus.FAILED.getValue())
        ));
    }

    @Test
    void testUnresolvedAddressing_ShouldRejectAsMandatoryFieldMissing() throws JMSException {
        // Given: Addressing cannot be resolved -> amhs_recipients ends up empty
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        ResolvedAddressing unresolved = new ResolvedAddressing(
            null, null, ResolvedAddressing.SOURCE_UNRESOLVED
        );
        when(addressingResolver.resolve(any(), anyString()))
            .thenReturn(unresolved);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: EUR Doc 047 treats amhs_recipients as mandatory -> rejected (still persisted for audit)
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getStatus().equals(InboundStatus.FAILED.getValue())
        ));
    }

    @Test
    void testPlainTextPayload_ShouldNotThrowNpe() throws JMSException {
        // Given: Plain text message like ICAO FPL (not JSON root)
        String plainTextFpl = "(FPL-HVN123-IS-B738/M-SDE2E3FGHIJ1RW/S-VVTS0200-N0450F350 DCT-VVNB0140 DCT)";
        when(textMessage.getText()).thenReturn(plainTextFpl);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When & Then: Should process without NullPointerException
        assertDoesNotThrow(() -> service.handleMessage(amqpMessage, "ats/fpl/flightplan"));
        verify(gwinRepository).save(argThat(gwin -> gwin.getMessageId().equals("test-msg-123")));
    }

    @Test
    void testJsonPayload_ShouldBeForwardedUnchanged() throws Exception {
        String jsonFpl = """
            {
                "messageId": "FPL_TEXT_12345",
                "messageType": "FPL",
                "recipients": "VVVVNVNV"
            }
            """;
        when(textMessage.getText()).thenReturn(jsonFpl);
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn("SWIM_INTERWORKING");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "ats/fpl/flightplan");

        // Content must pass through unconverted (ICAO Doc 047: keep original content regardless of direction)
        verify(gwinRepository).save(argThat(gwin -> gwin.getPayloadContent().equals(jsonFpl)));
    }

    @Test
    void testTextPlainContentTypeNotTreatedAsBinary() throws Exception {
        when(amqpMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("text/plain; charset=\"utf-8\"");
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn("FPL");
        when(textMessage.getText()).thenReturn("Sample text with \u0000 NUL char");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "ats/fpl/flightplan");

        verify(gwinRepository).save(argThat(gwin -> gwin.getStatus() != InboundStatus.FAILED.getValue()));
    }
}