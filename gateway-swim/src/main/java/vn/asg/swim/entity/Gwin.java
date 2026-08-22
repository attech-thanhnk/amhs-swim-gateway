package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Gwin Table — Represents INBOUND messages received from SWIM AMQP, waiting to
 * be sent to the AMHS MTA.
 */
@Entity
@Table(name = "gwin")
public class Gwin {

    public Gwin() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long msgid;

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
    @Column(name = "subject", length = 128)
    private String subject;

    /**
     * All AMQP application properties in JSON format.
     */
    @Column(name = "amqp_properties", columnDefinition = "TEXT")
    private String amqpProperties;

    /** 0=Flash, 1=Urgent, 2=Normal, 3=Low */
    @Column(name = "priority")
    private Byte priority = 2;

    /** Recipients: comma separated AMHS ICAO */
    @Column(name = "amhsRecipients", length = 200)
    private String amhsRecipients;

    /** Arrival timestamp from AMQP broker */
    @Column(name = "time")
    private LocalDateTime time;

    /** Original JSON/TEXT payload received from SWIM */
    @Column(name = "payload_content", columnDefinition = "MEDIUMTEXT")
    private String payloadContent;

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
    @Column(name = "address", length = 1000)
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
     * Mức dịch vụ ATSMHS đã phân giải cho bản tin này (BASIC / EXTENDED).
     * EUR Doc 047 §3.3.3 + §4.5.2.10.1/§4.5.3.7-9: quyết định thành phần dựng IPM map
     * amhs_ats_ft và amhs_ats_ohi vào ATS-message-Filing-Time/Optional-Heading-Info (basic)
     * hay authorization-time/originators-reference (extended).
     */
    @Column(name = "atsmhs_service_level", length = 20)
    private String atsmhsServiceLevel;

    /**
     * Global Status:
     * 0=PENDING, 4=FAILED, 5=UNROUTED, 6=RESOLVED, 7=CANCELLED
     */
    @Column(name = "status")
    private Integer status = InboundStatus.PENDING.getValue();

    public Long getMsgid() { return msgid; }
    public void setMsgid(Long msgid) { this.msgid = msgid; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getAmqpProperties() { return amqpProperties; }
    public void setAmqpProperties(String amqpProperties) { this.amqpProperties = amqpProperties; }
    public Byte getPriority() { return priority; }
    public void setPriority(Byte priority) { this.priority = priority; }
    public String getAmhsRecipients() { return amhsRecipients; }
    public void setAmhsRecipients(String amhsRecipients) { this.amhsRecipients = amhsRecipients; }
    public LocalDateTime getTime() { return time; }
    public void setTime(LocalDateTime time) { this.time = time; }
    public String getPayloadContent() { return payloadContent; }
    public void setPayloadContent(String payloadContent) { this.payloadContent = payloadContent; }
    public String getBodyType() { return bodyType; }
    public void setBodyType(String bodyType) { this.bodyType = bodyType; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getAddressingSource() { return addressingSource; }
    public void setAddressingSource(String addressingSource) { this.addressingSource = addressingSource; }
    public String getAtsmhsServiceLevel() { return atsmhsServiceLevel; }
    public void setAtsmhsServiceLevel(String atsmhsServiceLevel) { this.atsmhsServiceLevel = atsmhsServiceLevel; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}