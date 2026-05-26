package vn.asg.cp.entity;

import jakarta.persistence.*;

/**
 * Bảng message_type_registry — Danh mục các loại điện văn ATS.
 */
@Entity
@Table(name = "message_type_registry")
public class MessageTypeRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_type", length = 50, nullable = false, unique = true)
    private String messageType;

    @Column(name = "detect_pattern", length = 255, nullable = false)
    private String detectPattern;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "note", length = 500)
    private String note;

    public MessageTypeRegistry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getDetectPattern() { return detectPattern; }
    public void setDetectPattern(String detectPattern) { this.detectPattern = detectPattern; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
