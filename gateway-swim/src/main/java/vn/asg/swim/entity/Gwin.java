package vn.asg.swim.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Gwin Table — Represents INBOUND messages received from SWIM AMQP, waiting to
 * be sent to the AMHS MTA.
 * One Gwin record maps to multiple GwinDispatch records (one per recipient).
 */
@Entity
@Table(name = "gwin")
@Data
@NoArgsConstructor
public class Gwin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long msgid;

    /** AMHS CPA Character. N=Normal, U=Urgent */
    @Column(name = "cpa", length = 1)
    private String cpa = "N";

    /**
     * AMQP message-id to prevent duplicate processing. Example:
     * nm-b2b-fum-20240101-12345
     */
    @Column(name = "message_id", length = 255, unique = true)
    private String messageId;

    /** AMQP Source Topic. Example: ats.met.metar */
    @Column(name = "source", length = 200)
    private String source;

    /** AMQP Subject Header. Example: FUM, DPI, METAR */
    @Column(name = "subject", length = 100)
    private String subject;

    /**
     * All AMQP application properties in JSON format.
     */
    @Column(name = "amqp_properties", columnDefinition = "TEXT")
    private String amqpProperties;

    /** 0=Flash, 1=Urgent, 2=Normal, 3=Low */
    @Column(name = "priority")
    private Byte priority = 2;

    /** Arrival timestamp from AMQP broker */
    @Column(name = "time")
    private LocalDateTime time;

    /** Original JSON/TEXT payload received from SWIM */
    @Column(name = "payload_content", columnDefinition = "MEDIUMTEXT")
    private String payloadContent;

    /** Converted plain text payload for AMHS */
    @Column(name = "TEXT", columnDefinition = "MEDIUMTEXT")
    private String text;

    /** Body type: text or ftbp */
    @Column(name = "body_type", length = 10)
    private String bodyType = "text";

    /** AMQP Content-Type. Example: application/xml, text/plain */
    @Column(name = "content_type", length = 128)
    private String contentType;

    /** Resolved AMHS Originator address. Example: VVHHZPZX */
    @Column(name = "origin", length = 200)
    private String origin;

    /** Resolved AMHS Recipient addresses. Example: VVTSZTZX VVHHZPZX */
    @Column(name = "address", length = 250)
    private String address;

    /**
     * Source of addressing resolution mechanism.
     * Values:
     * - AMQP_PROPERTY: Resolved explicitly via AMQP headers.
     * - ROUTING_RULE: Resolved via strict exact-match simple routing table rule.
     * - UNRESOLVED: Failed to route, remains dead.
     * - MANUAL_ROUTE: Manually routed by Operator.
     */
    @Column(name = "addressing_source", length = 200)
    private String addressingSource;

    /**
     * Global Status.
     * 0=PENDING, 1=PROCESSING, 2=SENT, 3=FAILED, 4=DEAD, 5=UNROUTED
     */
    @Column(name = "status")
    private Integer status = STATUS_PENDING;

    // Status constants
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_SENT = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_DEAD = 4;
    public static final int STATUS_UNROUTED = 5;
}
