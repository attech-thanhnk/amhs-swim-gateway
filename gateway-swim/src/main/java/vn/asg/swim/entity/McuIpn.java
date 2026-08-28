package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Thực thể McuIpn - IPN (Receipt Notification / Non-Receipt Notification) nhận từ AMHS,
 * do AMHS Component (amss) ghi vào bảng {@code mtcu_ipn} và ITCU đọc ra xử lý.
 * <p>
 * EUR Doc 047 §4.4.7 và Appendix A CTSW014/CTSW015. IPN không được chuyển sang môi trường
 * SWIM (§2.2.1.1: "This interface shall not receive IPNs, Reports or Probes"); ITCU chỉ
 * kiểm tra, log và báo Control Position, hoặc sinh NDR khi RN không có bản tin chủ đề.
 */
@Entity
@Table(name = "mtcu_ipn")
public class McuIpn {

    public McuIpn() {
    }

    /** Receipt Notification */
    public static final String TYPE_RN = "RN";

    /** Non-Receipt Notification */
    public static final String TYPE_NRN = "NRN";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSED = "PROCESSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RN hoặc NRN */
    @Column(name = "notification_type", length = 3, nullable = false)
    private String notificationType;

    /**
     * IPM-Identifier của bản tin chủ đề. ITCU dùng giá trị này tra ngược
     * {@code gwout.ipm_id} để xác định bản tin đã đi qua gateway hay chưa (§4.4.7.1).
     */
    @Column(name = "subject_ipm_id", length = 255)
    private String subjectIpmId;

    /** MTS-Identifier của bản tin chủ đề, dùng đối chiếu phụ khi thiếu IPM-Identifier */
    @Column(name = "subject_mts_id", length = 255)
    private String subjectMtsId;

    /** O/R address của bên phát IPN */
    @Column(name = "or_address", length = 255)
    private String orAddress;

    /** Thời điểm nhận bản tin của recipient (receipt-time trong RN) */
    @Column(name = "receipt_time", length = 255)
    private String receiptTime;

    /** non-receipt-reason, chỉ áp dụng với NRN */
    @Column(name = "non_receipt_reason")
    private Integer nonReceiptReason;

    /** Thời điểm amss ghi bản ghi này */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** PENDING -> PROCESSED, do ITCU cập nhật sau khi xử lý xong */
    @Column(name = "status", length = 16)
    private String status = STATUS_PENDING;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getSubjectIpmId() {
        return subjectIpmId;
    }

    public void setSubjectIpmId(String subjectIpmId) {
        this.subjectIpmId = subjectIpmId;
    }

    public String getSubjectMtsId() {
        return subjectMtsId;
    }

    public void setSubjectMtsId(String subjectMtsId) {
        this.subjectMtsId = subjectMtsId;
    }

    public String getOrAddress() {
        return orAddress;
    }

    public void setOrAddress(String orAddress) {
        this.orAddress = orAddress;
    }

    public String getReceiptTime() {
        return receiptTime;
    }

    public void setReceiptTime(String receiptTime) {
        this.receiptTime = receiptTime;
    }

    public Integer getNonReceiptReason() {
        return nonReceiptReason;
    }

    public void setNonReceiptReason(Integer nonReceiptReason) {
        this.nonReceiptReason = nonReceiptReason;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
