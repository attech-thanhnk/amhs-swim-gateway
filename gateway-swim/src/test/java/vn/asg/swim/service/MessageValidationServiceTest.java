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
        when(configService.getMaxMsgDataSize()).thenReturn(0);
        String payload = "A".repeat(1_000_000);

        var result = service.validateSwimToAmhs("msg-1", amqpMessage, payload, 1_000_000);

        assertTrue(result.isValid());
    }

    @Test
    void testAmhsToSwim_SizeOverLimit_ShouldReportContentTooLong() {
        when(configService.getConversionDir()).thenReturn("BOTH");
        when(configService.getMaxMsgDataSize()).thenReturn(100);
        when(configService.getMaxMsgRecipients()).thenReturn(512);

        var result = service.validateAmhsToSwim("A".repeat(101), "VVHHZTZX", null);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("content-too-long"));
    }

    @Test
    void testAmhsToSwim_RecipientsCommaSeparated_ShouldBeCountedIndividually() {
        when(configService.getConversionDir()).thenReturn("BOTH");
        when(configService.getMaxMsgDataSize()).thenReturn(0);
        when(configService.getMaxMsgRecipients()).thenReturn(512);

        StringBuilder tooMany = new StringBuilder("VVHHZTZX");
        for (int i = 1; i < 513; i++) {
            tooMany.append(",VVHHZTZX");
        }

        var result = service.validateAmhsToSwim("METAR VVTS", tooMany.toString(), null);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("too-many-recipients"));
        assertTrue(result.getErrorMessage().contains("513"));
    }

    @Test
    void testAmhsToSwim_RecipientsAtConfiguredMax_ShouldPass() {
        when(configService.getConversionDir()).thenReturn("BOTH");
        when(configService.getMaxMsgDataSize()).thenReturn(0);
        when(configService.getMaxMsgRecipients()).thenReturn(512);

        StringBuilder atMax = new StringBuilder("VVHHZTZX");
        for (int i = 1; i < 512; i++) {
            atMax.append(",VVHHZTZX");
        }

        var result = service.validateAmhsToSwim("METAR VVTS", atMax.toString(), null);

        assertTrue(result.isValid());
    }

    @Test
    void testAtsHeader_ValidPriorityAndFilingTime_ShouldPass() {
        assertTrue(service.validateAtsMessageHeader("FF", "121200").isValid());
    }

    @Test
    void testAtsHeader_EmptyPriority_ShouldFail() {
        var result = service.validateAtsMessageHeader("", "121200");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("ATS-message-priority is empty"));
    }

    @Test
    void testAtsHeader_InvalidPriority_ShouldFail() {
        var result = service.validateAtsMessageHeader("XX", "121200");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("ATS-message-priority 'XX' is invalid"));
    }

    @Test
    void testAtsHeader_EmptyFilingTime_ShouldFail() {
        var result = service.validateAtsMessageHeader("FF", null);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("ATS-message-filing-time is empty"));
    }

    @Test
    void testAtsHeader_InvalidFilingTime_ShouldFail() {
        var result = service.validateAtsMessageHeader("FF", "12:00");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("ATS-message-filing-time '12:00' is invalid"));
    }

    @Test
    void testAtsHeader_CompletelyEmptyHeader_ShouldReportBothFields() {
        var result = service.validateAtsMessageHeader(null, null);

        assertFalse(result.isValid());
        assertEquals(2, result.getErrors().size());
    }

    @Test
    void testAtsHeader_LowercasePriority_ShouldBeAccepted() {
        assertTrue(service.validateAtsMessageHeader("ss", "121200").isValid());
    }

    @Test
    void testAmhsToSwim_ExplicitByteSize_ShouldOverrideStringLength() {
        when(configService.getConversionDir()).thenReturn("BOTH");
        when(configService.getMaxMsgDataSize()).thenReturn(100);
        when(configService.getMaxMsgRecipients()).thenReturn(0);

        String base64Payload = "A".repeat(120);
        var result = service.validateAmhsToSwim(base64Payload, "VVHHZTZX", 90);

        assertTrue(result.isValid(), "Kích thước thật 90 < 100 nên không được từ chối");
    }

    @Test
    void testAmhsToSwim_ExplicitByteSize_ExceedingMax_ShouldFail() {
        when(configService.getConversionDir()).thenReturn("BOTH");
        when(configService.getMaxMsgDataSize()).thenReturn(100);
        when(configService.getMaxMsgRecipients()).thenReturn(0);

        var result = service.validateAmhsToSwim("A".repeat(10), "VVHHZTZX", 150);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("content-too-long"));
    }

    @Test
    void testAmhsToSwim_NullByteSize_ShouldFallBackToStringLength() {
        when(configService.getConversionDir()).thenReturn("BOTH");
        when(configService.getMaxMsgDataSize()).thenReturn(100);
        when(configService.getMaxMsgRecipients()).thenReturn(0);

        var result = service.validateAmhsToSwim("A".repeat(150), "VVHHZTZX", null);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("content-too-long"));
    }

    @Test
    void testRepertoire_Ia5TextWithIta2_ShouldBeRejected() {
        var result = service.validateRepertoire("ia5-text-body-part", "ITA2");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("unsupported-body-part-type"));
    }

    @Test
    void testRepertoire_Ia5TextDefaultOrIa5_ShouldBeAccepted() {
        assertTrue(service.validateRepertoire("ia5-text-body-part", null).isValid());
        assertTrue(service.validateRepertoire("ia5-text-body-part", "ISO-646").isValid());
    }

    @Test
    void testRepertoire_GeneralTextIso646_ShouldAlwaysBeAccepted() {
        var result = service.validateRepertoire("general-text-body-part", "ISO-646");

        assertTrue(result.isValid());
    }

    @Test
    void testRepertoire_GeneralTextNonIso646_PolicyAllows_ShouldBeAccepted() {
        when(configService.isNonIso646RepertoireAllowed()).thenReturn(true);

        assertTrue(service.validateRepertoire("general-text-body-part", "ISO-8859-1").isValid());
        assertTrue(service.validateRepertoire("general-text-body-part", "ISO-REG-144").isValid());
    }

    @Test
    void testRepertoire_GeneralTextNonIso646_PolicyRejects_ShouldBeRejected() {
        when(configService.isNonIso646RepertoireAllowed()).thenReturn(false);

        var result = service.validateRepertoire("general-text-body-part", "ISO-REG-144");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("unsupported-encoded-information-types"));
    }

    @Test
    void testRepertoire_FileTransferBodyPart_ShouldBeIgnored() {
        assertTrue(service.validateRepertoire("file-transfer-body-part", "ISO-REG-144").isValid());
    }

    @Test
    void testAftnAddress_EightUppercaseLetters_ShouldPass() {
        assertTrue(service.validateAftnAddress("VVTSZTZX", "Recipient").isValid());
        assertTrue(MessageValidationService.isValidAftnAddress("VVTSZTZX"));
    }

    @Test
    void testAftnAddress_WithDigits_ShouldBeRejected() {
        var result = service.validateAftnAddress("VVTS1234", "Recipient");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("uppercase letters"));
        assertFalse(MessageValidationService.isValidAftnAddress("VVTS1234"));
    }

    @Test
    void testAftnAddress_WrongLength_ShouldBeRejected() {
        assertFalse(service.validateAftnAddress("VVTSZTZ", "Recipient").isValid());
        assertFalse(service.validateAftnAddress("VVTSZTZXX", "Recipient").isValid());
        assertFalse(MessageValidationService.isValidAftnAddress(null));
    }

    @Test
    void testAtsHeader_FilingTimeLongerThanSixDigits_ShouldFail() {
        var result = service.validateAtsMessageHeader("FF", "0704301234");

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("ATS-message-filing-time"));
    }
}
