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

    /**
     * IPM-Identifier của IPM do ITCU dựng ra cho bản tin này.
     * <p>
     * Lấy từ application property amhs_ipm_id của bản tin AMQP. Đây là khoá để đối
     * chiếu RN/NRN bay ngược về (mtcu_ipn.subject_ipm_id).
     */
    @Column(name = "ipm_id", length = 255)
    private String ipmId;   

    /**
     * MTS-Identifier do MTA cấp lúc amss submit bản tin ra X.400.
     * <p>
     * Chỉ amss biết giá trị này nên amss phải ghi ngược vào đây. Là khoá đối chiếu chính của
     * NDR tham chiếu bản tin gốc qua MTS-Identifier.
     */
    @Column(name = "mts_id", length = 255)
    private String mtsId;

    /**
     * ATS-message-priority của bản tin, suy từ AMQP priority.
     * Cần giá trị này để quyết định chấp nhận hay từ chối RN.
     */
    @Column(name = "ats_priority", length = 2)
    private String atsPriority;

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
    @Column(name = "amhsRecipients", columnDefinition = "TEXT")
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
    @Column(name = "address", columnDefinition = "TEXT")
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
     * EUR Doc 047 §3.3.3 + §4.5.2.10.1/§4.5.3.7-9.
     */
    @Column(name = "atsmhs_service_level", length = 20)
    private String atsmhsServiceLevel;

    /**
     * Lý do bị từ chối theo ICAO Doc 047 (validation-failed, atsmhs-validation-failed, unauthorized...).
     */
    @Column(name = "rejection_reason", length = 64)
    private String rejectionReason;

    /**
     * Chẩn đoán chi tiết lỗi từ chối theo ICAO Doc 047 (Invalid priority: 10, Missing messageId...).
     */
    @Column(name = "rejection_diagnostic", length = 500)
    private String rejectionDiagnostic;

    /** Nguon phat sinh loi: SWIM / AMHS */
    @Column(name = "rejection_source", length = 20)
    private String rejectionSource;

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
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; if (rejectionReason != null && this.rejectionSource == null) { this.rejectionSource = "SWIM"; } }
    public String getRejectionDiagnostic() { return rejectionDiagnostic; }
    public void setRejectionDiagnostic(String rejectionDiagnostic) { this.rejectionDiagnostic = rejectionDiagnostic; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getIpmId() { return ipmId; }
    public void setIpmId(String ipmId) { this.ipmId = ipmId; }
    public String getMtsId() { return mtsId; }
    public void setMtsId(String mtsId) { this.mtsId = mtsId; }
    public String getAtsPriority() { return atsPriority; }
    public void setAtsPriority(String atsPriority) { this.atsPriority = atsPriority; }
    public String getRejectionSource() { return rejectionSource; }
    public String getErrorSource() { return rejectionSource; }
    public void setRejectionSource(String rejectionSource) { this.rejectionSource = rejectionSource; }
    public void setErrorSource(String errorSource) { this.rejectionSource = errorSource; }
}