package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Bảng gwin_dispatch — Từng lệnh gửi vào AMHS cho mỗi recipient.
 * Một gwin có thể có nhiều dispatch (1 per AMHS recipient).
 * Retry logic và trạng thái xử lý được quản lý tại level dispatch.
 */
@Entity
@Table(name = "gwin_dispatch")
@Data
@NoArgsConstructor
public class GwinDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK tới gwin.msgid */
    @Column(name = "gwin_id", nullable = false)
    private Long gwinId;

    /** Địa chỉ AMHS recipient sẽ gửi đến. Ví dụ: VVHHZTZX (ATC Hà Nội) */
    @Column(name = "amhs_address", length = 100, nullable = false)
    private String amhsAddress;

    /**
     * Trạng thái xử lý: PENDING → PROCESSING → SENT hoặc FAILED → DEAD
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** Số lần đã retry. Vượt RETRY_MAX_COUNT → DEAD */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /**
     * Thời điểm retry tiếp theo.
     * retry 1: NOW+30s, retry 2: NOW+120s, retry 3: NOW+300s. NULL = chưa cần
     * retry.
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * Mô tả lỗi. Ví dụ:
     * "MTA từ chối: địa chỉ VVHHZTZX không tồn tại"
     * "Timeout kết nối AMHS sau 30s"
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Bước xảy ra lỗi: routing / convert / send
     */
    @Column(name = "failed_step", length = 20)
    private String failedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Thời điểm gửi thành công */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD = "DEAD";

    // Failed step constants
    public static final String STEP_ROUTING = "routing";
    public static final String STEP_CONVERT = "convert";
    public static final String STEP_SEND = "send";
}
