package vn.asg.swim.exception;

/**
 * Exception khi convert message fail (TAC ↔ JSON).
 */
public class ConversionException extends GatewayException {

    private final String messageType;
    private final String sourceFormat;
    private final String targetFormat;

    public ConversionException(String message, String messageType, String sourceFormat, String targetFormat) {
        super(message);
        this.messageType = messageType;
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
    }

    public ConversionException(String message, Throwable cause, String messageType) {
        super(message, cause);
        this.messageType = messageType;
        this.sourceFormat = null;
        this.targetFormat = null;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    @Override
    public String toString() {
        return String.format("ConversionException[type=%s, %s→%s]: %s",
            messageType, sourceFormat, targetFormat, getMessage());
    }
}
