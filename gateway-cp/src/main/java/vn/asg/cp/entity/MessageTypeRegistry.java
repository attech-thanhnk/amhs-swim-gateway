package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MessageTypeRegistry Table — Registry of document types (METAR, FPL, etc.),
 * detection patterns, and implementation metadata.
 * Data is static and cached in MessageDetectService.
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
     * Message type name, used as a key across the system.
     * Examples: METAR, FPL, NOTAM, TAF, SIGMET, UNKNOWN
     */
    @Column(name = "message_type", length = 50, nullable = false, unique = true)
    private String messageType;

    /**
     * Start of body pattern for identification.
     * Example: "METAR " -> body starts with "METAR ", "(FPL-" -> flight plan
     */
    @Column(name = "detect_pattern", length = 255, nullable = false)
    private String detectPattern;

    /** Parser complexity: easy / medium / hard */
    @Column(name = "difficulty", length = 10, nullable = false)
    private String difficulty;

    /** Implementation phase: 1=high priority, 2=medium, 3=low */
    @Column(name = "phase", nullable = false)
    private Byte phase = 1;

    /** Status: 0=Disabled, 1=Enabled */
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /** Technical notes. Example: "TAF AMD has scope at word[3]" */
    @Column(name = "note", length = 500)
    private String note;

}
