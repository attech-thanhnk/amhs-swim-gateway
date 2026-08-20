package vn.asg.swim.entity;

/**
 * Convenience compatibility class linking to InboundStatus and OutboundStatus.
 */
public enum MessageStatus {
    // Inbound (Gwin) statuses
    IN_PENDING(InboundStatus.PENDING.getValue()),
    IN_UNROUTED(InboundStatus.UNROUTED.getValue()),
    IN_TRANSFORMED(InboundStatus.TRANSFORMED.getValue()),
    IN_DELIVERED(InboundStatus.DELIVERED.getValue()),
    IN_FAILED(InboundStatus.FAILED.getValue()),
    IN_RESOLVED(InboundStatus.RESOLVED.getValue()),
    IN_CANCELLED(InboundStatus.CANCELLED.getValue()),

    // Outbound (Gwout) statuses
    OUT_PENDING(OutboundStatus.PENDING.getValue()),
    OUT_TRANSFORMED(OutboundStatus.TRANSFORMED.getValue()),
    OUT_PUBLISHED(OutboundStatus.PUBLISHED.getValue()),
    OUT_FAILED(OutboundStatus.FAILED.getValue()),
    OUT_UNROUTED(OutboundStatus.UNROUTED.getValue()),
    OUT_RESOLVED(OutboundStatus.RESOLVED.getValue()),
    OUT_CANCELLED(OutboundStatus.CANCELLED.getValue());

    private final int value;

    MessageStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
