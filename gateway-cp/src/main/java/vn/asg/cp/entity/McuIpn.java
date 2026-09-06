package vn.asg.cp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bảng mtcu_ipn — IPN (RN/NRN) nhận từ AMHS, do AMHS Component ghi và ITCU xử lý.
 * <p>
 * Control Position chỉ ĐỌC. Appendix A CTSW014, CTSW015, CTSW113 đều yêu cầu
 * "stores the message for appropriate processing at the Control Position", nên operator
 * phải xem được chính bản ghi IPN chứ không chỉ dòng cảnh báo tóm tắt.
 */
@Entity
@Table(name = "mtcu_ipn")
@Data
@NoArgsConstructor
public class McuIpn {

    public static final String TYPE_RN = "RN";
    public static final String TYPE_NRN = "NRN";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSED = "PROCESSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RN hoặc NRN */
    @Column(name = "notification_type", length = 3)
    private String notificationType;

    /** IPM-Identifier của bản tin chủ đề — đối chiếu với gwin.ipm_id */
    @Column(name = "subject_ipm_id", length = 255)
    private String subjectIpmId;

    /** MTS-Identifier của bản tin chủ đề — đối chiếu với gwin.mts_id */
    @Column(name = "subject_mts_id", length = 255)
    private String subjectMtsId;

    /** O/R address của bên phát IPN */
    @Column(name = "or_address", length = 255)
    private String orAddress;

    /** receipt-time trong RN */
    @Column(name = "receipt_time", length = 255)
    private String receiptTime;

    /** non-receipt-reason, chỉ với NRN */
    @Column(name = "non_receipt_reason")
    private Integer nonReceiptReason;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** PENDING -> PROCESSED, do ITCU cập nhật */
    @Column(name = "status", length = 16)
    private String status;
}
