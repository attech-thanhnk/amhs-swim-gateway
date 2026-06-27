package vn.asg.cp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GwoutDispatchHistory Table — Represents archived gwout_dispatch records.
 */
@Entity
@Table(name = "gwout_dispatch_history")
@Data
@NoArgsConstructor
public class GwoutDispatchHistory {


    @Id
    private Long id;

    @Column(name = "gwout_id", nullable = false)
    private Long gwoutId;

    @Column(name = "recipient", length = 100, nullable = false)
    private String recipient;

    @Column(name = "message_type", length = 50)
    private String messageType;

    @Column(name = "scope", length = 10)
    private String scope;

    @Column(name = "topic", length = 100)
    private String topic;

    @Column(name = "amqp_account", length = 50)
    private String amqpAccount;

    @Column(name = "status", nullable = false, length = 20)
    private String status = GwoutDispatch.STATUS_PENDING;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "failed_step", length = 20)
    private String failedStep;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
