package vn.asg.swim.entity;

/**
 * Lifecycle status for Outbound messages (AMHS -> SWIM Gateway: gwout).
 */
public enum OutboundStatus {
    PENDING(0),      // Chờ xử lý
    TRANSFORMED(1),  // Đã validate & map Topic
    PUBLISHED(2),    // Phát AMQP Solace thành công (OK)
    FAILED(3),       // Convert / Publish thất bại
    UNROUTED(4),     // Chưa định tuyến
    RESOLVED(5),     // Operator xử lý xong
    CANCELLED(6);    // Đã hủy

    private final int value;

    OutboundStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static OutboundStatus fromValue(int value) {
        for (OutboundStatus status : values()) {
            if (status.getValue() == value) {
                return status;
            }
        }
        return FAILED;
    }
}
