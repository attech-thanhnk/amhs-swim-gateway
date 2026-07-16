package vn.asg.swim.entity;

/**
 * Unified status Enum for Gwin (IN) and Gwout (OUT) message lifecycles.
 */
public enum MessageStatus {
    // Inbound (Gwin) statuses
    IN_PENDING(0),
    IN_PROCESSING(1),
    IN_TRANSFORMED(2),
    IN_SENT(3),
    IN_FAILED(4),
    IN_UNROUTED(5),
    IN_RESOLVED(6),
    IN_CANCELLED(7),

    // Outbound (Gwout) statuses
    OUT_PENDING(0),
    OUT_TRANSFORMED(2),
    OUT_PUBLISHED(3),
    OUT_FAILED(5),
    OUT_RESOLVED(6),
    OUT_CANCELLED(7);

    private final int value;

    MessageStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static MessageStatus fromInValue(int val) {
        for (MessageStatus s : values()) {
            if (s.name().startsWith("IN_") && s.value == val) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown Inbound status: " + val);
    }

    public static MessageStatus fromOutValue(int val) {
        for (MessageStatus s : values()) {
            if (s.name().startsWith("OUT_") && s.value == val) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown Outbound status: " + val);
    }
}
