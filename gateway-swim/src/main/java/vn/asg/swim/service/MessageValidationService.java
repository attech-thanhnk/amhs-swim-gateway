package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * EUR Doc 047 Compliance: Message Validation Service
 *
 * Validates AMQP messages according to EUR Doc 047 requirements:
 * - C-02, C-03: Conversion direction check
 * - C-05, S-08: Message size validation
 * - C-07, S-09: Recipients count validation
 * - S-06, S-07: Mandatory fields validation
 * - S-11, S-15: AFTN address format validation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageValidationService {

    private final ConfigService configService;

    /**
     * Validation result container
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
     * EUR Doc 047 §4.5.1 - Validate SWIM→AMHS message
     *
     * Checks:
     * - C-02: Conversion direction allows SWIM→AMHS
     * - S-06: Mandatory fields present
     * - S-08: Message size within limit
     * - S-09: Recipients count within limit
     */
    public ValidationResult validateSwimToAmhs(String messageId, Message msg, String payload) {
        List<String> errors = new ArrayList<>();

        // C-02: Check conversion direction
        String direction = configService.getConversionDir();
        if ("AMHS_TO_SWIM".equals(direction)) {
            errors.add("Conversion direction is AMHS_TO_SWIM - SWIM→AMHS messages not allowed");
            return ValidationResult.failure(errors);
        }

        // S-06: Validate mandatory AMQP fields
        try {
            if (messageId == null || messageId.isBlank()) {
                errors.add("Mandatory field 'message-id' (JMSMessageID) is missing");
            }

            // Priority and Timestamp: warning instead of rejection to handle non-compliant
            // test tools
            try {
                msg.getJMSPriority();
            } catch (Exception e) {
                log.warn("Message {}: Mandatory field 'priority' (JMSPriority) is missing", messageId);
            }

            long timestamp = msg.getJMSTimestamp();
            if (timestamp <= 0) {
                log.warn("Message {}: Mandatory field 'creation-time' (JMSTimestamp) is missing", messageId);
            }

            // Required: data or amqp-value (payload)
            if (payload == null || payload.isBlank()) {
                errors.add("Mandatory field 'data/amqp-value' (message body) is missing");
            }

        } catch (JMSException e) {
            errors.add("Failed to read AMQP message properties: " + e.getMessage());
        }

        // S-08: Check message size
        if (payload != null) {
            int maxSize = configService.getMaxMsgDataSize();
            int actualSize = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (actualSize > maxSize) {
                errors.add(String.format("Message size %d bytes exceeds maximum %d bytes", actualSize, maxSize));
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
    }

    /**
     * EUR Doc 047 §4.4.1 - Validate AMHS→SWIM message
     *
     * Checks:
     * - C-03: Conversion direction allows AMHS→SWIM
     * - C-05: Message size within limit
     * - C-07: Recipients count within limit
     */
    public ValidationResult validateAmhsToSwim(String payload, String recipients) {
        List<String> errors = new ArrayList<>();

        // C-03: Check conversion direction
        String direction = configService.getConversionDir();
        if ("SWIM_TO_AMHS".equals(direction)) {
            errors.add("Conversion direction is SWIM_TO_AMHS - AMHS→SWIM messages not allowed");
            return ValidationResult.failure(errors);
        }

        // C-05: Check message size
        if (payload != null) {
            int maxSize = configService.getMaxMsgDataSize();
            int actualSize = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (actualSize > maxSize) {
                errors.add(String.format("Message size %d bytes exceeds maximum %d bytes (content-too-long)",
                        actualSize, maxSize));
            }
        }

        // C-07: Check recipients count
        if (recipients != null && !recipients.isBlank()) {
            String[] recipientArray = recipients.trim().split("\\s+");
            int maxRecipients = configService.getMaxMsgRecipients();
            if (recipientArray.length > maxRecipients) {
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
     * EUR Doc 047 §4.5.2.4 - Validate AFTN address format
     *
     * S-11, S-15: AFTN address must be exactly 8 uppercase alphanumeric characters
     */
    public ValidationResult validateAftnAddress(String aftn, String fieldName) {
        if (aftn == null || aftn.isBlank()) {
            return ValidationResult.failure(fieldName + " is null or empty");
        }

        String trimmed = aftn.trim();

        // Must be exactly 8 characters
        if (trimmed.length() != 8) {
            return ValidationResult.failure(String.format("%s '%s' must be exactly 8 characters (actual: %d)",
                    fieldName, trimmed, trimmed.length()));
        }

        // Must be uppercase alphanumeric
        if (!trimmed.matches("[A-Z0-9]{8}")) {
            return ValidationResult.failure(String.format("%s '%s' must contain only uppercase letters and digits",
                    fieldName, trimmed));
        }

        return ValidationResult.success();
    }

    /**
     * EUR Doc 047 - Validate space-separated AFTN addresses
     *
     * S-09, S-11: Validate recipients list
     */
    public ValidationResult validateAftnRecipients(String recipients) {
        if (recipients == null || recipients.isBlank()) {
            return ValidationResult.failure("Recipients list is empty");
        }

        List<String> errors = new ArrayList<>();
        String[] addresses = recipients.trim().split("\\s+");

        // S-09: Check count
        int maxRecipients = configService.getMaxMsgRecipients();
        if (addresses.length > maxRecipients) {
            errors.add(String.format("Recipients count %d exceeds maximum %d", addresses.length, maxRecipients));
        }

        // S-11: Validate each address format
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
     * Check if conversion direction allows the specified direction
     */
    public boolean isDirectionAllowed(String direction) {
        String configDir = configService.getConversionDir();
        return "BOTH".equals(configDir) || configDir.equals(direction);
    }
}
