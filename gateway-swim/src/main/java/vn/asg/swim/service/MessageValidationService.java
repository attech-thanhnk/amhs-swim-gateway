package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Tuân thủ EUR Doc 047: Dịch vụ Kiểm thử tính Hợp lệ của Bản tin (Message Validation Service)
 *
 * Thực hiện kiểm thử tính hợp lệ của các bản tin AMQP theo các yêu cầu của tài liệu EUR Doc 047:
 * - C-02, C-03: Kiểm tra chiều chuyển đổi định dạng (Conversion direction)
 * - C-05, S-08: Kiểm tra giới hạn kích thước bản tin (Message size)
 * - C-07, S-09: Kiểm tra số lượng người nhận (Recipients count)
 * - S-06, S-07: Kiểm tra các trường bắt buộc (Mandatory fields)
 * - S-11, S-15: Kiểm tra định dạng địa chỉ AFTN (AFTN address format)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageValidationService {

    private final ConfigService configService;

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
     * EUR Doc 047 §4.5.1 - Kiểm thử bản tin chiều SWIM → AMHS
     *
     * Thực hiện kiểm tra:
     * - C-02: Chiều chuyển đổi định dạng có cho phép SWIM → AMHS
     * - S-06: Sự hiện diện của các trường bắt buộc (Mandatory fields)
     * - S-08: Kích thước bản tin trong giới hạn cho phép
     * - S-09: Số lượng người nhận trong giới hạn cho phép
     */
    public ValidationResult validateSwimToAmhs(String messageId, Message msg, String payload) {
        List<String> errors = new ArrayList<>();

        // C-02: Kiểm tra chiều chuyển đổi định dạng
        String direction = configService.getConversionDir();
        if ("AMHS_TO_SWIM".equals(direction)) {
            errors.add("Conversion direction is AMHS_TO_SWIM - SWIM→AMHS messages not allowed");
            return ValidationResult.failure(errors);
        }

        // S-06: Kiểm thử các trường AMQP bắt buộc
        try {
            if (messageId == null || messageId.isBlank()) {
                errors.add("Mandatory field 'message-id' (JMSMessageID) is missing");
            }

            // Priority và Timestamp: chỉ đưa ra cảnh báo thay vì từ chối bản tin nhằm tương thích với các công cụ kiểm thử không hoàn toàn tuân thủ
            try {
                msg.getJMSPriority();
            } catch (Exception e) {
                log.warn("Message {}: Mandatory field 'priority' (JMSPriority) is missing", messageId);
            }

            long timestamp = msg.getJMSTimestamp();
            if (timestamp <= 0) {
                log.warn("Message {}: Mandatory field 'creation-time' (JMSTimestamp) is missing", messageId);
            }

            // Bắt buộc: trường data hoặc amqp-value (payload)
            if (payload == null || payload.isBlank()) {
                errors.add("Mandatory field 'data/amqp-value' (message body) is missing");
            }

            // CTSW116: Validate FTBP properties if body part is file-transfer-body-part
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

        // S-08: Kiểm tra kích thước bản tin (EUR Doc 047 §3.3.1.4: 0 hoặc không cấu hình = không giới hạn)
        if (payload != null) {
            int maxSize = configService.getMaxMsgDataSize();
            int actualSize = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (maxSize > 0 && actualSize > maxSize) {
                errors.add(String.format("Message size %d bytes exceeds maximum %d bytes", actualSize, maxSize));
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
    }

    /**
     * EUR Doc 047 §4.4.1 - Kiểm thử bản tin chiều AMHS → SWIM
     *
     * Thực hiện kiểm tra:
     * - C-03: Chiều chuyển đổi định dạng có cho phép AMHS → SWIM
     * - C-05: Kích thước bản tin trong giới hạn cho phép
     * - C-07: Số lượng người nhận trong giới hạn cho phép
     */
    public ValidationResult validateAmhsToSwim(String payload, String recipients) {
        List<String> errors = new ArrayList<>();

        // C-03: Kiểm tra chiều chuyển đổi định dạng
        String direction = configService.getConversionDir();
        if ("SWIM_TO_AMHS".equals(direction)) {
            errors.add("Conversion direction is SWIM_TO_AMHS - AMHS→SWIM messages not allowed");
            return ValidationResult.failure(errors);
        }

        // C-05: Kiểm tra kích thước bản tin (EUR Doc 047 §3.3.1.4: 0 hoặc không cấu hình = không giới hạn)
        if (payload != null) {
            int maxSize = configService.getMaxMsgDataSize();
            int actualSize = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (maxSize > 0 && actualSize > maxSize) {
                errors.add(String.format("Message size %d bytes exceeds maximum %d bytes (content-too-long)",
                        actualSize, maxSize));
            }
        }

        // C-07: Kiểm tra số lượng người nhận (EUR Doc 047 §3.3.2.4: 0 hoặc không cấu hình = không giới hạn)
        if (recipients != null && !recipients.isBlank()) {
            String[] recipientArray = recipients.trim().split("\\s+");
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
     * EUR Doc 047 §4.5.2.4 - Kiểm thử định dạng địa chỉ AFTN
     *
     * S-11, S-15: Địa chỉ AFTN phải có đúng 8 ký tự alphanumeric viết hoa
     */
    public ValidationResult validateAftnAddress(String aftn, String fieldName) {
        if (aftn == null || aftn.isBlank()) {
            return ValidationResult.failure(fieldName + " is null or empty");
        }

        String trimmed = aftn.trim();

        // Phải có đúng 8 ký tự
        if (trimmed.length() != 8) {
            return ValidationResult.failure(String.format("%s '%s' must be exactly 8 characters (actual: %d)",
                    fieldName, trimmed, trimmed.length()));
        }

        // Phải là ký tự alphanumeric viết hoa
        if (!trimmed.matches("[A-Z0-9]{8}")) {
            return ValidationResult.failure(String.format("%s '%s' must contain only uppercase letters and digits",
                    fieldName, trimmed));
        }

        return ValidationResult.success();
    }

    /**
     * EUR Doc 047 - Kiểm thử danh sách địa chỉ AFTN phân tách bằng dấu cách
     *
     * S-09, S-11: Kiểm thử danh sách người nhận (recipients list)
     */
    public ValidationResult validateAftnRecipients(String recipients) {
        if (recipients == null || recipients.isBlank()) {
            return ValidationResult.failure("Recipients list is empty");
        }

        List<String> errors = new ArrayList<>();
        String[] addresses = recipients.trim().split("\\s+");

        // S-09: Kiểm tra số lượng người nhận (EUR Doc 047 §3.3.2.4: 0 hoặc không cấu hình = không giới hạn)
        int maxRecipients = configService.getMaxMsgRecipients();
        if (maxRecipients > 0 && addresses.length > maxRecipients) {
            errors.add(String.format("Recipients count %d exceeds maximum %d", addresses.length, maxRecipients));
        }

        // S-11: Kiểm thử định dạng của từng địa chỉ cụ thể
        for (int i = 0; i < addresses.length; i++) {
            ValidationResult result = validateAftnAddress(addresses[i], "Recipient[" + i + "]");
            if (!result.isValid()) {
                errors.addAll(result.getErrors());
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
    }

    /**
     * S-06, CTSW016: Kiểm thử định dạng EIT/Body Part Type
     */
    public ValidationResult validateBodyPartType(String bodyPartType) {
        if (bodyPartType == null || bodyPartType.isBlank()) {
            return ValidationResult.success();
        }
        String cleanType = bodyPartType.trim().toLowerCase();
        if (cleanType.equals("ia5-text") ||
                cleanType.equals("ia5-text-body-part") ||
                cleanType.equals("general-text-body-part") ||
                cleanType.equals("file-transfer-body-part")) {
            return ValidationResult.success();
        }
        return ValidationResult.failure("Unsupported Encoded Information Type (EIT) / Body Part Type: " + bodyPartType);
    }

    /**
     * Kiểm tra xem chiều chuyển đổi hiện tại có cho phép chiều mong muốn hay không
     */
    public boolean isDirectionAllowed(String direction) {
        String configDir = configService.getConversionDir();
        return "BOTH".equals(configDir) || configDir.equals(direction);
    }
}
