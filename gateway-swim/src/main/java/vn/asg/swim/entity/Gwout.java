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

    /** X.400 message-id (MTS-Identifier) */
    @Column(name = "amhsid", length = 200)
    private String amhsid;

    /** X.400 IPM-Identifier */
    @Column(name = "ipm_id", length = 200)
    private String ipmId;

    /** AMHS Priority: 'SS', 'DD', 'FF', 'GG', 'KK' */
    @Column(name = "amhs_priority", length = 10)
    private String amhsPriority;

    /** SWIM Priority: 2, 3, 4, 6, 7 or 6, 7, 8 */
    @Column(name = "swim_priority")
    private Integer swimPriority;

    /** Received time from AMHS Component */
    @Column(name = "time")
    private LocalDateTime time;

    /**
     * AMHS Filing Time (DDhhmm).
     */
    @Column(name = "filing_time", length = 32)
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

    /**
     * Danh sách địa chỉ AFTN người nhận, phân cách dấu phẩy.
     */
    @Column(name = "address", columnDefinition = "MEDIUMTEXT")
    private String address;

    /** X.400 Optional Heading Information (OHI) / originators-reference */
    @Column(name = "optional_heading", length = 255)
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

    /**
     * Phần tử user-visible-string của FTBP.
     */
    @Column(name = "amhs_user_visible_string", length = 512)
    private String amhsUserVisibleString;

    /** 0 = delivery report not requested, 1 = requested */
    @Column(name = "amhs_delivery_report")
    private Boolean amhsDeliveryReport = false;

    /** Content-Type. Example: text/plain, application/xml */
    @Column(name = "content_type", length = 128)
    private String contentType;

    /**
     * Overall Status:
     * 0=PENDING, 2=TRANSFORMED, 4=PUBLISHED, 5=FAILED, 6=RESOLVED, 7=CANCELLED
     */
    @Column(name = "status")
    private Integer status = OutboundStatus.PENDING.getValue();

    /**
     * Current encoded-information-types của IPM gốc (mtcu_tmp.originEncodeInformationType).
     */
    @Column(name = "origin_eit", length = 255)
    private String originEit;

    /**
     * Content-type abstract-value lấy từ Message Transfer Envelope (mtcu_tmp.contentType).
     */
    @Column(name = "x400_content_type")
    private Integer x400ContentType;

    /**
     * Số body part của IPM gốc (mtcu_tmp.numberOfAttachment).
     */
    @Column(name = "number_of_attachment")
    private Integer numberOfAttachment;

    /**
     * Precedence cao nhất trong các recipient "responsible" của Extended IPM.
     */
    @Column(name = "precedence")
    private Integer precedence;

    /**
     * Tham số content-length của Probe.
     */
    @Column(name = "content_length")
    private Integer contentLength;

    @Column(name = "body_part_type", length = 50)
    private String bodyPartType;

    /** Repertoire của body part: ITA2 / ISO-646 / ISO-8859-1 / ISO-REG-n */
    @Column(name = "body_part_charset", length = 20)
    private String bodyPartCharset;

    /** FTBP file name */
    @Column(name = "ftbp_file_name", length = 255)
    private String ftbpFileName;

    /** FTBP object size (bytes) */
    @Column(name = "ftbp_object_size", length = 20)
    private String ftbpObjectSize;

    /** FTBP date-and-time-of-last-modification */
    @Column(name = "ftbp_last_mod", length = 20)
    private String ftbpLastMod;

    @Column(name = "rejection_reason", length = 64)
    private String rejectionReason;

    /** NDR diagnostic-code */
    @Column(name = "rejection_diagnostic", length = 64)
    private String rejectionDiagnostic;

    /** Nguon phat sinh loi: SWIM / AMHS */
    @Column(name = "rejection_source", length = 20)
    private String rejectionSource;

    /** AMQP broker-assigned message-id, captured after a successful publish */
    @Column(name = "amqp_message_id", length = 256)
    private String amqpMessageId;

    /** Mirrors the amhs_message_signed AMQP property actually sent (currently always "unsigned") */
    @Column(name = "message_signed", length = 20)
    private String messageSigned;

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

    public String getIpmId() {
        return ipmId;
    }

    public void setIpmId(String ipmId) {
        this.ipmId = ipmId;
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

    public String getAmhsUserVisibleString() {
        return amhsUserVisibleString;
    }

    public void setAmhsUserVisibleString(String amhsUserVisibleString) {
        this.amhsUserVisibleString = amhsUserVisibleString;
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

    public String getOriginEit() {
        return originEit;
    }

    public void setOriginEit(String originEit) {
        this.originEit = originEit;
    }

    public Integer getX400ContentType() {
        return x400ContentType;
    }

    public void setX400ContentType(Integer x400ContentType) {
        this.x400ContentType = x400ContentType;
    }

    public Integer getPrecedence() {
        return precedence;
    }

    public void setPrecedence(Integer precedence) {
        this.precedence = precedence;
    }

    public Integer getContentLength() {
        return contentLength;
    }

    public void setContentLength(Integer contentLength) {
        this.contentLength = contentLength;
    }

    public Integer getNumberOfAttachment() {
        return numberOfAttachment;
    }

    public void setNumberOfAttachment(Integer numberOfAttachment) {
        this.numberOfAttachment = numberOfAttachment;
    }

    public String getBodyPartType() {
        return bodyPartType;
    }

    public void setBodyPartType(String bodyPartType) {
        this.bodyPartType = bodyPartType;
    }

    public String getBodyPartCharset() {
        return bodyPartCharset;
    }

    public void setBodyPartCharset(String bodyPartCharset) {
        this.bodyPartCharset = bodyPartCharset;
    }

    public String getFtbpFileName() {
        return ftbpFileName;
    }

    public void setFtbpFileName(String ftbpFileName) {
        this.ftbpFileName = ftbpFileName;
    }

    public String getFtbpObjectSize() {
        return ftbpObjectSize;
    }

    public void setFtbpObjectSize(String ftbpObjectSize) {
        this.ftbpObjectSize = ftbpObjectSize;
    }

    public String getFtbpLastMod() {
        return ftbpLastMod;
    }

    public void setFtbpLastMod(String ftbpLastMod) {
        this.ftbpLastMod = ftbpLastMod;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
        if (rejectionReason != null && this.rejectionSource == null) {
            this.rejectionSource = "AMHS";
        }
    }

    public String getRejectionDiagnostic() {
        return rejectionDiagnostic;
    }

    public void setRejectionDiagnostic(String rejectionDiagnostic) {
        this.rejectionDiagnostic = rejectionDiagnostic;
    }

    public String getAmqpMessageId() {
        return amqpMessageId;
    }

    public void setAmqpMessageId(String amqpMessageId) {
        this.amqpMessageId = amqpMessageId;
    }

    public String getMessageSigned() {
        return messageSigned;
    }

    public void setMessageSigned(String messageSigned) {
        this.messageSigned = messageSigned;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getRejectionSource() {
        return rejectionSource;
    }

    public String getErrorSource() {
        return rejectionSource;
    }

    public void setRejectionSource(String rejectionSource) {
        this.rejectionSource = rejectionSource;
    }

    public void setErrorSource(String errorSource) {
        this.rejectionSource = errorSource;
    }
}