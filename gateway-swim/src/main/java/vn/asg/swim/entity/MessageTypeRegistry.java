package vn.asg.swim.entity;

import jakarta.persistence.*;

/**
 * MessageTypeRegistry entity — List of ATS message types, identification
 * patterns, and active status.
 */
@Entity
@Table(name = "message_type_registry")
public class MessageTypeRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Message type name, used as a global system key.
     * Examples: METAR, FPL, NOTAM, TAF, SIGMET, UNKNOWN
     */
    @Column(name = "message_type", length = 50, nullable = false, unique = true)
    private String messageType;

    /**
     * Identification pattern (prefix) found in the message body.
     * Examples: "METAR " (starts with "METAR "), "(FPL-" (Flight Plan)
     */
    @Column(name = "detect_pattern", length = 255, nullable = false)
    private String detectPattern;

    /** 0=Disabled, 1=Enabled */
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /** Technical notes. */
    @Column(name = "note", length = 500)
    private String note;

    public MessageTypeRegistry() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
