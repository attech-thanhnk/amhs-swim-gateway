package vn.asg.swim.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * GwinDispatch entity — Individual send commands to AMHS for each recipient.
 * A single Gwin can have multiple dispatches (1 per AMHS recipient).
 * Retry logic and processing state are managed at the dispatch level.
 */
@Entity
@Table(name = "gwin_dispatch")
@Data
@NoArgsConstructor
public class GwinDispatch {

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
