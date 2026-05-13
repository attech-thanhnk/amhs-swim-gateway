package vn.asg.swim.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Gwout entity — Messages received from the AMHS Component, waiting for the
 * SWIM Component to publish to AMQP.
 * The overall status reflects the status of all child gwout_dispatch records.
 */
@Entity
@Table(name = "gwout")
@Data
@NoArgsConstructor
public class Gwout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long msgid;

    /** X.400 message-id */
    @Column(name = "amhsid", length = 200)
    private String amhsid;

    /** AMHS Priority: 0=Flash, 1=Urgent, 2=Normal, 3=Low */
    @Column(name = "priority")
    private Integer priority;

    /** Received time from AMHS Component */
    @Column(name = "time")
    private LocalDateTime time;

    /** AMHS Filing Time (DDhhmm format) */
    @Column(name = "filing_time", length = 6)
    private String filingTime;

    /** Plain text message content */
    @Column(name = "TEXT", columnDefinition = "MEDIUMTEXT")
    private String text;

    /** Body type: text or ftbp */
    @Column(name = "body_type", length = 10)
    private String bodyType = "text";

    /** AMHS Originator address (8-character AFTN) */
    @Column(name = "origin", length = 8)
    private String origin;

    /** AMHS Recipients list (space-separated) */
    @Column(name = "address", length = 250)
    private String address;

    /** X.400 Optional Heading Information (OHI) */
    @Column(name = "optional_heading", length = 60)
    private String optionalHeading;

    /**
     * Expiry time — message will not be published after this time. NULL = infinite
     */
    @Column(name = "amhs_ttl")
    private LocalDateTime amhsTtl;

    /** X.400 registered identifier */
    @Column(name = "amhs_registered_id", length = 200)
    private String amhsRegisteredId;

    /** 0 = delivery report not requested, 1 = requested */
    @Column(name = "amhs_delivery_report")
    private Boolean amhsDeliveryReport = false;

    /** Content-Type. Example: text/plain, application/xml */
    @Column(name = "content_type", length = 128)
    private String contentType;

    /**
     * Overall Status. Reflects the aggregate status of all child gwout_dispatch
     * records.
     * 0=PENDING, 1=PROCESSING, 2=SENT, 3=DEAD
     */
    @Column(name = "status")
    private Integer status = STATUS_PENDING;

    /** Converted JSON/TEXT content */
    @Column(name = "payload_content", columnDefinition = "MEDIUMTEXT")
    private String payloadContent;

    // Status constants
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_SENT = 2;
    public static final int STATUS_DEAD = 3;
}
