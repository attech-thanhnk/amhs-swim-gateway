package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * GwoutHistory Table — Represents archived gwout records.
 */
@Entity
@Table(name = "gwout_history")
public class GwoutHistory {

    public GwoutHistory() {
    }

    @Id
    private Long msgid;

    @Column(name = "amhsid", length = 200)
    private String amhsid;

    @Column(name = "ipm_id", length = 200)
    private String ipmId;

    @Column(name = "amhs_priority", length = 10)
    private String amhsPriority;

    @Column(name = "swim_priority")
    private Integer swimPriority;

    @Column(name = "time")
    private LocalDateTime time;

    @Column(name = "filing_time", length = 6)
    private String filingTime;

    @Column(name = "TEXT", columnDefinition = "MEDIUMTEXT")
    private String text;

    @Column(name = "body_type", length = 10)
    private String bodyType = "text";

    @Column(name = "origin", length = 200)
    private String origin;

    @Column(name = "address", length = 1000)
    private String address;

    @Column(name = "optional_heading", length = 60)
    private String optionalHeading;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "amhs_ttl")
    private LocalDateTime amhsTtl;

    @Column(name = "amhs_registered_id", length = 200)
    private String amhsRegisteredId;

    @Column(name = "amhs_delivery_report")
    private Boolean amhsDeliveryReport = false;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "status")
    private Integer status = MessageStatus.OUT_PENDING.getValue();

    @Column(name = "payload_content", columnDefinition = "MEDIUMTEXT")
    private String payloadContent;

    @Column(name = "body_part_type", length = 50)
    private String bodyPartType;

    @Column(name = "body_part_charset", length = 20)
    private String bodyPartCharset;

    @Column(name = "ftbp_file_name", length = 255)
    private String ftbpFileName;

    @Column(name = "ftbp_object_size", length = 20)
    private String ftbpObjectSize;

    @Column(name = "ftbp_last_mod", length = 20)
    private String ftbpLastMod;

    @Column(name = "rejection_reason", length = 64)
    private String rejectionReason;

    @Column(name = "error_type")
    private Integer errorType = ErrorType.UNDEFINED.getValue();

    public Long getMsgid() { return msgid; }
    public void setMsgid(Long msgid) { this.msgid = msgid; }
    public String getAmhsid() { return amhsid; }
    public void setAmhsid(String amhsid) { this.amhsid = amhsid; }
    public String getIpmId() { return ipmId; }
    public void setIpmId(String ipmId) { this.ipmId = ipmId; }
    public String getAmhsPriority() { return amhsPriority; }
    public void setAmhsPriority(String amhsPriority) { this.amhsPriority = amhsPriority; }
    public Integer getSwimPriority() { return swimPriority; }
    public void setSwimPriority(Integer swimPriority) { this.swimPriority = swimPriority; }
    public LocalDateTime getTime() { return time; }
    public void setTime(LocalDateTime time) { this.time = time; }
    public String getFilingTime() { return filingTime; }
    public void setFilingTime(String filingTime) { this.filingTime = filingTime; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getBodyType() { return bodyType; }
    public void setBodyType(String bodyType) { this.bodyType = bodyType; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getOptionalHeading() { return optionalHeading; }
    public void setOptionalHeading(String optionalHeading) { this.optionalHeading = optionalHeading; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public LocalDateTime getAmhsTtl() { return amhsTtl; }
    public void setAmhsTtl(LocalDateTime amhsTtl) { this.amhsTtl = amhsTtl; }
    public String getAmhsRegisteredId() { return amhsRegisteredId; }
    public void setAmhsRegisteredId(String amhsRegisteredId) { this.amhsRegisteredId = amhsRegisteredId; }
    public Boolean getAmhsDeliveryReport() { return amhsDeliveryReport; }
    public void setAmhsDeliveryReport(Boolean amhsDeliveryReport) { this.amhsDeliveryReport = amhsDeliveryReport; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getPayloadContent() { return payloadContent; }
    public void setPayloadContent(String payloadContent) { this.payloadContent = payloadContent; }
    public String getBodyPartType() { return bodyPartType; }
    public void setBodyPartType(String bodyPartType) { this.bodyPartType = bodyPartType; }
    public String getBodyPartCharset() { return bodyPartCharset; }
    public void setBodyPartCharset(String bodyPartCharset) { this.bodyPartCharset = bodyPartCharset; }
    public String getFtbpFileName() { return ftbpFileName; }
    public void setFtbpFileName(String ftbpFileName) { this.ftbpFileName = ftbpFileName; }
    public String getFtbpObjectSize() { return ftbpObjectSize; }
    public void setFtbpObjectSize(String ftbpObjectSize) { this.ftbpObjectSize = ftbpObjectSize; }
    public String getFtbpLastMod() { return ftbpLastMod; }
    public void setFtbpLastMod(String ftbpLastMod) { this.ftbpLastMod = ftbpLastMod; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Integer getErrorType() { return errorType; }
    public void setErrorType(Integer errorType) { this.errorType = errorType; }
}
