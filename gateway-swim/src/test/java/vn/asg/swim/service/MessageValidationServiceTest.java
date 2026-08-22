package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test suite for MessageValidationService - kiểm tra trực tiếp logic thật
 * (không mock qua AMQPSubscriberServiceTest), đặc biệt phần size-limit CTSW111.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageValidationServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private Message amqpMessage;

    @InjectMocks
    private MessageValidationService service;

    @BeforeEach
    void setUp() throws JMSException {
        when(configService.getConversionDir()).thenReturn("SWIM_TO_AMHS");
        when(amqpMessage.getJMSPriority()).thenReturn(4);
        when(amqpMessage.getJMSTimestamp()).thenReturn(System.currentTimeMillis());
        when(amqpMessage.getStringProperty(anyString())).thenReturn(null);
    }

    @Test
    void testPayloadSize_UnderLimit_ShouldPass() {
        // CTSW111 Case A: text payload <= max size -> accept
        when(configService.getMaxMsgDataSize()).thenReturn(100);
        String payload = "A".repeat(50);

        var result = service.validateSwimToAmhs("msg-1", amqpMessage, payload, 50);

        assertTrue(result.isValid());
    }

    @Test
    void testPayloadSize_OverLimit_ShouldReject() {
        // CTSW111 Case C: text payload > max size -> reject
        when(configService.getMaxMsgDataSize()).thenReturn(100);
        String payload = "A".repeat(150);

        var result = service.validateSwimToAmhs("msg-1", amqpMessage, payload, 150);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("150"));
    }

    @Test
    void testPayloadSize_BinaryRawSizeUnderLimit_Base64StringOverLimit_ShouldStillPass() {
        // CTSW111 Case B: kích thước binary GỐC (data) <= max, dù chuỗi base64 sau khi
        // encode để lưu payload dài hơn ~33% và có thể vượt max nếu đo nhầm chuỗi base64
        // thay vì đo bytes gốc. payloadByteSize truyền vào phải là kích thước binary gốc.
        when(configService.getMaxMsgDataSize()).thenReturn(100);
        byte[] rawBinary = new byte[90]; // <= 100 -> phải PASS
        String base64Payload = Base64.getEncoder().encodeToString(rawBinary); // ~120 chars, > 100

        var result = service.validateSwimToAmhs("msg-1", amqpMessage, base64Payload, rawBinary.length);

        assertTrue(result.isValid(),
                "Binary gốc 90 bytes <= max 100 phải PASS dù chuỗi base64 (" + base64Payload.length()
                        + " ký tự) trông có vẻ vượt ngưỡng - phải đo bằng payloadByteSize, không phải độ dài chuỗi");
    }

    @Test
    void testPayloadSize_BinaryRawSizeOverLimit_ShouldReject() {
        // CTSW111 Case D: kích thước binary gốc (data) > max -> reject
        when(configService.getMaxMsgDataSize()).thenReturn(100);
        byte[] rawBinary = new byte[200];
        String base64Payload = Base64.getEncoder().encodeToString(rawBinary);

        var result = service.validateSwimToAmhs("msg-1", amqpMessage, base64Payload, rawBinary.length);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("200"));
    }

    @Test
    void testPayloadSize_MaxSizeZero_ShouldMeanUnlimited() {
        // EUR Doc 047 §3.3.1.4: 0 hoặc không cấu hình = không giới hạn
        when(configService.getMaxMsgDataSize()).thenReturn(0);
        String payload = "A".repeat(1_000_000);

        var result = service.validateSwimToAmhs("msg-1", amqpMessage, payload, 1_000_000);

        assertTrue(result.isValid());
    }
}
