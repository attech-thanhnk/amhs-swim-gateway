package vn.asg.swim.entity;

/**
 * Unified status Enum for Gwin (IN) and Gwout (OUT) message lifecycles.
 * Đánh số liên tục theo từng chiều (không dùng chung 1 dải số cho cả IN/OUT).
 * Yêu cầu chạy kèm migration database/migration_2026-08-19_renumber_message_status.sql
 * TRƯỚC khi khởi động lại gateway-swim với bản build có enum này — nếu không dữ liệu
 * status cũ (đánh số theo enum trước đó) sẽ bị đọc sai.
 */
public enum MessageStatus {
    // Inbound (Gwin) statuses
    IN_PENDING(0),
    IN_FAILED(1),
    IN_UNROUTED(2),
    IN_RESOLVED(3),
    IN_CANCELLED(4),

    // Outbound (Gwout) statuses
    OUT_PENDING(0),
    OUT_TRANSFORMED(1),
    OUT_PUBLISHED(2),
    OUT_FAILED(3),
    OUT_RESOLVED(4),
    OUT_CANCELLED(5);

    private final int value;

    MessageStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
