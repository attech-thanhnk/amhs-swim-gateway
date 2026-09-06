package vn.asg.cp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bảng mtcu_report — DR/NDR nhận từ AMHS cho bản tin ITCU đã gửi ở chiều SWIM sang AMHS.
 * <p>
 * Control Position chỉ ĐỌC. Appendix A CTSW114 chấm ở đúng màn hình này: NDR về thì phải
 * "logs and reports the situation to the Control Position".
 * <p>
 * Đừng nhầm với gwout_report — bảng đó là report ITCU TỰ SINH cho chiều ngược lại, xếp hàng
 * để AMHS Component phát ra X.400.
 */
@Entity
@Table(name = "mtcu_report")
@Data
@NoArgsConstructor
public class McuReport {

    public static final String TYPE_DR = "DR";
    public static final String TYPE_NDR = "NDR";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSED = "PROCESSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** DR hoặc NDR */
    @Column(name = "report_type", length = 3)
    private String reportType;

    /** gwin.msgid của bản tin gốc, nếu AMHS Component map được */
    @Column(name = "gwin_id")
    private Long gwinId;

    @Column(name = "subject_mts_id", length = 255)
    private String subjectMtsId;

    @Column(name = "subject_ipm_id", length = 255)
    private String subjectIpmId;

    /** O/R address của recipient mà per-recipient-fields này nói tới */
    @Column(name = "recipient", length = 255)
    private String recipient;

    /** non-delivery-reason-code, chỉ với NDR */
    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    /** non-delivery-diagnostic-code — để trống là hợp lệ (CTSW114) */
    @Column(name = "diagnostic_code", length = 64)
    private String diagnosticCode;

    @Column(name = "supplementary_info", length = 512)
    private String supplementaryInfo;

    @Column(name = "report_time", length = 255)
    private String reportTime;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "status", length = 16)
    private String status;
}
