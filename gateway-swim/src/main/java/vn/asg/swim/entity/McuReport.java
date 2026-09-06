package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Thực thể McuReport — Delivery Report / Non-Delivery Report nhận từ AMHS, do AMHS Component
 * (amss) ghi vào bảng {@code mtcu_report} và ITCU đọc ra xử lý.
 * <p>
 * Appendix A CTSW114.
 * <p>
 * <b>Đừng nhầm với {@link GwoutReport}.</b> Hai bảng ngược chiều nhau:
 * <ul>
 *   <li>{@code gwout_report} — report ITCU tự sinh cho chiều AMHS → SWIM, xếp hàng để amss
 *       phát ra X.400. ITCU ghi, amss đọc.</li>
 *   <li>{@code mtcu_report} — report ITCU nhận về cho bản tin nó đã gửi ở chiều SWIM → AMHS.
 *       amss ghi, ITCU đọc.</li>
 * </ul>
 * Report không được chuyển sang môi trường SWIM; ITCU chỉ log và báo Control Position.
 */
@Entity
@Table(name = "mtcu_report")
public class McuReport {

    public McuReport() {
    }

    /** Delivery Report — bản tin đã tới hòm thư người nhận */
    public static final String TYPE_DR = "DR";

    /** Non-Delivery Report — bản tin không giao được */
    public static final String TYPE_NDR = "NDR";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSED = "PROCESSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** DR hoặc NDR */
    @Column(name = "report_type", length = 3, nullable = false)
    private String reportType;

    /**
     * {@code gwin.msgid} của bản tin gốc, khi amss map được. Để NULL thì ITCU tự tra theo
     * {@code subject_mts_id} rồi tới {@code subject_ipm_id}.
     */
    @Column(name = "gwin_id")
    private Long gwinId;

    /** MTS-Identifier bản tin gốc mà report này nói tới — khoá đối chiếu chính */
    @Column(name = "subject_mts_id", length = 255)
    private String subjectMtsId;

    /** IPM-Identifier bản tin gốc — khoá đối chiếu dự phòng */
    @Column(name = "subject_ipm_id", length = 255)
    private String subjectIpmId;

    /** O/R address của recipient mà per-recipient-fields này nói tới */
    @Column(name = "recipient", length = 255)
    private String recipient;

    /** non-delivery-reason-code, ví dụ {@code unable-to-transfer}. Chỉ có ở NDR. */
    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    /**
     * non-delivery-diagnostic-code.
     * <p>
     * <b>Rỗng là hợp lệ.</b> CTSW114 quy định NDR của nó mang {@code unable-to-transfer} cho
     * reason-code và "empty field" cho diagnostic-code — nên không được coi NULL/rỗng là dữ
     * liệu hỏng rồi loại bản ghi.
     */
    @Column(name = "diagnostic_code", length = 64)
    private String diagnosticCode;

    /** supplementary-information, nếu report có mang */
    @Column(name = "supplementary_info", length = 512)
    private String supplementaryInfo;

    /** Thời điểm ghi trong report, giữ nguyên văn từ X.400 */
    @Column(name = "report_time", length = 255)
    private String reportTime;

    /** Thời điểm amss ghi bản ghi này */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** PENDING -> PROCESSED, do ITCU cập nhật sau khi xử lý xong */
    @Column(name = "status", length = 16)
    private String status = STATUS_PENDING;

    /** Report báo không giao được — nhánh CTSW114 phải log và báo Control Position */
    public boolean isNonDelivery() {
        return TYPE_NDR.equalsIgnoreCase(reportType);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public Long getGwinId() { return gwinId; }
    public void setGwinId(Long gwinId) { this.gwinId = gwinId; }
    public String getSubjectMtsId() { return subjectMtsId; }
    public void setSubjectMtsId(String subjectMtsId) { this.subjectMtsId = subjectMtsId; }
    public String getSubjectIpmId() { return subjectIpmId; }
    public void setSubjectIpmId(String subjectIpmId) { this.subjectIpmId = subjectIpmId; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getDiagnosticCode() { return diagnosticCode; }
    public void setDiagnosticCode(String diagnosticCode) { this.diagnosticCode = diagnosticCode; }
    public String getSupplementaryInfo() { return supplementaryInfo; }
    public void setSupplementaryInfo(String supplementaryInfo) { this.supplementaryInfo = supplementaryInfo; }
    public String getReportTime() { return reportTime; }
    public void setReportTime(String reportTime) { this.reportTime = reportTime; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
