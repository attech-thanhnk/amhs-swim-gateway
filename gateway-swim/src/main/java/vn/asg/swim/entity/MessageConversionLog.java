package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * MessageConversionLog entity — Traffic log recorded after each message
 * conversion.
 */
@Entity
@Table(name = "message_conversion_log")
public class MessageConversionLog {

    public MessageConversionLog() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "date", length = 8)
    private String date;

    /** AMHS / SWIM */
    @Column(name = "type", length = 4)
    private String type;

    /** IN / OUT */
    @Column(name = "category", length = 12)
    private String category;

    @Column(name = "message_id", length = 256)
    private String messageId;

    @Column(name = "ipm_id", length = 256)
    private String ipmId;

    /** EUR Doc 047 §4.4.3.4e - MTS Identifier (G-13) */
    @Column(name = "mts_id", length = 256)
    private String mtsId;

    @Column(name = "amqp_message_id", length = 256)
    private String amqpMessageId;

    @Column(name = "priority", length = 12)
    private String priority;

    @Column(name = "ohi", length = 255)
    private String ohi;

    @Column(name = "origin", length = 128)
    private String origin;

    @Column(name = "filing_time", length = 20)
    private String filingTime;

    @Column(name = "subject", length = 512)
    private String subject;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "converted_time")
    private LocalDateTime convertedTime;

    /** OK / ERROR / REJECT */
    @Column(name = "status", length = 8)
    private String status;

    /** convert-as-amqp / convert-as-ipm / reject */
    @Column(name = "action_taken", length = 255)
    private String actionTaken;

    @Column(name = "rejection_source", length = 20)
    private String rejectionSource;

    @Column(name = "rejection_reason", length = 64)
    private String rejectionReason;

    @Column(name = "rejection_diagnostic", columnDefinition = "TEXT")
    private String rejectionDiagnostic;

    @Column(name = "supplementary_info", length = 512)
    private String supplementaryInfo;

    @Column(name = "remark", length = 1000)
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getIpmId() {
        return ipmId;
    }

    public void setIpmId(String ipmId) {
        this.ipmId = ipmId;
    }

    public String getMtsId() {
        return mtsId;
    }

    public void setMtsId(String mtsId) {
        this.mtsId = mtsId;
    }

    public String getAmqpMessageId() {
        return amqpMessageId;
    }

    public void setAmqpMessageId(String amqpMessageId) {
        this.amqpMessageId = amqpMessageId;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getOhi() {
        return ohi;
    }

    public void setOhi(String ohi) {
        this.ohi = ohi;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getFilingTime() {
        return filingTime;
    }

    public void setFilingTime(String filingTime) {
        this.filingTime = filingTime;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getConvertedTime() {
        return convertedTime;
    }

    public void setConvertedTime(LocalDateTime convertedTime) {
        this.convertedTime = convertedTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }



    public String getSupplementaryInfo() {
        return supplementaryInfo;
    }

    public void setSupplementaryInfo(String supplementaryInfo) {
        this.supplementaryInfo = supplementaryInfo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getRejectionSource() {
        return rejectionSource;
    }

    public void setRejectionSource(String rejectionSource) {
        this.rejectionSource = rejectionSource;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
        if (rejectionReason != null && this.rejectionSource == null) {
            this.rejectionSource = "SWIM";
        }
    }

    public String getRejectionDiagnostic() {
        return rejectionDiagnostic;
    }

    public void setRejectionDiagnostic(String rejectionDiagnostic) {
        this.rejectionDiagnostic = rejectionDiagnostic;
    }
}
