package vn.asg.swim.exception;

import java.util.List;

/**
 * Exception khi message validation fail theo EUR Doc 047.
 */
public class ValidationException extends GatewayException {

    private final List<String> validationErrors;
    private final String messageId;

    public ValidationException(String message, String messageId, List<String> validationErrors) {
        super(message);
        this.messageId = messageId;
        this.validationErrors = validationErrors;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public String getMessageId() {
        return messageId;
    }

    @Override
    public String toString() {
        return String.format("ValidationException[messageId=%s, errors=%d]: %s",
                messageId, validationErrors != null ? validationErrors.size() : 0, getMessage());
    }
}
