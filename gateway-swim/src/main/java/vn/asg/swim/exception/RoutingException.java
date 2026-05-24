package vn.asg.swim.exception;

/**
 * Exception khi không tìm thấy routing rule phù hợp.
 */
public class RoutingException extends GatewayException {

    private final String direction;
    private final String topic;
    private final String messageType;

    public RoutingException(String message, String direction, String topic, String messageType) {
        super(message);
        this.direction = direction;
        this.topic = topic;
        this.messageType = messageType;
    }

    public String getDirection() {
        return direction;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageType() {
        return messageType;
    }

    @Override
    public String toString() {
        return String.format("RoutingException[direction=%s, topic=%s, type=%s]: %s",
                direction, topic, messageType, getMessage());
    }
}
