package vn.asg.swim.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MessageTypeRegistry entity — List of ATS message types, identification
 * patterns, and geographic scope logic.
 * This relatively static data is cached within the MessageDetectService.
 */
@Entity
@Table(name = "message_type_registry")
@Data
@NoArgsConstructor
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

    /** Parser complexity: easy / medium / hard */
    @Column(name = "difficulty", length = 10, nullable = false)
    private String difficulty;

    /** Implementation Phase: 1=High Priority, 2=Medium, 3=Low */
    @Column(name = "phase", nullable = false)
    private Byte phase = 1;

    /** 0=Disabled, 1=Enabled */
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /** Technical notes. Example: "For TAF AMD, scope is at word[3]" */
    @Column(name = "note", length = 500)
    private String note;

}
