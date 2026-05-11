package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Bảng gwout — Message nhận từ AMHS Component, chờ SWIM Component publish lên
 * AMQP.
 * Status tổng phản ánh tình trạng của tất cả gwout_dispatch con.
 */
@Entity
@Table(name = "gwout")
@Data
@NoArgsConstructor
public class Gwout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long msgid;

    /** X.400 message-id, unique. Ví dụ: <20240101120000.12345@amhs.vatm.vn> */
    @Column(name = "amhsid", length = 200)
    private String amhsid;

    /** Priority AMHS: 0=Flash, 1=Urgent, 2=Normal, 3=Low */
    @Column(name = "priority")
    private Integer priority;

    /** Thời điểm nhận từ AMHS Component */
    @Column(name = "time")
    private LocalDateTime time;

    /** Filing time AMHS. Ví dụ: 010000 */
    @Column(name = "filing_time", length = 6)
    private String filingTime;

    /** Nội dung plain text */
    @Column(name = "TEXT", columnDefinition = "MEDIUMTEXT")
    private String text;

    /** Loại body: text hoặc ftbp */
    @Column(name = "body_type", length = 10)
    private String bodyType = "text";

    /** Địa chỉ AMHS originator (8 ký tự AFTN). Ví dụ: VVHHZQZX */
    @Column(name = "origin", length = 8)
    private String origin;

    /** Danh sách AMHS recipients (space-separated). Ví dụ: VVHHZTZX VVTSZDYX */
    @Column(name = "address", length = 250)
    private String address;

    /** X.400 Optional Heading Information */
    @Column(name = "optional_heading", length = 60)
    private String optionalHeading;

    /** Thời điểm hết hạn — sau đây không publish nữa. NULL = không giới hạn */
    @Column(name = "amhs_ttl")
    private LocalDateTime amhsTtl;

    /** X.400 registered identifier */
    @Column(name = "amhs_registered_id", length = 200)
    private String amhsRegisteredId;

    /** 0=không yêu cầu delivery report, 1=có */
    @Column(name = "amhs_delivery_report")
    private Boolean amhsDeliveryReport = false;

    /** Content type. Ví dụ: text/plain, application/xml */
    @Column(name = "content_type", length = 128)
    private String contentType;

    /**
     * Trạng thái tổng. Phản ánh tình trạng của tất cả gwout_dispatch con.
     * 0=PENDING, 1=PROCESSING, 2=SENT, 3=DEAD
     */
    @Column(name = "status")
    private Integer status = STATUS_PENDING;

    /** Converted XML content */
    @Column(name = "xml_content", columnDefinition = "MEDIUMTEXT")
    private String xmlContent;

    // Status constants
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_SENT = 2;
    public static final int STATUS_DEAD = 3;
}
