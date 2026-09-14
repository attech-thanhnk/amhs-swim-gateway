package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Dịch vụ kiểm thử tính hợp lệ của bản tin (Message Validation Service).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageValidationService {

    private final ConfigService configService;

    /** Các giá trị ATS-message-priority hợp lệ. */
    private static final List<String> ATS_PRIORITIES = List.of("SS", "DD", "FF", "GG", "KK");

    /** Repertoire ITA2 của X.400. */
    public static final String REPERTOIRE_ITA2 = "ITA2";

    /** Repertoire Basic ISO 646. */
    public static final String REPERTOIRE_ISO646 = "ISO-646";

    /**
     * Đối tượng chứa kết quả kiểm thử (Validation Result Container)
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }

        public static ValidationResult success() {
            return new ValidationResult(true, new ArrayList<>());
        }

        public static ValidationResult failure(String error) {
            List<String> errors = new ArrayList<>();
            errors.add(error);
            return new ValidationResult(false, errors);
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }

    /**
     * Kiểm thử bản tin chiều SWIM → AMHS.
     */
    public ValidationResult validateSwimToAmhs(String messageId, Message msg, String payload, int payloadByteSize) {
        List<String> errors = new ArrayList<>();

        String direction = configService.getConversionDir();
        if ("AMHS_TO_SWIM".equals(direction)) {
            errors.add("Conversion direction is AMHS_TO_SWIM - SWIM→AMHS messages not allowed");
            return ValidationResult.failure(errors);
        }

        try {
            if (messageId == null || messageId.isBlank()) {
                errors.add("Mandatory field 'message-id' (JMSMessageID) is missing");
            }

            try {
                msg.getJMSPriority();
            } catch (Exception e) {
                log.warn("Message {}: Mandatory field 'priority' (JMSPriority) is missing", messageId);
            }

            long timestamp = msg.getJMSTimestamp();
            if (timestamp <= 0) {
                log.warn("Message {}: Mandatory field 'creation-time' (JMSTimestamp) is missing", messageId);
            }

            if (payload == null || payload.isBlank()) {
                errors.add("Mandatory field 'data/amqp-value' (message body) is missing");
            }

            String bodyPartType = msg.getStringProperty("amhs_bodypart_type");
            if ("file-transfer-body-part".equalsIgnoreCase(bodyPartType)) {
                String ftbpFileName = msg.getStringProperty("amhs_ftbp_file_name");
                if (ftbpFileName == null || ftbpFileName.isBlank()) {
                    errors.add("Mandatory FTBP attribute 'amhs_ftbp_file_name' is missing");
                }
            }

        } catch (JMSException e) {
            errors.add("Failed to read AMQP message properties: " + e.getMessage());
        }

        int maxSize = configService.getMaxMsgDataSize();
        if (maxSize > 0 && payloadByteSize > maxSize) {
            errors.add(String.format("Message size %d bytes exceeds maximum %d bytes", payloadByteSize, maxSize));
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
    }

    /**
     * Kiểm thử bản tin chiều AMHS → SWIM.
     */
    public ValidationResult validateAmhsToSwim(String payload, String recipients, Integer payloadByteSize) {
        List<String> errors = new ArrayList<>();

        String direction = configService.getConversionDir();
        if ("SWIM_TO_AMHS".equals(direction)) {
            errors.add("Conversion direction is SWIM_TO_AMHS - AMHS→SWIM messages not allowed");
            return ValidationResult.failure(errors);
        }

        if (payload != null) {
            int maxSize = configService.getMaxMsgDataSize();
            int actualSize = payloadByteSize != null ? payloadByteSize
                    : payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (maxSize > 0 && actualSize > maxSize) {
                errors.add(String.format("Message size %d bytes exceeds maximum %d bytes (content-too-long)",
                        actualSize, maxSize));
            }
        }

        if (recipients != null && !recipients.isBlank()) {
            String[] recipientArray = recipients.trim().split("[,\\s]+");
            int maxRecipients = configService.getMaxMsgRecipients();
            if (maxRecipients > 0 && recipientArray.length > maxRecipients) {
                errors.add(String.format("Recipients count %d exceeds maximum %d (too-many-recipients)",
                        recipientArray.length, maxRecipients));
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
    }

    /**
     * Kiểm thử cú pháp ATS-message-header của IPM đến.
     */
    public ValidationResult validateAtsMessageHeader(String atsPriority, String atsFilingTime) {
        List<String> errors = new ArrayList<>();

        if (atsPriority == null || atsPriority.isBlank()) {
            errors.add("ATS-message-priority is empty");
        } else if (!ATS_PRIORITIES.contains(atsPriority.trim().toUpperCase())) {
            errors.add(String.format("ATS-message-priority '%s' is invalid", atsPriority.trim()));
        }

        if (atsFilingTime == null || atsFilingTime.isBlank()) {
            errors.add("ATS-message-filing-time is empty");
        } else if (!atsFilingTime.trim().matches("^\\d{6}$")) {
            errors.add(String.format("ATS-message-filing-time '%s' is invalid", atsFilingTime.trim()));
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
    }

    public static final String AFTN_ADDRESS_PATTERN = "[A-Z]{8}";

    public static boolean isValidAftnAddress(String aftn) {
        return aftn != null && aftn.trim().matches(AFTN_ADDRESS_PATTERN);
    }

    /**
     * Kiểm thử định dạng địa chỉ AFTN (phải có đúng 8 chữ cái viết hoa).
     */
    public ValidationResult validateAftnAddress(String aftn, String fieldName) {
        if (aftn == null || aftn.isBlank()) {
            return ValidationResult.failure(fieldName + " is null or empty");
        }

        String trimmed = aftn.trim();

        if (trimmed.length() != 8) {
            return ValidationResult.failure(String.format("%s '%s' must be exactly 8 characters (actual: %d)",
                    fieldName, trimmed, trimmed.length()));
        }

        if (!trimmed.matches(AFTN_ADDRESS_PATTERN)) {
            return ValidationResult.failure(String.format("%s '%s' must contain only uppercase letters",
                    fieldName, trimmed));
        }

        return ValidationResult.success();
    }

    /**
     * Kiểm tra loại thông tin mã hoá (current encoded-information-types) của IPM.
     */
    public ValidationResult validateEncodedInformationTypes(String eit) {
        if (eit == null || eit.isBlank()) {
            return ValidationResult.success();
        }
        for (String value : splitEitValues(eit)) {
            if (!isAllowedEit(value.toLowerCase())) {
                return ValidationResult.failure(
                        "Unsupported encoded-information-types: " + value);
            }
        }
        return ValidationResult.success();
    }

    private static final java.util.regex.Pattern EIT_VALUE_PATTERN =
            java.util.regex.Pattern.compile("\\{[^}]*\\}|[^,;\\s]+");

    /** {@code {id-cs-eit-authority N}} dạng ký hiệu. */
    private static final java.util.regex.Pattern EIT_AUTHORITY_PATTERN =
            java.util.regex.Pattern.compile("authority\\D+(\\d+)");

    private static final String EIT_OID_UNDEFINED = "2.6.3.4.0";
    private static final String EIT_OID_IA5_TEXT = "2.6.3.4.2";
    private static final String EIT_OID_FILE_TRANSFER = "2.6.1.12.0";

    /** Các tiền tố OID của authority. */
    private static final List<String> EIT_AUTHORITY_OID_PREFIXES = List.of(
            "1.0.10021.7.1.0.",
            "2.16.840.1.101.2.1.22.");

    private static final java.util.Set<String> ALLOWED_EIT_AUTHORITIES =
            java.util.Set.of("1", "2", "6", "100");

    /**
     * Tách chuỗi EIT thành từng giá trị riêng.
     */
    private static List<String> splitEitValues(String eit) {
        List<String> values = new ArrayList<>();
        java.util.regex.Matcher matcher = EIT_VALUE_PATTERN.matcher(eit);
        while (matcher.find()) {
            String value = matcher.group().trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private boolean isAllowedEit(String token) {
        if (token.contains("unspecified") || token.contains("unknown")) {
            return true;
        }
        if (token.contains("ia5-text") || token.contains("ia5text")) {
            return true;
        }
        if (token.contains("file-transfer") || token.contains("filetransfer")) {
            return true;
        }
        java.util.regex.Matcher matcher = EIT_AUTHORITY_PATTERN.matcher(token);
        if (matcher.find()) {
            return ALLOWED_EIT_AUTHORITIES.contains(matcher.group(1));
        }

        String oid = token.replaceAll("[{}\\s]", "");
        if (EIT_OID_UNDEFINED.equals(oid) || EIT_OID_IA5_TEXT.equals(oid)
                || EIT_OID_FILE_TRANSFER.equals(oid)) {
            return true;
        }
        for (String prefix : EIT_AUTHORITY_OID_PREFIXES) {
            if (oid.startsWith(prefix)) {
                return ALLOWED_EIT_AUTHORITIES.contains(oid.substring(prefix.length()));
            }
        }
        return false;
    }

    /**
     * Kiểm tra repertoire của body part.
     */
    public ValidationResult validateRepertoire(String bodyPartType, String repertoire) {
        if (bodyPartType == null || repertoire == null || repertoire.isBlank()) {
            return ValidationResult.success();
        }
        String bpt = bodyPartType.trim().toLowerCase();
        String rep = repertoire.trim().toUpperCase();

        if (bpt.startsWith("ia5-text")) {
            if (REPERTOIRE_ITA2.equals(rep)) {
                return ValidationResult.failure(String.format(
                        "ia5-text-body-part repertoire '%s' is not supported (unsupported-body-part-type)",
                        repertoire.trim()));
            }
            return ValidationResult.success();
        }

        if (bpt.equals("general-text-body-part")) {
            if (REPERTOIRE_ISO646.equals(rep)) {
                return ValidationResult.success();
            }
            if (configService.isNonIso646RepertoireAllowed()) {
                return ValidationResult.success();
            }
            return ValidationResult.failure(String.format(
                    "general-text-body-part repertoire '%s' rejected by local AMHS Management Domain "
                            + "policy (unsupported-encoded-information-types)",
                    repertoire.trim()));
        }

        return ValidationResult.success();
    }

    public ValidationResult validateBodyPartType(String bodyPartType) {
        if (bodyPartType == null || bodyPartType.isBlank()) {
            return ValidationResult.success();
        }
        String cleanType = bodyPartType.trim().toLowerCase();
        if (cleanType.equals("ia5-text") ||
                cleanType.equals("ia5-text-body-part") ||
                cleanType.equals("401") ||
                cleanType.equals("general-text") ||
                cleanType.equals("general-text-body-part") ||
                cleanType.equals("402") ||
                cleanType.equals("407") ||
                cleanType.equals("file-transfer") ||
                cleanType.equals("file-transfer-body-part") ||
                cleanType.equals("403")) {
            return ValidationResult.success();
        }
        return ValidationResult.failure("Unsupported Encoded Information Type (EIT) / Body Part Type: " + bodyPartType);
    }
}
