package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * GwinDispatch entity — Individual send commands to AMHS for each recipient.
 * A single Gwin can have multiple dispatches (1 per AMHS recipient).
 * Retry logic and processing state are managed at the dispatch level.
 */
@Entity
@Table(name = "gwin_dispatch")
public class GwinDispatch {

    public GwinDispatch() {}

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to gwin.msgid */
    @Column(name = "gwin_id", nullable = false)
    private Long gwinId;

    /** AMHS recipient address. Example: VVHHZTZX (Hanoi ATC) */
    @Column(name = "amhs_address", length = 100, nullable = false)
    private String amhsAddress;

    /**
     * Processing status: PENDING → PROCESSING → SENT or FAILED → DEAD
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** Current retry count. Escalates to DEAD if it exceeds RETRY_MAX_COUNT. */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /**
     * Next scheduled retry time.
     * Calculated using exponential backoff (e.g., +30s, +120s, +300s). NULL if no
     * retry needed.
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * Error description. Example:
     * "MTA Rejected: address VVHHZTZX not found"
     * "AMHS connection timeout after 30s"
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Step where the failure occurred: routing / convert / send
     */
    @Column(name = "failed_step", length = 20)
    private String failedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Success timestamp of the send task */
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
    public Long getGwinId() { return gwinId; }
    public void setGwinId(Long gwinId) { this.gwinId = gwinId; }
    public String getAmhsAddress() { return amhsAddress; }
    public void setAmhsAddress(String amhsAddress) { this.amhsAddress = amhsAddress; }
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
