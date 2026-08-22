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
        when(amqpMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        when(amqpMessage.getStringProperty("JMS_AMQP_CONTENT_TYPE")).thenReturn("text/plain; charset=\"utf-8\"");
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn("METAR");
        when(amqpMessage.getStringProperty("amhs_gateway_id")).thenReturn(null); // Default: no loopback

        when(configService.getGatewayId()).thenReturn("ASG-GW-01");
        when(configService.isStrictComplianceMode()).thenReturn(false);

        MessageValidationService.ValidationResult validResult =
            new MessageValidationService.ValidationResult(true, List.of());
        when(validationService.validateSwimToAmhs(anyString(), any(), anyString(), anyInt()))
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

    @Test
    void testSubjectMapping_AmhsSubjectPresent_ShouldOverrideSubjectProperty() throws JMSException {
        // CTSW107 Case 4: cả amhs_subject và subject đều có -> amhs_subject thắng
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
        // CTSW107 Case 1&2: amhs_subject rỗng -> dùng subject (AMQP Properties section)
        when(amqpMessage.getStringProperty("amhs_subject")).thenReturn("");
        when(amqpMessage.getStringProperty("subject")).thenReturn("SWIM_INTERWORKING");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                "SWIM_INTERWORKING".equals(gwin.getSubject())
        ));
    }

    @Test
    void testSubjectMapping_SubjectPropertyBlank_ShouldUseAmhsSubject() throws JMSException {
        // CTSW107 Case 3: subject (Properties) rỗng, amhs_subject có giá trị -> dùng amhs_subject
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
        // CTSW107 Case 1: subject > 128 ký tự -> cắt còn đúng 128 ký tự đầu
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

    // ==================== FTBP BINARY / GZIP (CTSW116) ====================

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
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(bytesMessage, "swim.test.queue");

        String expectedBase64 = java.util.Base64.getEncoder().encodeToString(rawBinary);
        verify(gwinRepository).save(argThat(gwin ->
                expectedBase64.equals(gwin.getPayloadContent())
        ));
    }

    @Test
    void testFtbpAttributes_ShouldBeForwardedVerbatim() throws JMSException {
        // CTSW116: amhs_ftbp_file_name/object_size/last_mod chỉ được forward nguyên văn - việc map
        // sang incomplete-pathname/actual-values/date-and-time-of-last-modification trong IPM file
        // transfer parameters (§4.5.2.6-8) là dựng object IPM thô, ngoài phạm vi gateway-swim.
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
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(bytesMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin -> {
            String props = gwin.getAmqpProperties();
            return props.contains("flight_plan_data.bin")
                    && props.contains("1024")
                    && props.contains("20260723142742Z");
        }));
    }

    @Test
    void testGzipCompressedFtbp_ShouldDecompressAndBase64EncodeNotCorrupted() throws Exception {
        // CTSW116 Case 2: data nén gzip -> giải nén, rồi base64-encode dữ liệu GỐC (không phải
        // chuỗi text bị hỏng do decode UTF-8 nhầm sau khi giải nén).
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
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(bytesMessage, "swim.test.queue");

        String expectedBase64 = java.util.Base64.getEncoder().encodeToString(originalBinary);
        verify(gwinRepository).save(argThat(gwin ->
                expectedBase64.equals(gwin.getPayloadContent())
        ));
    }

    // ==================== BODYPART TYPE / CONTENT ENCODING (CTSW115) ====================

    @Test
    void testBodyPartTypeAndEncoding_AllFourValidCombinations_ShouldBeForwardedVerbatim() throws JMSException {
        // CTSW115: gateway-swim chỉ forward nguyên văn amhs_bodypart_type/amhs_content_encoding/
        // amqp-value - việc map sang đúng AMHS Body Part object + repertoire + khởi tạo
        // original-encoded-information-types trong envelope (§4.5.4.7, Table 10) là dựng IPM/
        // envelope thô, ngoài phạm vi gateway-swim (đã xác nhận từ CTSW101).
        // Lưu ý: test_case.md Case 2 dùng "ia5_text_body_part" (underscore) - đây là chính tả CŨ
        // mà bản thân EUR Doc 047 v3.0 (trang errata, dòng 49-53) đã ghi rõ là lỗi và SỬA thành
        // dấu gạch ngang "ia5-text-body-part". Dùng đúng chính tả v3.0 hiện hành ở đây.
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

    // ==================== NOTIFICATION REQUESTS (CTSW113) ====================

    @Test
    void testNotificationRequests_RnAndNrn_ShouldBeForwarded() throws JMSException {
        // CTSW113: gateway-swim chỉ trích xuất & forward notification_requests (RN/NRN) vào
        // gwin.amqp_properties - việc xử lý IPN/RN/NRN nhận NGƯỢC LẠI từ AMHS (§4.4.7) và việc
        // gate rn/nrn theo priority=SS lúc dựng IPM (§4.5.3.4) nằm ngoài phạm vi gateway-swim
        // (không có bảng/kênh nào cho AMHS-originated control traffic quay lại - đã xác nhận
        // qua audit trước, xem [[project_eurdoc047_systematic_audit]]).
        when(amqpMessage.getStringProperty("notification_requests")).thenReturn("RN,NRN");
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin -> {
            String props = gwin.getAmqpProperties();
            return props.contains("notification_requests") && props.contains("RN") && props.contains("NRN");
        }));
    }

    // ==================== RECIPIENTS COUNT (CTSW112) ====================

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
        // CTSW112 Case A: đúng 512 recipient (== max cấu hình) -> accept
        when(configService.getMaxMsgRecipients()).thenReturn(512);
        when(amqpMessage.getStringProperty("amhs_recipients")).thenReturn(buildRecipientList(512));
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(amqpMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.PENDING.getValue())
        ));
    }

    @Test
    void testRecipientsCount_OverConfiguredMax_ShouldRejectAndReportControlPosition() throws JMSException {
        // CTSW112 Case B: 513 recipient (> max cấu hình 512) -> reject + báo Control Position
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

    // ==================== CONTENT-TYPE / PAYLOAD (CTSW110) ====================

    @Test
    void testContentType_TextPlainWithEmptyPayload_ShouldReject() throws JMSException {
        // CTSW110 Case 1: content-type=text/plain hợp lệ nhưng amqp-value/data đều rỗng -> reject
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
        // CTSW110 Case 5: content-type = application/xml (không hỗ trợ) -> reject
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
    void testContentType_TextPlainDeclaredButPayloadArrivedAsData_ShouldReject() throws JMSException {
        // CTSW110 Case 2: content-type=text/plain nhưng payload thực tế đến qua data (BytesMessage) -> reject
        jakarta.jms.BytesMessage bytesMessage = mock(jakarta.jms.BytesMessage.class);
        when(bytesMessage.getJMSMessageID()).thenReturn("test-ctsw110-case2");
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
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(bytesMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin ->
                gwin.getStatus().equals(InboundStatus.FAILED.getValue())
        ));
    }

    @Test
    void testContentType_OctetStreamWithRealBinaryData_ShouldAcceptAsFtbp() throws JMSException {
        // CTSW110 Case 3: content-type=application/octet-stream, payload thật sự binary (BytesMessage) -> accept
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
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        service.handleMessage(bytesMessage, "swim.test.queue");

        verify(gwinRepository).save(argThat(gwin -> {
            assertEquals(InboundStatus.PENDING.getValue(), gwin.getStatus());
            assertEquals("ftbp", gwin.getBodyType());
            return true;
        }));
    }

    // ==================== ORIGINATOR (CTSW108/CTSW109) ====================

    @Test
    void testOriginator_InvalidFormat_ShouldFallBackToDefaultAndReportControlPosition() throws JMSException {
        // CTSW109: amhs_originator không đúng format AFTN 8 ký tự -> dùng default originator,
        // ghi log VÀ báo cáo Control Position (EUR Doc 047 §4.5.2.12(b))
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
        // CTSW108: amhs_originator đúng format 8 ký tự -> dùng trực tiếp, không fallback.
        // EUR Doc 047 §4.5.2.12 chỉ đòi hỏi đúng ĐỊNH DẠNG 8 ký tự, không đòi hỏi phải khớp
        // 1 danh sách "known address" nào — kể cả literal "UNKNOWNX" (8 chữ hợp lệ) cũng phải
        // được dùng nguyên, không fallback.
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
        // CTSW106 - Điện văn 1-3: priority=4 (<6) -> ngưỡng cắt OHI = 53 ký tự
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
        // CTSW106 - Điện văn 4-6: priority=6 (>=6) -> ngưỡng cắt OHI = 48 ký tự
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
        // CTSW104: 10 bản tin, priority AMQP 0..9, không có amhs_ats_pri -> map theo Table 9
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
        // CTSW104: amhs_ats_pri luôn được ưu tiên hơn priority AMQP thô, bất kể priority thô là gì
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
        // Given (CTSW102): không có amhs_ats_ft/creation_time/creation-time property
        // và JMSTimestamp cũng <= 0 -> creation-time coi như thiếu, phải bị từ chối
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
        // Given: bản tin SWIM "AMHS-unaware" - không có amhs_bodypart_type, chỉ có content-type.
        // content-type=octet-stream bắt buộc phải đến qua data/BytesMessage (§4.5.1.6.a), không
        // thể là TextMessage - dùng BytesMessage thật cho hợp lệ.
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
        when(gwinRepository.existsByMessageId(anyString())).thenReturn(false);

        // When
        service.handleMessage(bytesMessage, "swim.test.queue");

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