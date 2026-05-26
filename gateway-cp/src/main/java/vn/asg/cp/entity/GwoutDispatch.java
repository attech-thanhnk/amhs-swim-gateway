package vn.asg.cp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Bảng gwout_dispatch — Từng lệnh publish lên AMQP cho mỗi recipient.
 * Một gwout có thể có nhiều dispatch (nhiều recipients → nhiều topics).
 * Retry logic và trạng thái xử lý được quản lý tại level dispatch.
 */
@Entity
@Table(name = "gwout_dispatch")
public class GwoutDispatch {

    public GwoutDispatch() {}

    // Status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD = "DEAD";

    // Failed step constants
    public static final String STEP_DETECT = "detect";
    public static final String STEP_ROUTING = "routing";
    public static final String STEP_CONVERT = "convert";
    public static final String STEP_PUBLISH = "publish";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK tới gwout.msgid */
    @Column(name = "gwout_id", nullable = false)
    private Long gwoutId;

    /** Địa chỉ AMHS recipient tương ứng với dispatch này. Ví dụ: VVHHZTZX */
    @Column(name = "recipient", length = 100, nullable = false)
    private String recipient;

    /** Loại điện văn đã nhận dạng. Ví dụ: METAR, FPL, UNKNOWN */
    @Column(name = "message_type", length = 50)
    private String messageType;

    /** Scope địa lý đã parse. Ví dụ: vvhh (sân bay), vvhf (FIR) */
    @Column(name = "scope", length = 10)
    private String scope;

    /**
     * Topic AMQP đã xác định. Ví dụ: metar.vvhh, fpl.vvhf. NULL nếu routing thất
     * bại
     */
    @Column(name = "topic", length = 100)
    private String topic;

    /** Account AMQP dùng để publish. Ví dụ: LOCAL_BROKER */
    @Column(name = "amqp_account", length = 50)
    private String amqpAccount;

    /**
     * Trạng thái xử lý: PENDING → PROCESSING → SENT hoặc FAILED → DEAD
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** Số lần đã retry */
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
     * Mô tả lỗi chi tiết. Ví dụ:
     * "Broker timeout sau 30s", "Không tìm được routing rule cho METAR+VVHHZTZX"
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Bước xảy ra lỗi: detect / routing / convert / publish
     */
    @Column(name = "failed_step", length = 20)
    private String failedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Thời điểm publish thành công */
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGwoutId() { return gwoutId; }
    public void setGwoutId(Long gwoutId) { this.gwoutId = gwoutId; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getAmqpAccount() { return amqpAccount; }
    public void setAmqpAccount(String amqpAccount) { this.amqpAccount = amqpAccount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getFailedStep() { return failedStep; }
    public void setFailedStep(String failedStep) { this.failedStep = failedStep; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
