package vn.asg.swim.model;

/**
 * Một dòng phản hồi AMHS bay ngược về cho điện văn ITCU đã gửi ở chiều SWIM → AMHS.
 * <p>
 * Bốn loại, phân biệt bằng {@code ipnType}:
 * <ul>
 *   <li>{@code RN} / {@code NRN} — thông báo do người nhận tạo ra.</li>
 *   <li>{@code DR} / {@code NDR} — báo cáo do MTA tạo ra.</li>
 * </ul>
 */
public class AmhsFeedback {

    /** Receipt Notification — người nhận đã đọc */
    public static final String TYPE_RN = "RN";

    /** Non-Receipt Notification — điện văn tới nơi nhưng không được đọc */
    public static final String TYPE_NRN = "NRN";

    /** Delivery Report — MTA đã giao tới hòm thư người nhận */
    public static final String TYPE_DR = "DR";

    /** Non-Delivery Report — MTA không giao được */
    public static final String TYPE_NDR = "NDR";

    private Long id;
    private String ipnType;

    /** IPM-Identifier của điện văn gốc — khoá đối chiếu của RN/NRN */
    private String subjectIpm;

    /** MTS-Identifier của điện văn gốc — khoá đối chiếu của DR/NDR */
    private String subjectMts;

    /** Bên phát phản hồi (ipn-originator) */
    private String origin;

    /** Bên mà phản hồi này nói tới (ipm-preferred-recipient, hoặc recipient của report) */
    private String recipient;

    /** receipt-time — chỉ RN */
    private String receiptTime;

    /** non-receipt-reason — chỉ NRN */
    private Integer nonReceiptReason;

    /** discard-reason — chỉ NRN */
    private Integer discardReason;

    /** non-delivery-reason-code — chỉ NDR, ví dụ {@code unable-to-transfer} */
    private String reasonCode;

    /**
     * non-delivery-diagnostic-code — chỉ NDR.
     */
    private String diagnosticCode;

    /** Văn bản bổ sung */
    private String supplementaryInfo;

    private Integer status;

    public boolean isFailure() {
        return TYPE_NDR.equalsIgnoreCase(ipnType) || TYPE_NRN.equalsIgnoreCase(ipnType);
    }

    public boolean isReport() {
        return TYPE_DR.equalsIgnoreCase(ipnType) || TYPE_NDR.equalsIgnoreCase(ipnType);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIpnType() { return ipnType; }
    public void setIpnType(String ipnType) { this.ipnType = ipnType; }
    public String getSubjectIpm() { return subjectIpm; }
    public void setSubjectIpm(String subjectIpm) { this.subjectIpm = subjectIpm; }
    public String getSubjectMts() { return subjectMts; }
    public void setSubjectMts(String subjectMts) { this.subjectMts = subjectMts; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getReceiptTime() { return receiptTime; }
    public void setReceiptTime(String receiptTime) { this.receiptTime = receiptTime; }
    public Integer getNonReceiptReason() { return nonReceiptReason; }
    public void setNonReceiptReason(Integer nonReceiptReason) { this.nonReceiptReason = nonReceiptReason; }
    public Integer getDiscardReason() { return discardReason; }
    public void setDiscardReason(Integer discardReason) { this.discardReason = discardReason; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getDiagnosticCode() { return diagnosticCode; }
    public void setDiagnosticCode(String diagnosticCode) { this.diagnosticCode = diagnosticCode; }
    public String getSupplementaryInfo() { return supplementaryInfo; }
    public void setSupplementaryInfo(String supplementaryInfo) { this.supplementaryInfo = supplementaryInfo; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
