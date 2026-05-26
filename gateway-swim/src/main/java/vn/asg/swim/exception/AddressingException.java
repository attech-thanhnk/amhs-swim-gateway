package vn.asg.swim.exception;

/**
 * Exception khi không resolve được AMHS addressing (originator + recipients).
 */
public class AddressingException extends GatewayException {

    private final String queue;
    private final String messageType;

    public AddressingException(String message, String queue, String messageType) {
        super(message);
        this.queue = queue;
        this.messageType = messageType;
    }

    public String getQueue() {
        return queue;
    }

    public String getMessageType() {
        return messageType;
    }

    @Override
    public String toString() {
        return String.format("AddressingException[queue=%s, type=%s]: %s",
                queue, messageType, getMessage());
    }
}
