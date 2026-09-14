package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

        // Default mocks
        when(amqpMessage.getJMSMessageID()).thenReturn("test-msg-123");
        when(textMessage.getText()).thenReturn(jsonPayload);
        when(amqpMessage.getJMSPriority()).thenReturn(2);
        when(amqpMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        when(amqpMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("text/plain; charset=\"utf-8\"");
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn("METAR");
        when(amqpMessage.getStringProperty("amhs_gateway_id")).thenReturn(null); // Default: no loopback
        // amhs_recipients/amhs_originator lấy trực tiếp từ AMQP properties
        when(amqpMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX VVTSZDYX");
        when(amqpMessage.getStringProperty("amhs_originator")).thenReturn("VVHHZPZX");

        when(configService.getGatewayId()).thenReturn("ASG-GW-01");

        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateSwimToAmhs(anyString(), any(), anyString(), anyInt()))
            .thenReturn(validResult);
        when(validationService.validateAftnAddress(anyString(), anyString()))
            .thenReturn(validResult);

        when(authorizationService.isSwimUserAuthorized(any())).thenReturn(true);
        when(atsmhsResolver.resolve(any(), any())).thenReturn("ENHANCED");
        when(atsmhsResolver.validateContent(any(), any(), anyBoolean())).thenReturn(true);
        when(configService.getMaxMsgRecipients()).thenReturn(20);
        // JpaRepository.save() luôn trả về entity đã persist, không bao giờ null. Mock mặc định trả
        // null khiến mọi nhánh reject NPE ở saved.getMsgid() - stub lại cho khớp hành vi thật.
        when(gwinRepository.save(any(Gwin.class))).thenAnswer(inv -> inv.getArgument(0));
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
        when(validationService.validateSwimToAmhs(anyString(), any(), anyString(), anyInt()))
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
    void testMissingMessageId_ShouldReject() throws JMSException {
        // Given: No message-id
        when(amqpMessage.getJMSMessageID()).thenReturn(null);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should reject but still persist the failed message for audit
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getStatus().equals(InboundStatus.FAILED.getValue()) &&
            "validation-failed".equals(gwin.getRejectionReason()) &&
            gwin.getRejectionDiagnostic().contains("Missing messageId")
        ));
        verify(alertService).create(
            eq("VALIDATION_ERROR"),
            eq("ERROR"),
            contains("Missing messageId"),
            eq("gwin"),
            isNull()
        );
    }

    // ==================== ATSMHS SERVICE LEVEL ====================

    @Test
    void testAtsmhsBasicMode_BinaryContent_ShouldReject() throws JMSException {
        // Given: BASIC mode cannot handle binary
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        // Code doc che do thang tu gateway_config (khong qua resolver), nen phai stub dung cho nay
        when(configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL)).thenReturn("BASIC");
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
        when(bytesMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX");
        when(bytesMessage.getJMSPriority()).thenReturn(2);
        when(bytesMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        when(authorizationService.isSwimUserAuthorized(any())).thenReturn(true);

        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateSwimToAmhs(anyString(), any(), anyString(), anyInt()))
            .thenReturn(validResult);

        // When
        service.handleMessage(bytesMessage, "swim.test.queue");

        // Then: Should reject but still persist the failed message for audit
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getStatus().equals(InboundStatus.FAILED.getValue()) &&
            "atsmhs-validation-failed".equals(gwin.getRejectionReason()) &&
            "Binary content not supported in BASIC mode".equals(gwin.getRejectionDiagnostic())
        ));
        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR),
            eq(GwAlert.SEV_ERROR),
            contains("Binary content rejected"),
            eq("gwin"),
            isNull()
        );
    }

    @Test
    void testAtsmhsBasicMode_SmallPdf_ShouldStillReject() throws JMSException {
        // Kiểm tra trường hợp PDF nhỏ, không dùng text heuristic mà qua resolver
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        // Code doc che do thang tu gateway_config (khong qua resolver), nen phai stub dung cho nay
        when(configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL)).thenReturn("BASIC");
        when(atsmhsResolver.validateContent(eq("BASIC"), any(), eq(true))).thenReturn(false);
        when(atsmhsResolver.validateContent(eq("BASIC"), any(), eq(false))).thenReturn(true);

        byte[] smallPdf = ("%PDF-1.4\n"
            + "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
            + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
            + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R>>endobj\n"
            + "4 0 obj<</Length 44>>stream\nBT /F1 24 Tf 100 700 Td (CTSW103-2) Tj ET\nendstream endobj\n"
            + "trailer<</Root 1 0 R>>\n%%EOF\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("test-ctsw103-2-small-pdf");
        when(bytesMessage.getBodyLength()).thenReturn((long) smallPdf.length);
        when(bytesMessage.readBytes(any(byte[].class))).thenAnswer(inv -> {
            byte[] buf = inv.getArgument(0);
            System.arraycopy(smallPdf, 0, buf, 0, smallPdf.length);
            return smallPdf.length;
        });
        when(bytesMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE"))
            .thenReturn("application/octet-stream");
        when(bytesMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX");
        when(bytesMessage.getJMSPriority()).thenReturn(2);
        when(bytesMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        when(authorizationService.isSwimUserAuthorized(any())).thenReturn(true);
        when(validationService.validateSwimToAmhs(anyString(), any(), anyString(), anyInt()))
            .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));

        // When
        service.handleMessage(bytesMessage, "swim.test.queue");

        // Then: bị từ chối, lưu lại để audit, và payload giữ nguyên dạng base64 (không bị UTF-8 decode)
        String expectedBase64 = java.util.Base64.getEncoder().encodeToString(smallPdf);
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getStatus().equals(InboundStatus.FAILED.getValue())
                && "atsmhs-validation-failed".equals(gwin.getRejectionReason())
                && "Binary content not supported in BASIC mode".equals(gwin.getRejectionDiagnostic())
                && expectedBase64.equals(gwin.getPayloadContent())
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

        // Then: Should use ats_priority (SS -> priority 6)
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getPriority() == 6
        ));
    }

    @Test
    void testSubjectMapping_AmhsSubjectPresent_ShouldOverrideSubjectProperty() throws JMSException {
        // Cả amhs_subject và subject đều có -> amhs_subject thắng
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn("subject from application property field");
        when(amqpMessage.getStringProperty("subject")).thenReturn("subject from properties");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                "subject from application property field".equals(gwin.getSubject())
        ));
    }

    @Test
    void testSubjectMapping_AmhsSubjectBlank_ShouldFallBackToPropertiesSubject() throws JMSException {
        // amhs_subject rỗng -> dùng subject (AMQP Properties section)
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn("");
        when(amqpMessage.getStringProperty("subject")).thenReturn("SWIM_INTERWORKING");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                "SWIM_INTERWORKING".equals(gwin.getSubject())
        ));
    }

    @Test
    void testSubjectMapping_StandardAmqpPropertiesSubjectViaJMSType() throws JMSException {
        // AMQP 1.0 properties.subject được map sang JMSType header
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn(null);
        when(amqpMessage.getStringProperty("subject")).thenReturn(null);
        when(amqpMessage.getJMSType()).thenReturn("SWIM_INTERWORKING");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                "SWIM_INTERWORKING".equals(gwin.getSubject())
        ));
    }

    @Test
    void testSubjectMapping_SubjectPropertyBlank_ShouldUseAmhsSubject() throws JMSException {
        // subject rỗng, amhs_subject có giá trị -> dùng amhs_subject
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn("Subject example");
        when(amqpMessage.getStringProperty("subject")).thenReturn("");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                "Subject example".equals(gwin.getSubject())
        ));
    }

    @Test
    void testSubjectMapping_OverLength_ShouldTrimTo128Chars() throws JMSException {
        // subject > 128 ký tự -> cắt còn đúng 128 ký tự đầu
        String longSubject = "A".repeat(140);
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn("");
        when(amqpMessage.getStringProperty("subject")).thenReturn(longSubject);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin -> {
            String s = gwin.getSubject();
            return s != null && s.length() == 128 && s.equals(longSubject.substring(0, 128));
        }));
    }

    // ==================== FTBP BINARY / GZIP ====================

    @Test
    void testBinaryFtbp_NonUtf8SafeBytes_ShouldBeBase64EncodedNotCorrupted() throws JMSException {
        // Bug đã sửa: nội dung binary trước đây bị ép decode UTF-8 (làm hỏng byte không hợp lệ
        // UTF-8 thành U+FFFD) thay vì base64-encode. Dùng byte 0xFF (không hợp lệ ở vị trí đầu
        // UTF-8) để chứng minh payload_content phải là base64 nguyên vẹn, không bị mất dữ liệu.
        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("test-ctsw116-binary");
        when(bytesMessage.getJMSPriority()).thenReturn(4);
        when(bytesMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        byte[] rawBinary = new byte[]{0x01, 0x00, 0x02, 0x03, (byte) 0xFF, (byte) 0x80, (byte) 0x81, 0x41};
        when(bytesMessage.getBodyLength()).thenReturn((long) rawBinary.length);
        when(bytesMessage.readBytes(any(byte[].class))).thenAnswer(inv -> {
            byte[] buf = inv.getArgument(0);
            System.arraycopy(rawBinary, 0, buf, 0, rawBinary.length);
            return rawBinary.length;
        });
        when(bytesMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("application/octet-stream");
        when(bytesMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(bytesMessage, "swim.test.queue");

        String expectedBase64 = java.util.Base64.getEncoder().encodeToString(rawBinary);
        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.PENDING.getValue())
                        && expectedBase64.equals(gwin.getPayloadContent())
        ));
    }

    @Test
    void testFtbpAttributes_ShouldBeForwardedVerbatim() throws JMSException {
        // Forward nguyên văn amhs_ftbp_file_name/object_size/last_mod
        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("test-ctsw116-ftbp-attrs");
        when(bytesMessage.getJMSPriority()).thenReturn(4);
        when(bytesMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        byte[] rawBinary = new byte[]{0x01, 0x02, 0x03, 0x04};
        when(bytesMessage.getBodyLength()).thenReturn((long) rawBinary.length);
        when(bytesMessage.readBytes(any(byte[].class))).thenAnswer(inv -> {
            byte[] buf = inv.getArgument(0);
            System.arraycopy(rawBinary, 0, buf, 0, rawBinary.length);
            return rawBinary.length;
        });
        when(bytesMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("application/octet-stream");
        when(bytesMessage.getStringProperty("amhs_ftbp_file_name")).thenReturn("flight_plan_data.bin");
        when(bytesMessage.getStringProperty("amhs_ftbp_object_size")).thenReturn("1024");
        when(bytesMessage.getStringProperty("amhs_ftbp_last_mod")).thenReturn("20260723142742Z");
        when(bytesMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(bytesMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin -> {
            String props = gwin.getAmqpProperties();
            return gwin.getStatus().equals(InboundStatus.PENDING.getValue())
                    && props.contains("flight_plan_data.bin")
                    && props.contains("1024")
                    && props.contains("20260723142742Z");
        }));
    }

    @Test
    void testGzipCompressedFtbp_ShouldDecompressAndBase64EncodeNotCorrupted() throws Exception {
        // Data nén gzip -> giải nén, rồi base64-encode dữ liệu gốc
        byte[] originalBinary = new byte[]{0x01, 0x02, 0x03, (byte) 0xFF, (byte) 0xFE, 0x10, 0x20, 0x30};
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(baos)) {
            gzos.write(originalBinary);
        }
        byte[] compressed = baos.toByteArray();

        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("test-ctsw116-gzip");
        when(bytesMessage.getJMSPriority()).thenReturn(4);
        when(bytesMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        when(bytesMessage.getBodyLength()).thenReturn((long) compressed.length);
        when(bytesMessage.readBytes(any(byte[].class))).thenAnswer(inv -> {
            byte[] buf = inv.getArgument(0);
            System.arraycopy(compressed, 0, buf, 0, compressed.length);
            return compressed.length;
        });
        when(bytesMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("application/octet-stream");
        when(bytesMessage.getStringProperty("swim_compression")).thenReturn("gzip");
        when(bytesMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(bytesMessage, "swim.test.queue");

        String expectedBase64 = java.util.Base64.getEncoder().encodeToString(originalBinary);
        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.PENDING.getValue())
                        && expectedBase64.equals(gwin.getPayloadContent())
        ));
    }

    // ==================== BODYPART TYPE / CONTENT ENCODING ====================

    @Test
    void testBodyPartTypeAndEncoding_AllFourValidCombinations_ShouldBeForwardedVerbatim() throws JMSException {
        // Forward nguyên văn amhs_bodypart_type và amhs_content_encoding
        record Case(String bodyPartType, String encoding, String value) {}
        List<Case> cases = List.of(
                new Case("ia5-text", "IA5", "Lorem ipsum"),
                new Case("ia5-text-body-part", "IA5", "Lorem ipsum i5bpt"),
                new Case("general-text-body-part", "ISO-646", "Lorem ipsum 646"),
                new Case("general-text-body-part", "ISO-8859-1", "Lorem ipsum 8859")
        );
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        ArgumentCaptor<Gwin> captor = ArgumentCaptor.forClass(Gwin.class);
        for (Case c : cases) {
            when(amqpMessage.getStringProperty("amhs_bodypart_type")).thenReturn(c.bodyPartType());
            when(amqpMessage.getStringProperty("amhs_content_encoding")).thenReturn(c.encoding());
            when(textMessage.getText()).thenReturn(c.value());
            service.handleMessage(amqpMessage, "swim.test.queue");
        }
        verify(gwinRepository, times(cases.size())).save(captor.capture());

        List<Gwin> saved = captor.getAllValues();
        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            Gwin gwin = saved.get(i);
            String props = gwin.getAmqpProperties();
            assertEquals("text", gwin.getBodyType(), "case " + i + " should map to bodyType=text");
            assertEquals(c.value(), gwin.getPayloadContent(), "case " + i + " content must pass through unchanged");
            assertTrue(props.contains("amhs_bodypart_type") && props.contains(c.bodyPartType()),
                    "case " + i + " expected amhs_bodypart_type=" + c.bodyPartType() + " in " + props);
            assertTrue(props.contains("amhs_content_encoding") && props.contains(c.encoding()),
                    "case " + i + " expected amhs_content_encoding=" + c.encoding() + " in " + props);
        }
    }

    // ==================== NOTIFICATION REQUESTS ====================

    @Test
    void testNotificationRequests_RnAndNrn_ShouldBeForwarded() throws JMSException {
        // Forward notification_requests (RN/NRN) vào amqp_properties
        when(amqpMessage.getStringProperty("notification_requests")).thenReturn("RN,NRN");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin -> {
            String props = gwin.getAmqpProperties();
            return props.contains("notification_requests") && props.contains("RN") && props.contains("NRN");
        }));
    }

    @Test
    void testAmhsNotificationRequest_ExplicitValueFromSwim_ShouldBeNormalised() throws JMSException {
        // amss đọc amhs_notification_request để đặt phần tử notification-requests khi dựng IPM.
        // SWIM gửi tường minh thì tôn trọng, chỉ chuẩn hoá chữ thường và bỏ trùng.
        when(amqpMessage.getStringProperty("notification_requests")).thenReturn("RN, NRN, RN");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getAmqpProperties().contains("\"amhs_notification_request\":\"rn,nrn\"")));
    }

    @Test
    void testAmhsNotificationRequest_SsPriorityWithoutExplicitValue_ShouldDefaultToRnAndNrn()
            throws JMSException {
        // Bản tin priority "6" (= SS) tự gán notification-requests mang "RN,NRN" khi SWIM không gửi kèm
        when(amqpMessage.getStringProperty("notification_requests")).thenReturn(null);
        when(amqpMessage.getStringProperty("amhs_ats_pri")).thenReturn("SS");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getAmqpProperties().contains("\"amhs_notification_request\":\"rn,nrn\"")));
    }

    @Test
    void testAmhsNotificationRequest_NonSsPriority_ShouldNotBeSet() throws JMSException {
        // Không gán notification request cho bản tin không phải priority SS
        when(amqpMessage.getStringProperty("notification_requests")).thenReturn(null);
        when(amqpMessage.getStringProperty("amhs_ats_pri")).thenReturn("FF");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                !gwin.getAmqpProperties().contains("amhs_notification_request")));
    }

    // ==================== REGISTERED IDENTIFIER ====================

    @Test
    void testRegisteredIdentifier_NonDefaultOidWithoutUserVisibleString_ShouldReportControlPosition()
            throws JMSException {
        // registered-identifier khác OID mặc định thì user-visible-string bắt buộc phải có
        when(amqpMessage.getStringProperty("amhs_registered_identifier")).thenReturn("2.16.840.1.113694.2.2.9.9");
        when(amqpMessage.getStringProperty("amhs_user_visible_string")).thenReturn(null);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(alertService).create(
                eq(GwAlert.TYPE_VALIDATION_ERROR),
                eq(GwAlert.SEV_WARNING),
                contains("amhs_user_visible_string"),
                eq("gwin"),
                isNull());
        // vẫn chuyển tiếp, không reject
        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.PENDING.getValue())));
    }

    @Test
    void testRegisteredIdentifier_DefaultOid_ShouldNotReportControlPosition() throws JMSException {
        // OID mặc định thì không cần user-visible-string
        when(amqpMessage.getStringProperty("amhs_registered_identifier")).thenReturn("2.16.840.1.113694.2.2.1.1");
        when(amqpMessage.getStringProperty("amhs_user_visible_string")).thenReturn(null);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(alertService, never()).create(any(), any(), contains("amhs_user_visible_string"), any(), any());
        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.PENDING.getValue())));
    }

    // ==================== RECIPIENTS COUNT ====================

    private String buildRecipientList(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            if (i > 1) sb.append(",");
            sb.append(String.format("VV%06d", i));
        }
        return sb.toString();
    }

    @Test
    void testRecipientsCount_AtConfiguredMax_ShouldAccept() throws JMSException {
        // Đúng 512 recipient (== max cấu hình) -> accept
        when(configService.getMaxMsgRecipients()).thenReturn(512);
        when(amqpMessage.getStringProperty("amhs_recipients")).thenReturn(buildRecipientList(512));
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.PENDING.getValue())
        ));
    }

    @Test
    void testRecipientsCount_OverConfiguredMax_ShouldRejectMessage() throws JMSException {
        // 513 recipient (> max cấu hình 512) -> reject (FAILED) và báo Control Position
        when(configService.getMaxMsgRecipients()).thenReturn(512);
        when(amqpMessage.getStringProperty("amhs_recipients")).thenReturn(buildRecipientList(513));
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.FAILED.getValue())
        ));
        verify(alertService).create(
                eq(GwAlert.TYPE_VALIDATION_ERROR),
                eq(GwAlert.SEV_ERROR),
                contains("amhs_recipients"),
                eq("gwin"),
                isNull()
        );
    }

    // ==================== CONTENT-TYPE / PAYLOAD ====================

    @Test
    void testContentType_TextPlainWithEmptyPayload_ShouldReject() throws JMSException {
        // content-type=text/plain nhưng payload rỗng -> reject
        when(textMessage.getText()).thenReturn(null);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        // anyString() không khớp null -> stub lại validateSwimToAmhs cho trường hợp payload null
        when(validationService.validateSwimToAmhs(anyString(), any(), any(), anyInt()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.FAILED.getValue())
        ));
    }

    @Test
    void testContentType_UnsupportedType_ShouldReject() throws JMSException {
        // content-type không hỗ trợ -> reject
        when(amqpMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("application/xml");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.FAILED.getValue())
        ));
        verify(alertService).create(
                eq(GwAlert.TYPE_VALIDATION_ERROR),
                eq(GwAlert.SEV_ERROR),
                contains("application/xml"),
                eq("gwin"),
                isNull()
        );
    }

    @Test
    void testContentType_TextPlainWithPayloadArrivedAsData_ShouldAcceptAndDecodeAsText() throws JMSException {
        // CTSW101: KHÔNG reject khi content-type=text/plain nhưng payload đến qua data (BytesMessage)
        // - client AMQP thật gửi vậy, JMS message type không đáng tin làm proxy cho amqp-value/data;
        // content-type quyết định cách xử lý, đoán nhầm binary thì decode lại đúng thành text.
        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("test-ctsw101-text-via-data");
        when(bytesMessage.getJMSPriority()).thenReturn(4);
        when(bytesMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        byte[] hexLikeBytes = "A1B2C3D4E5F67890".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(bytesMessage.getBodyLength()).thenReturn((long) hexLikeBytes.length);
        when(bytesMessage.readBytes(any(byte[].class))).thenAnswer(inv -> {
            byte[] buf = inv.getArgument(0);
            System.arraycopy(hexLikeBytes, 0, buf, 0, hexLikeBytes.length);
            return hexLikeBytes.length;
        });
        when(bytesMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("text/plain; charset=\"utf-8\"");
        when(bytesMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(bytesMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin -> {
            assertEquals(InboundStatus.PENDING.getValue(), gwin.getStatus());
            assertEquals("A1B2C3D4E5F67890", gwin.getPayloadContent());
            return true;
        }));
    }

    @Test
    void testContentType_TextPlainDeclaredButBytesNotValidUtf8_ShouldReject() throws JMSException {
        // content-type=text/plain nhưng nội dung không phải UTF-8 hợp lệ -> reject
        jakarta.jms.TextMessage dataSectionMessage = mock(jakarta.jms.TextMessage.class);
        when(dataSectionMessage.getJMSMessageID()).thenReturn("test-mismatch-invalid-utf8");
        when(dataSectionMessage.getJMSPriority()).thenReturn(4);
        when(dataSectionMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        when(dataSectionMessage.getText())
                .thenThrow(new JMSException("Cannot decode String in UTF-8"));
        when(dataSectionMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE"))
                .thenReturn("text/plain; charset=\"utf-8\"");
        when(dataSectionMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        // anyString() không khớp null -> stub lại validateSwimToAmhs cho trường hợp payload null
        when(validationService.validateSwimToAmhs(anyString(), any(), any(), anyInt()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));

        // When: KHÔNG được ném exception ra ngoài (nếu thoát ra thì listener chỉ log, bản tin mất)
        service.handleMessage(dataSectionMessage, "swim.test.queue");

        // Then: lưu bản ghi FAILED để audit, chẩn đoán đúng là mismatch chứ không phải "thiếu data"
        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.FAILED.getValue())
                        && gwin.getRejectionDiagnostic().contains("not valid UTF-8")
                        && !gwin.getRejectionDiagnostic().contains("missing or empty")
        ));
        // Và phải báo lên Control Position
        verify(alertService).create(
                eq(GwAlert.TYPE_VALIDATION_ERROR),
                eq(GwAlert.SEV_ERROR),
                contains("validation failed"),
                eq("gwin"),
                isNull()
        );
    }

    @Test
    void testContentType_TextPlainWithLowControlBinary_ShouldReject() throws JMSException {
        // Payload chứa byte không hợp lệ UTF-8 -> strict decode bắt lỗi
        byte[] lowControlBinary = new byte[]{0x41, 0x42, (byte) 0xC3, 0x28, 0x43, 0x44, (byte) 0xE0, 0x28, 0x45};

        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("test-lowcontrol-binary");
        when(bytesMessage.getJMSPriority()).thenReturn(4);
        when(bytesMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        when(bytesMessage.getBodyLength()).thenReturn((long) lowControlBinary.length);
        when(bytesMessage.readBytes(any(byte[].class))).thenAnswer(inv -> {
            byte[] buf = inv.getArgument(0);
            System.arraycopy(lowControlBinary, 0, buf, 0, lowControlBinary.length);
            return lowControlBinary.length;
        });
        when(bytesMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE"))
                .thenReturn("text/plain; charset=\"utf-8\"");
        when(bytesMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        when(validationService.validateSwimToAmhs(anyString(), any(), any(), anyInt()))
                .thenReturn(new MessageValidationService.ValidationResult(true, List.of()));

        service.handleMessage(bytesMessage, "swim.test.queue");

        // Phải reject, và tuyệt đối không được lưu nội dung đã bị U+FFFD làm hỏng
        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.FAILED.getValue())
                        && gwin.getRejectionDiagnostic().contains("not valid UTF-8")
                        && (gwin.getPayloadContent() == null
                            || !gwin.getPayloadContent().contains("�"))
        ));
    }

    @Test
    void testContentType_TextPlainWithAmqpValueOnly_ShouldAccept() throws JMSException {
        // content-type=text/plain, payload qua TextMessage -> accept
        when(textMessage.getText()).thenReturn("METAR VVTS 251200Z 09008KT CAVOK 30/24 Q1010 NOSIG=");
        when(amqpMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE"))
                .thenReturn("text/plain; charset=\"utf-8\"");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.PENDING.getValue())
                        && gwin.getPayloadContent().startsWith("METAR VVTS")
                        && gwin.getRejectionReason() == null
        ));
        verify(alertService, never()).create(
                eq(GwAlert.TYPE_VALIDATION_ERROR), anyString(), anyString(), anyString(), any());
    }

    @Test
    void testContentType_OctetStreamWithRealBinaryData_ShouldAcceptAsFtbp() throws JMSException {
        // content-type=application/octet-stream, payload binary -> accept dưới dạng ftbp
        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("test-binary-ctsw110");
        when(bytesMessage.getJMSPriority()).thenReturn(4);
        when(bytesMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        byte[] fakeBinary = new byte[50];
        java.util.Arrays.fill(fakeBinary, (byte) 0x01);
        when(bytesMessage.getBodyLength()).thenReturn((long) fakeBinary.length);
        when(bytesMessage.readBytes(any(byte[].class))).thenAnswer(inv -> {
            byte[] buf = inv.getArgument(0);
            System.arraycopy(fakeBinary, 0, buf, 0, fakeBinary.length);
            return fakeBinary.length;
        });
        when(bytesMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("application/octet-stream");
        when(bytesMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(bytesMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin -> {
            assertEquals(InboundStatus.PENDING.getValue(), gwin.getStatus());
            assertEquals("ftbp", gwin.getBodyType());
            return true;
        }));
    }

    // ==================== ORIGINATOR ====================

    @Test
    void testOriginator_InvalidFormat_ShouldFallBackToDefaultAndReportControlPosition() throws JMSException {
        // amhs_originator sai định dạng -> fallback default originator và báo Control Position
        when(amqpMessage.getStringProperty("amhs_originator")).thenReturn("BADORIG");
        when(validationService.validateAftnAddress(eq("BADORIG"), anyString()))
                .thenReturn(new MessageValidationService.ValidationResult(false, List.of("bad format")));
        when(configService.getDefaultOriginator()).thenReturn("VVTSSWIM");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin -> "VVTSSWIM".equals(gwin.getOrigin())));
        verify(alertService).create(
                eq(GwAlert.TYPE_VALIDATION_ERROR),
                eq(GwAlert.SEV_WARNING),
                contains("BADORIG"),
                eq("gwin"),
                isNull()
        );
    }

    @Test
    void testOriginator_ValidEightLetterFormat_ShouldBeUsedAsIs() throws JMSException {
        // amhs_originator đúng định dạng 8 ký tự -> dùng trực tiếp
        when(amqpMessage.getStringProperty("amhs_originator")).thenReturn("UNKNOWNX");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin -> "UNKNOWNX".equals(gwin.getOrigin())));
    }

    private String extractOhi(String amqpPropertiesJson) throws Exception {
        return objectMapper.readTree(amqpPropertiesJson).get("amhs_ats_ohi").asText();
    }

    @Test
    void testOhiTrimming_Priority4_ThresholdIs53Chars() throws Exception {
        // Priority 4 (<6) -> ngưỡng cắt OHI = 53 ký tự
        when(amqpMessage.getJMSPriority()).thenReturn(4);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        String under53 = "A".repeat(52);
        String exactly53 = "B".repeat(53);
        String over53 = "C".repeat(60);

        ArgumentCaptor<Gwin> captor = ArgumentCaptor.forClass(Gwin.class);
        for (String ohi : List.of(under53, exactly53, over53)) {
            when(amqpMessage.getStringProperty("amhs_ats_ohi")).thenReturn(ohi);
            service.handleMessage(amqpMessage, "swim.test.queue");
        }
        verify(gwinRepository, times(3)).save(captor.capture());

        List<Gwin> saved = captor.getAllValues();
        assertEquals(under53, extractOhi(saved.get(0).getAmqpProperties()));
        assertEquals(exactly53, extractOhi(saved.get(1).getAmqpProperties()));
        String truncated = extractOhi(saved.get(2).getAmqpProperties());
        assertEquals(53, truncated.length());
        assertEquals(over53.substring(0, 53), truncated);
    }

    @Test
    void testOhiTrimming_Priority6_ThresholdIs48Chars() throws Exception {
        // Priority 6 (>=6) -> ngưỡng cắt OHI = 48 ký tự
        when(amqpMessage.getJMSPriority()).thenReturn(6);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        String under48 = "A".repeat(47);
        String exactly48 = "B".repeat(48);
        String over48 = "C".repeat(60);

        ArgumentCaptor<Gwin> captor = ArgumentCaptor.forClass(Gwin.class);
        for (String ohi : List.of(under48, exactly48, over48)) {
            when(amqpMessage.getStringProperty("amhs_ats_ohi")).thenReturn(ohi);
            service.handleMessage(amqpMessage, "swim.test.queue");
        }
        verify(gwinRepository, times(3)).save(captor.capture());

        List<Gwin> saved = captor.getAllValues();
        assertEquals(under48, extractOhi(saved.get(0).getAmqpProperties()));
        assertEquals(exactly48, extractOhi(saved.get(1).getAmqpProperties()));
        String truncated = extractOhi(saved.get(2).getAmqpProperties());
        assertEquals(48, truncated.length());
        assertEquals(over48.substring(0, 48), truncated);
    }

    @Test
    void testPriorityMapping_RawAmqpPrioritySweep_ShouldMapPerTable9() throws JMSException {
        // Map priority AMQP 0..9 sang ATS priority tương ứng
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        String[] expectedCodes = {"KK", "KK", "KK", "GG", "FF", "DD", "SS", "SS", "SS", "SS"};

        ArgumentCaptor<Gwin> captor = ArgumentCaptor.forClass(Gwin.class);
        for (int p = 0; p <= 9; p++) {
            when(amqpMessage.getJMSPriority()).thenReturn(p);
            service.handleMessage(amqpMessage, "swim.test.queue");
        }
        verify(gwinRepository, times(10)).save(captor.capture());

        List<Gwin> saved = captor.getAllValues();
        for (int p = 0; p <= 9; p++) {
            Gwin gwin = saved.get(p);
            assertEquals(p, gwin.getPriority().intValue(), "priority echo mismatch at p=" + p);
            String props = gwin.getAmqpProperties();
            assertTrue(props.contains("ats_priority") && props.contains(expectedCodes[p]),
                    "expected ats_priority=" + expectedCodes[p] + " at raw priority=" + p + " but got " + props);
        }
    }

    @Test
    void testPriorityMapping_AmhsAtsPriProperty_AlwaysOverridesRawPriority() throws JMSException {
        // amhs_ats_pri luôn được ưu tiên hơn priority AMQP thô
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        record Case(int rawPriority, String atsCode, int expectedAmqpPriority) {}
        List<Case> cases = List.of(
                new Case(4, "SS", 6), new Case(4, "DD", 5), new Case(4, "FF", 4), new Case(4, "GG", 3), new Case(4, "KK", 2),
                new Case(1, "SS", 6), new Case(1, "DD", 5), new Case(1, "FF", 4), new Case(1, "GG", 3),
                new Case(9, "KK", 2)
        );

        ArgumentCaptor<Gwin> captor = ArgumentCaptor.forClass(Gwin.class);
        for (Case c : cases) {
            when(amqpMessage.getJMSPriority()).thenReturn(c.rawPriority());
            when(amqpMessage.getStringProperty("amhs_ats_pri")).thenReturn(c.atsCode());
            service.handleMessage(amqpMessage, "swim.test.queue");
        }
        verify(gwinRepository, times(cases.size())).save(captor.capture());

        List<Gwin> saved = captor.getAllValues();
        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            Gwin gwin = saved.get(i);
            assertEquals(c.expectedAmqpPriority(), gwin.getPriority().intValue(),
                    "amhs_ats_pri=" + c.atsCode() + " with raw priority=" + c.rawPriority()
                            + " should map to AMQP priority " + c.expectedAmqpPriority());
            String props = gwin.getAmqpProperties();
            assertTrue(props.contains("ats_priority") && props.contains(c.atsCode()),
                    "expected ats_priority=" + c.atsCode() + " in props but got " + props);
        }
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

    @Test
    void testCreationTime_Missing_ShouldRejectMessage() throws JMSException {
        // Thiếu creation time và timestamp <= 0 -> reject
        when(amqpMessage.getJMSTimestamp()).thenReturn(0L);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: bị từ chối (status FAILED), báo cáo Control Position
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getStatus().equals(InboundStatus.FAILED.getValue())
        ));
        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR),
            eq(GwAlert.SEV_ERROR),
            contains("creation-time"),
            eq("gwin"),
            isNull()
        );
    }

    @Test
    void testPriority_OutOfRange10_ShouldRejectMessage() throws JMSException {
        // Priority ngoài khoảng 0-9 -> reject và báo Control Position
        when(amqpMessage.getJMSPriority()).thenReturn(10);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.FAILED.getValue())));
        verify(alertService).create(
                eq(GwAlert.TYPE_VALIDATION_ERROR),
                eq(GwAlert.SEV_ERROR),
                contains("priority"),
                eq("gwin"),
                isNull());
    }

    @Test
    void testContentType_Empty_ShouldRejectMessage() throws JMSException {
        // Thiếu content-type -> reject
        when(amqpMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn(null);
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.FAILED.getValue())));
        verify(alertService).create(
                eq(GwAlert.TYPE_VALIDATION_ERROR),
                eq(GwAlert.SEV_ERROR),
                contains("content-type"),
                eq("gwin"),
                isNull());
    }

    @Test
    void testRecipients_AddressLongerThanEightLetters_ShouldNotBeConveyed() throws JMSException {
        // Địa chỉ recipient quá 8 ký tự bị loại, không còn recipient hợp lệ -> reject
        when(amqpMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZXX");
        when(validationService.validateAftnAddress(eq("VVHHZTZXX"), anyString()))
                .thenReturn(new MessageValidationService.ValidationResult(false, List.of("must be exactly 8 characters")));
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.FAILED.getValue())));
    }

    @Test
    void testFilingTime_BlankAmhsAtsFt_ShouldFallBackToAmqpCreationTime() throws JMSException {
        // CTSW105 - Điện văn 1: amhs_ats_ft để trống -> dùng creation-time AMQP
        // (JMSTimestamp, không phải property "creation-time") chuyển sang DDhhmm
        when(amqpMessage.getStringProperty("amhs_ats_ft")).thenReturn("");
        when(amqpMessage.getJMSTimestamp()).thenReturn(1787285680974L); // 2026-08-21 04:14:40 UTC -> 210414
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then
        verify(gwinRepository).save(argThat(gwin -> {
            String props = gwin.getAmqpProperties();
            return props.contains("amhs_ats_ft") && props.contains("210414");
        }));
    }

    @Test
    void testFilingTime_WrongFormatAmhsAtsFt_ShouldFallBackToAmqpCreationTime() throws JMSException {
        // amhs_ats_ft sai định dạng -> fallback sang creation-time của AMQP
        when(amqpMessage.getStringProperty("amhs_ats_ft")).thenReturn("ABCDEF");
        when(amqpMessage.getJMSTimestamp()).thenReturn(1787285680974L); // -> 210414
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then
        verify(gwinRepository).save(argThat(gwin -> {
            String props = gwin.getAmqpProperties();
            assertTrue(props.contains("210414"), "phải dùng creation-time AMQP");
            assertFalse(props.contains("ABCDEF"), "không được lưu chuỗi sai định dạng làm filing time");
            return true;
        }));
    }

    // ==================== SUCCESSFUL PROCESSING ====================

    @Test
    void testSuccessfulProcessing_WithResolvedAddressing() throws JMSException {
        // Given: All valid, amhs_originator/amhs_recipients present (default setUp() stubs)
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: Should save with PENDING status
        verify(gwinRepository).save(argThat(gwin -> {
            assertEquals(InboundStatus.PENDING.getValue(), gwin.getStatus());
            assertEquals("VVHHZPZX", gwin.getOrigin());
            assertEquals("VVHHZTZX VVTSZDYX", gwin.getAddress());
            assertEquals(ResolvedAddressing.SOURCE_AMQP_PROPERTY, gwin.getAddressingSource());
            assertTrue(gwin.getPayloadContent().contains("\"messageType\": \"METAR\""));
            assertTrue(gwin.getPayloadContent().contains("\"stationIcao\": \"VVTS\""));
            return true;
        }));
    }

    @Test
    void testAmhsUnaware_OctetStreamContentType_ShouldMapToFtbpBodyType() throws JMSException {
        // Không có amhs_bodypart_type, content-type=octet-stream -> map sang ftbp
        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("test-amhs-unaware-octet");
        when(bytesMessage.getJMSPriority()).thenReturn(2);
        when(bytesMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        byte[] fakeBinary = new byte[20];
        java.util.Arrays.fill(fakeBinary, (byte) 0x02);
        when(bytesMessage.getBodyLength()).thenReturn((long) fakeBinary.length);
        when(bytesMessage.readBytes(any(byte[].class))).thenAnswer(inv -> {
            byte[] buf = inv.getArgument(0);
            System.arraycopy(fakeBinary, 0, buf, 0, fakeBinary.length);
            return fakeBinary.length;
        });
        when(bytesMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("application/octet-stream");
        when(bytesMessage.getStringProperty("amhs_recipients")).thenReturn("VVHHZTZX");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(bytesMessage, "swim.test.queue");

        // Suy luận bodyType từ content-type khi thiếu amhs_bodypart_type
        verify(gwinRepository).save(argThat(gwin -> {
            assertEquals(InboundStatus.PENDING.getValue(), gwin.getStatus());
            assertEquals("ftbp", gwin.getBodyType());
            return true;
        }));
    }

    @Test
    void testPartiallyInvalidRecipients_ShouldDropInvalidOnesNotRejectWhole() throws JMSException {
        // amhs_recipients có 1 địa chỉ hợp lệ và 1 địa chỉ sai định dạng
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
        // Given: TẤT CẢ recipient đều sai định dạng -> reject (FAILED)
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        when(amqpMessage.getStringProperty("amhs_recipients")).thenReturn("BADADDR1,BADADDR2");
        when(validationService.validateAftnAddress(anyString(), anyString()))
            .thenReturn(new MessageValidationService.ValidationResult(false, List.of("bad format")));

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: FAILED
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getStatus().equals(InboundStatus.FAILED.getValue()) &&
            "validation-failed".equals(gwin.getRejectionReason()) &&
            gwin.getRejectionDiagnostic().contains("amhs_recipients")
        ));
    }

    @Test
    void testMissingAmhsRecipientsProperty_ShouldRejectMessage() throws JMSException {
        // Thiếu amhs_recipients -> reject và báo Control Position
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);
        when(amqpMessage.getStringProperty("amhs_recipients")).thenReturn(null);

        // When
        service.handleMessage(amqpMessage, "swim.test.queue");

        // Then: FAILED
        verify(gwinRepository).save(argThat(gwin ->
            gwin.getStatus().equals(InboundStatus.FAILED.getValue()) &&
            "validation-failed".equals(gwin.getRejectionReason()) &&
            gwin.getRejectionDiagnostic().contains("amhs_recipients")
        ));
        verify(alertService).create(
                eq(GwAlert.TYPE_VALIDATION_ERROR),
                eq(GwAlert.SEV_ERROR),
                contains("amhs_recipients"),
                eq("gwin"),
                isNull()
        );
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

        // Content must pass through unconverted
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