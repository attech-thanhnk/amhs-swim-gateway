package vn.asg.cp.entity;

/**
 * Lifecycle status for Inbound messages (SWIM -> AMHS Gateway: gwin).
 */
public enum InboundStatus {
    PENDING(0),      // Chờ xử lý
    UNROUTED(1),     // Chưa định tuyến (Cần gán trên CP)
    TRANSFORMED(2),  // Đã bọc phong bì X.400
    DELIVERED(3),    // Gửi MTA AMHS thành công (OK)
    FAILED(4),       // Thất bại
    RESOLVED(5),     // Operator định tuyến thủ công xong
    CANCELLED(6);    // Đã hủy

    private final int value;

    InboundStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static InboundStatus fromValue(int value) {
        if (value == 10) return DELIVERED;
        if (value == 11) return FAILED;
        for (InboundStatus status : values()) {
            if (status.getValue() == value) {
                return status;
            }
        }
        return FAILED;
    }
}
