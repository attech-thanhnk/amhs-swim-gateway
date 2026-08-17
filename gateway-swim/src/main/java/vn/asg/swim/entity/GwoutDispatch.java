package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Thực thể GwoutDispatch - Lưu trữ các lệnh gửi tin riêng biệt tới AMQP cho
 * từng người nhận.
 * Một bản tin Gwout có thể có nhiều đích đến (nhiều người nhận -> nhiều topic).
 * Logic thử lại và trạng thái xử lý được quản lý ở cấp độ này.
 */
@Entity
@Table(name = "gwout_dispatch")
public class GwoutDispatch {

    public GwoutDispatch() {
    }

    // Các hằng số trạng thái
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PUBLISHING = "PUBLISHING"; // Đang gửi AMQP (tránh lỗi rollback sau khi gửi)
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD = "DEAD";

    // Các hằng số bước bị lỗi
    public static final String STEP_VALIDATION = "validation";
    public static final String STEP_AUTHORIZATION = "authorization";
    public static final String STEP_DETECT = "detect";
    public static final String STEP_ROUTING = "routing";
    public static final String STEP_PUBLISH = "publish";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Khóa ngoại trỏ đến gwout.msgid */
    @Column(name = "gwout_id", nullable = false)
    private Long gwoutId;

    /** Địa chỉ người nhận AMHS. Ví dụ: VVHHZTZX */
    @Column(name = "recipient", length = 100, nullable = false)
    private String recipient;

    /** Loại bản tin được nhận dạng. Ví dụ: METAR, FPL */
    @Column(name = "message_type", length = 50)
    private String messageType;

    /**
     * Phạm vi địa lý được phân tích. Ví dụ: vvhh (Sân bay), vvhf (Vùng thông báo
     * bay)
     */
    @Column(name = "scope", length = 10)
    private String scope;

    /**
     * Topic AMQP tương ứng sau khi phân giải. NULL nếu lỗi định tuyến.
     */
    @Column(name = "topic", length = 100)
    private String topic;

    /** Tài khoản AMQP dùng để gửi tin. Ví dụ: LOCAL_BROKER */
    @Column(name = "amqp_account", length = 50)
    private String amqpAccount;

    /**
     * Trạng thái xử lý: PENDING → PROCESSING → SENT hoặc FAILED → DEAD
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** Tổng số lần thử lại */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /**
     * Thời gian dự kiến thử lại tiếp theo.
     * Tính toán theo exponential backoff (ví dụ: +30s, +120s, +300s).
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * Chi tiết thông báo lỗi cuối cùng.
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Bước xảy ra lỗi cuối cùng: detect / routing / publish
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGwoutId() {
        return gwoutId;
    }

    public void setGwoutId(Long gwoutId) {
        this.gwoutId = gwoutId;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getAmqpAccount() {
        return amqpAccount;
    }

    public void setAmqpAccount(String amqpAccount) {
        this.amqpAccount = amqpAccount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getFailedStep() {
        return failedStep;
    }

    public void setFailedStep(String failedStep) {
        this.failedStep = failedStep;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
