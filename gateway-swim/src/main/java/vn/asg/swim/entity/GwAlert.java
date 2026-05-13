package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Gateway Alerts table (gw_alert) — Critical alerts displayed on the Control
 * Position for operator intervention.
 */
@Entity
@Table(name = "gw_alert")
public class GwAlert {

    public GwAlert() {}

    // Alert type constants
    public static final String TYPE_CONNECTION_LOST = "CONNECTION_LOST";
    public static final String TYPE_MESSAGE_DEAD = "MESSAGE_DEAD";
    public static final String TYPE_QUEUE_BACKLOG = "QUEUE_BACKLOG";
    public static final String TYPE_CONVERT_ERROR = "CONVERT_ERROR";
    public static final String TYPE_ROUTING_ERROR = "ROUTING_ERROR";
    public static final String TYPE_VALIDATION_ERROR = "VALIDATION_ERROR";

    // Severity constants
    public static final String SEV_INFO = "INFO";
    public static final String SEV_WARNING = "WARNING";
    public static final String SEV_ERROR = "ERROR";
    public static final String SEV_CRITICAL = "CRITICAL";

    // Status constants
    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String STATUS_RESOLVED = "RESOLVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Alert type.
     * Examples: CONNECTION_LOST, MESSAGE_DEAD, QUEUE_BACKLOG, CONVERT_ERROR,
     * ROUTING_ERROR
     */
    @Column(name = "alert_type", length = 30, nullable = false)
    private String alertType;

    /**
     * Severity level: INFO, WARNING, ERROR, CRITICAL
     */
    @Column(name = "severity", length = 10, nullable = false)
    private String severity;

    /**
     * Alert message content. Example: "Connection to LOCAL_BROKER lost after 3
     * retries"
     */
    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    /** Related table for operator drill-down. Example: "gwout_dispatch" */
    @Column(name = "ref_table", length = 50)
    private String refTable;

    /** Related record ID in ref_table */
    @Column(name = "ref_id")
    private Long refId;

    /**
     * Alert processing status: NEW → ACKNOWLEDGED → RESOLVED
     */
    @Column(name = "status", length = 20, nullable = false)
    private String status = STATUS_NEW;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Time when the operator acknowledged the alert */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** Operator ID who acknowledged the alert. Example: "operator01" */
    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    /** Time when the alert was resolved */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRefTable() { return refTable; }
    public void setRefTable(String refTable) { this.refTable = refTable; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
