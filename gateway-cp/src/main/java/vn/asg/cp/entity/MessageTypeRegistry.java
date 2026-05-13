package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bảng message_type_registry — Danh sách loại điện văn ATS, cách nhận dạng và
 * parse scope địa lý.
 * Dữ liệu ít thay đổi, được cache trong MessageDetectService.
 */
@Entity
@Table(name = "message_type_registry")
@Data
@NoArgsConstructor
public class MessageTypeRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tên loại điện văn, dùng làm key trong toàn hệ thống.
     * Ví dụ: METAR, FPL, NOTAM, TAF, SIGMET, UNKNOWN
     */
    @Column(name = "message_type", length = 50, nullable = false, unique = true)
    private String messageType;

    /**
     * Chuỗi ký tự đầu của body để nhận dạng.
     * Ví dụ: "METAR " → body bắt đầu bằng "METAR ", "(FPL-" → flight plan
     */
    @Column(name = "detect_pattern", length = 255, nullable = false)
    private String detectPattern;

    /**
     * Cách lấy scope địa lý từ body:
     * body_word2 / body_adep_fir / body_qline_fir / body_xml / fixed
     */
    @Column(name = "scope_source", length = 30, nullable = false)
    private String scopeSource;

    /** Độ phức tạp parser: easy / medium / hard */
    @Column(name = "difficulty", length = 10, nullable = false)
    private String difficulty;

    /** Giai đoạn implement: 1=ưu tiên cao, 2=trung bình, 3=thấp */
    @Column(name = "phase", nullable = false)
    private Byte phase = 1;

    /** 0=tắt, 1=bật */
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /** Ghi chú kỹ thuật. Ví dụ: "TAF AMD thì scope ở word[3]" */
    @Column(name = "note", length = 500)
    private String note;

    // Scope source constants
    public static final String SCOPE_BODY_WORD2 = "body_word2";
    public static final String SCOPE_BODY_ADEP_FIR = "body_adep_fir";
    public static final String SCOPE_BODY_QLINE_FIR = "body_qline_fir";
    public static final String SCOPE_BODY_XML = "body_xml";
    public static final String SCOPE_FIXED = "fixed";
}
