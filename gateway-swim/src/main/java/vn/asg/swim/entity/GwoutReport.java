package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Thực thể GwoutReport - hàng đợi AMHS report mà ITCU yêu cầu AMHS Component (amss) phát
 * ra đường truyền X.400 (EUR Doc 047 §4.4.8 Generation of AMHS reports).
 * <p>
 * ITCU chỉ QUYẾT ĐỊNH và ghi nhận; việc dựng và phát Delivery Report / Non-Delivery Report
 * trên X.400 là trách nhiệm của AMHS Component.
 * <p>
 * <b>Mỗi dòng là kết quả của MỘT recipient</b>, vì report của X.400 mang per-recipient-fields.
 * Nhờ vậy CTSW012 biểu diễn được combined report: cùng một {@code gwoutId} có thể vừa có dòng
 * DR (recipient tra được địa chỉ AF) vừa có dòng NDR (recipient không tra được). amss gom theo
 * {@code gwoutId}, phát MỘT report chứa đủ per-recipient-fields, rồi đánh dấu cả nhóm là SENT.
 * <p>
 * Ràng buộc UNIQUE (gwout_id, recipient, report_type) bảo đảm idempotency hai chiều: ITCU chạy
 * lại không sinh dòng trùng, amss không phát trùng report.
 */
@Entity
@Table(name = "gwout_report")
public class GwoutReport {

    public GwoutReport() {
    }

    /** Delivery Report - bản tin đã chuyển đổi thành công cho recipient này. */
    public static final String TYPE_DR = "DR";

    /** Non-Delivery Report - bản tin bị từ chối cho recipient này. */
    public static final String TYPE_NDR = "NDR";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    /**
     * non-delivery-reason-code. EUR Doc 047 §4.4.1-§4.4.2/§4.4.6: mọi trường hợp từ chối ở
     * chiều AMHS → SWIM đều dùng abstract-value "unable-to-transfer".
     */
    public static final String REASON_UNABLE_TO_TRANSFER = "unable-to-transfer";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Khóa ngoại trỏ đến gwout.msgid */
    @Column(name = "gwout_id", nullable = false)
    private Long gwoutId;

    /** MTS-Identifier của bản tin gốc, để amss đối chiếu với bản tin đã nhận trên X.400 */
    @Column(name = "mts_id", length = 200)
    private String mtsId;

    /** DR hoặc NDR */
    @Column(name = "report_type", length = 3, nullable = false)
    private String reportType;

    /** Địa chỉ AFTN của recipient mà dòng report này áp dụng */
    @Column(name = "recipient", length = 100, nullable = false)
    private String recipient;

    /** non-delivery-reason-code (chỉ với NDR) */
    @Column(name = "reason_code", length = 32)
    private String reasonCode;

    /** non-delivery-diagnostic-code (chỉ với NDR) */
    @Column(name = "diagnostic_code", length = 64)
    private String diagnosticCode;

    /**
     * supplementary-information (chỉ với NDR, và chỉ ở những trường hợp Appendix A yêu cầu).
     * Chuỗi phải khớp NGUYÊN VĂN với Appendix A vì test tool so sánh chính xác.
     */
    @Column(name = "supplementary_info", length = 512)
    private String supplementaryInfo;

    /** PENDING → SENT hoặc FAILED. amss cập nhật sau khi phát. */
    @Column(name = "status", length = 16, nullable = false)
    private String status = STATUS_PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** Lỗi do amss ghi lại nếu phát report thất bại */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGwoutId() {
        return gwoutId;
    }

    public void setGwoutId(Long gwoutId) {
        this.gwoutId = gwoutId;
    }

    public String getMtsId() {
        return mtsId;
    }

    public void setMtsId(String mtsId) {
        this.mtsId = mtsId;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getDiagnosticCode() {
        return diagnosticCode;
    }

    public void setDiagnosticCode(String diagnosticCode) {
        this.diagnosticCode = diagnosticCode;
    }

    public String getSupplementaryInfo() {
        return supplementaryInfo;
    }

    public void setSupplementaryInfo(String supplementaryInfo) {
        this.supplementaryInfo = supplementaryInfo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
