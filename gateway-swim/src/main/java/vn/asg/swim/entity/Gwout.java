package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Gwout entity — Messages received from the AMHS Component, waiting for the
 * SWIM Component to publish to AMQP.
 * The overall status reflects the status of all child gwout_dispatch records.
 */
@Entity
@Table(name = "gwout")
public class Gwout {

    public Gwout() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long msgid;

    /** X.400 message-id */
    @Column(name = "amhsid", length = 200)
    private String amhsid;

    /** AMHS Priority: 'SS', 'DD', 'FF', 'GG', 'KK' */
    @Column(name = "amhs_priority", length = 10)
    private String amhsPriority;

    /** SWIM Priority: 2, 3, 4, 6, 7 or 6, 7, 8 */
    @Column(name = "swim_priority")
    private Integer swimPriority;

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
    @Column(name = "origin", length = 200)
    private String origin;

    /** AMHS Recipients list (space-separated) */
    @Column(name = "address", length = 1000)
    private String address;

    /** X.400 Optional Heading Information (OHI) */
    @Column(name = "optional_heading", length = 60)
    private String optionalHeading;

    @Column(name = "subject", length = 200)
    private String subject;

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
    private Integer status = MessageStatus.OUT_PENDING.getValue();

    /** Converted JSON/TEXT content */
    @Column(name = "payload_content", columnDefinition = "MEDIUMTEXT")
    private String payloadContent;

    @Column(name = "body_part_type", length = 50)
    private String bodyPartType;

    @Column(name = "rejection_reason", length = 64)
    private String rejectionReason;

    @Column(name = "error_type")
    private Integer errorType = ErrorType.UNDEFINED.getValue();

    public Long getMsgid() {
        return msgid;
    }

    public void setMsgid(Long msgid) {
        this.msgid = msgid;
    }

    public String getAmhsid() {
        return amhsid;
    }

    public void setAmhsid(String amhsid) {
        this.amhsid = amhsid;
    }


    public String getAmhsPriority() {
        return amhsPriority;
    }

    public void setAmhsPriority(String amhsPriority) {
        this.amhsPriority = amhsPriority;
    }

    public Integer getSwimPriority() {
        return swimPriority;
    }

    public void setSwimPriority(Integer swimPriority) {
        this.swimPriority = swimPriority;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public String getFilingTime() {
        return filingTime;
    }

    public void setFilingTime(String filingTime) {
        this.filingTime = filingTime;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getBodyType() {
        return bodyType;
    }

    public void setBodyType(String bodyType) {
        this.bodyType = bodyType;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getOptionalHeading() {
        return optionalHeading;
    }

    public void setOptionalHeading(String optionalHeading) {
        this.optionalHeading = optionalHeading;
    }

    public LocalDateTime getAmhsTtl() {
        return amhsTtl;
    }

    public void setAmhsTtl(LocalDateTime amhsTtl) {
        this.amhsTtl = amhsTtl;
    }

    public String getAmhsRegisteredId() {
        return amhsRegisteredId;
    }

    public void setAmhsRegisteredId(String amhsRegisteredId) {
        this.amhsRegisteredId = amhsRegisteredId;
    }

    public Boolean getAmhsDeliveryReport() {
        return amhsDeliveryReport;
    }

    public void setAmhsDeliveryReport(Boolean amhsDeliveryReport) {
        this.amhsDeliveryReport = amhsDeliveryReport;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getPayloadContent() {
        return payloadContent;
    }

    public void setPayloadContent(String payloadContent) {
        this.payloadContent = payloadContent;
    }

    public Integer getErrorType() {
        return errorType;
    }

    public void setErrorType(Integer errorType) {
        this.errorType = errorType;
    }

    public String getBodyPartType() {
        return bodyPartType;
    }

    public void setBodyPartType(String bodyPartType) {
        this.bodyPartType = bodyPartType;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
