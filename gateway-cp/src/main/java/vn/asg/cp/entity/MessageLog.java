package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================
    // MESSAGE IDENTIFICATION
    // =========================

    @Column(name = "message_id", length = 100, nullable = false)
    private String messageId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    // =========================
    // DIRECTION & SYSTEMS
    // =========================

    @Column(name = "direction", length = 10)
    private String direction; // IN / OUT

    @Column(name = "source_system", length = 20)
    private String sourceSystem; // AMHS / SWIM

    @Column(name = "target_system", length = 20)
    private String targetSystem;

    // =========================
    // ROUTING
    // =========================

    @Column(name = "routing_rule_id")
    private Integer routingRuleId;

    @Column(name = "routing_result", length = 50)
    private String routingResult; // MATCHED / NO_RULE / FAILED

    // =========================
    // AMHS METADATA
    // =========================

    @Column(name = "amhs_message_type", length = 50)
    private String amhsMessageType;

    @Column(name = "amhs_priority", length = 10)
    private String amhsPriority; // SS/DD/FF/GG/KK

    @Column(name = "originator", length = 8)
    private String originator;

    @Column(name = "recipients", columnDefinition = "TEXT")
    private String recipients;

    // =========================
    // SWIM METADATA
    // =========================

    @Column(name = "swim_topic", length = 255)
    private String swimTopic;

    @Column(name = "swim_priority")
    private Integer swimPriority;

    @Column(name = "content_type", length = 30)
    private String contentType; // XML / JSON

    // =========================
    // PROCESSING STATUS
    // =========================

    @Column(name = "processing_status", length = 30)
    private String processingStatus; // SUCCESS / FAILED

    @Column(name = "processing_step", length = 50)
    private String processingStep; // PARSING / ROUTING / SENDING

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // =========================
    // PAYLOADS
    // =========================

    @Lob
    @Column(name = "raw_payload", columnDefinition = "LONGTEXT")
    private String rawPayload;

    @Lob
    @Column(name = "transformed_payload", columnDefinition = "LONGTEXT")
    private String transformedPayload;

    @Lob
    @Column(name = "ack_payload", columnDefinition = "LONGTEXT")
    private String ackPayload;

    // =========================
    // TIMING
    // =========================

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "routed_at")
    private LocalDateTime routedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "ack_at")
    private LocalDateTime ackAt;

    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    // =========================
    // AUDIT
    // =========================

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
