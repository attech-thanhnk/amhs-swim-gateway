package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Routing Table — Defines Simple Routing Rules.
 * direction=OUT: AMHS → SWIM, direction=IN: SWIM → AMHS.
 */
@Entity
@Table(name = "routing")
public class Routing {

    public Routing() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "direction", length = 3, nullable = false)
    private String direction; // 'IN' or 'OUT'

    // ========== INBOUND DIRECTION (SWIM → AMHS) ==========
    @Column(name = "receive_topic", length = 100)
    private String receiveTopic;

    @Column(name = "recipients", length = 500)
    private String recipients;

    @Column(name = "originator", length = 8)
    private String originator;

    // ========== OUTBOUND DIRECTION (AMHS → SWIM) ==========
    @Column(name = "message_type", length = 50)
    private String messageType;

    /** Mẫu nhận diện loại bản tin từ nội dung thô (prefix). Ví dụ: "METAR ", "(FPL-" */
    @Column(name = "detect_pattern", length = 255)
    private String detectPattern;

    @Column(name = "send_topic", length = 100)
    private String sendTopic;

    // ========== COMMON PROPERTIES ==========
    @Column(name = "priority")
    private Integer priority = 100;

    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getReceiveTopic() {
        return receiveTopic;
    }

    public void setReceiveTopic(String receiveTopic) {
        this.receiveTopic = receiveTopic;
    }

    public String getRecipients() {
        return recipients;
    }

    public void setRecipients(String recipients) {
        this.recipients = recipients;
    }

    public String getOriginator() {
        return originator;
    }

    public void setOriginator(String originator) {
        this.originator = originator;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getDetectPattern() {
        return detectPattern;
    }

    public void setDetectPattern(String detectPattern) {
        this.detectPattern = detectPattern;
    }

    public String getSendTopic() {
        return sendTopic;
    }

    public void setSendTopic(String sendTopic) {
        this.sendTopic = sendTopic;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
