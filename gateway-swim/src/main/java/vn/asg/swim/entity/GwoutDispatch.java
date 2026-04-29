package vn.asg.swim.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * GwoutDispatch entity — Individual publish commands to AMQP for each
 * recipient.
 * A single Gwout can have multiple dispatches (multiple recipients → multiple
 * topics).
 * Retry logic and processing state are managed at the dispatch level.
 */
@Entity
@Table(name = "gwout_dispatch")
@Data
@NoArgsConstructor
public class GwoutDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to gwout.msgid */
    @Column(name = "gwout_id", nullable = false)
    private Long gwoutId;

    /** AMHS recipient address for this dispatch. Example: VVHHZTZX */
    @Column(name = "recipient", length = 100, nullable = false)
    private String recipient;

    /** Detected message type. Example: METAR, FPL, UNKNOWN */
    @Column(name = "message_type", length = 50)
    private String messageType;

    /** Parsed geographic scope. Example: vvhh (Airport), vvhf (FIR) */
    @Column(name = "scope", length = 10)
    private String scope;

    /**
     * Resolved AMQP topic. Example: metar.vvhh, fpl.vvhf. NULL if routing fails.
     */
    @Column(name = "topic", length = 100)
    private String topic;

    /** AMQP account used for publishing. Example: LOCAL_BROKER */
    @Column(name = "amqp_account", length = 50)
    private String amqpAccount;

    /**
     * Processing status: PENDING → PROCESSING → SENT or FAILED → DEAD
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** Total retry attempts */
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
     * Detailed error message. Example:
     * "Broker timeout after 30s", "No routing rule found for METAR+VVHHZTZX"
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Step where the failure occurred: detect / routing / convert / publish
     */
    @Column(name = "failed_step", length = 20)
    private String failedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Success timestamp of the publish task */
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
    public static final String STEP_DETECT = "detect";
    public static final String STEP_ROUTING = "routing";
    public static final String STEP_CONVERT = "convert";
    public static final String STEP_PUBLISH = "publish";
}
