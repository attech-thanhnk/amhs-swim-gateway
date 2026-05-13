package vn.asg.cp.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Routing Table — Defines Simple Routing Rules.
 * direction=OUT: AMHS → SWIM, direction=IN: SWIM → AMHS.
 */
@Entity
@Table(name = "routing")
public class Routing {

    public Routing() {}

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Schema(description = "Auto-generated ID", accessMode = Schema.AccessMode.READ_ONLY, example = "1")
        private Integer id;

        @Column(name = "direction", length = 3, nullable = false)
        @Schema(description = "Direction: IN (SWIM→AMHS) or OUT (AMHS→SWIM)", example = "OUT", allowableValues = { "IN",
                        "OUT" }, required = true)
        private String direction; // 'IN' or 'OUT'

        // ========== INBOUND DIRECTION (SWIM → AMHS) ==========
        @Column(name = "receive_topic", length = 100)
        @Schema(description = "AMQP topic to subscribe (for IN direction only)", example = "ats/met/metar")
        private String receiveTopic;

        @Column(name = "message_filter", length = 100)
        @Schema(description = "Optional content filter (for IN direction)", example = "METAR")
        private String messageFilter;

        @Column(name = "recipients", length = 500)
        @Schema(description = "Space-separated AFTN addresses (for IN direction)", example = "VVHHZTZX VVTSZDYX")
        private String recipients;

        @Column(name = "originator", length = 8)
        @Schema(description = "AFTN originator address (for IN direction)", example = "VVHHZQZX")
        private String originator;

        // ========== OUTBOUND DIRECTION (AMHS → SWIM) ==========
        @Column(name = "message_type", length = 50)
        @Schema(description = "Message type to detect (for OUT direction only)", example = "METAR")
        private String messageType;

        @Column(name = "send_topic", length = 100)
        @Schema(description = "AMQP topic to publish (for OUT direction only)", example = "ats/met/metar")
        private String sendTopic;

        // ========== COMMON PROPERTIES ==========
        @Column(name = "priority")
        @Schema(description = "Priority (0-255, lower = higher priority)", example = "100")
        private Integer priority = 100;

        @Column(name = "active")
        @Schema(description = "Enable/disable this rule", example = "true")
        private Boolean active = true;

        @Column(name = "convert_to_json")
        @Schema(description = "Output format choice: true=JSON, false=TAC Forward", example = "true")
        private Boolean convertToJson = true;

        @Column(name = "note", columnDefinition = "TEXT")
        @Schema(description = "Optional note/description", example = "METAR routing to SWIM")
        private String note;

        @Column(name = "created_at", nullable = false, updatable = false)
        @Schema(description = "Creation timestamp", accessMode = Schema.AccessMode.READ_ONLY)
        private LocalDateTime createdAt;

        @Column(name = "updated_at", nullable = false)
        @Schema(description = "Last update timestamp", accessMode = Schema.AccessMode.READ_ONLY)
        private LocalDateTime updatedAt;

        @Column(name = "created_by", length = 50)
        @Schema(description = "Created by username", accessMode = Schema.AccessMode.READ_ONLY)
        private String createdBy;

        @PrePersist
        public void prePersist() {
                createdAt = LocalDateTime.now();
                updatedAt = LocalDateTime.now();
        }

        @PreUpdate
        public void preUpdate() {
                updatedAt = LocalDateTime.now();
        }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getReceiveTopic() { return receiveTopic; }
    public void setReceiveTopic(String receiveTopic) { this.receiveTopic = receiveTopic; }
    public String getMessageFilter() { return messageFilter; }
    public void setMessageFilter(String messageFilter) { this.messageFilter = messageFilter; }
    public String getRecipients() { return recipients; }
    public void setRecipients(String recipients) { this.recipients = recipients; }
    public String getOriginator() { return originator; }
    public void setOriginator(String originator) { this.originator = originator; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getSendTopic() { return sendTopic; }
    public void setSendTopic(String sendTopic) { this.sendTopic = sendTopic; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Boolean getConvertToJson() { return convertToJson; }
    public void setConvertToJson(Boolean convertToJson) { this.convertToJson = convertToJson; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
