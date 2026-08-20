package vn.asg.cp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    /** Priority AMHS: 'SS', 'DD', 'FF', 'GG', 'KK' */
    @Column(name = "amhs_priority", length = 10)
    private String amhsPriority;

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
    @Column(name = "origin", length = 200)
    private String origin;

    /** Danh sách địa chỉ AMHS recipients, cách nhau dấu cách */
    @Column(name = "address", length = 1000)
    private String address;

    /** Optional heading/Priority prefix */
    @Column(name = "optional_heading", length = 60)
    private String optionalHeading;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "amhs_ttl")
    private LocalDateTime amhsTtl;

    @Column(name = "amhs_registered_id", length = 200)
    private String amhsRegisteredId;

    @Column(name = "ipm_id", length = 200)
    private String ipmId;

    @Column(name = "swim_priority")
    private Integer swimPriority;

    @Column(name = "amqp_message_id", length = 256)
    private String amqpMessageId;

    @Column(name = "body_part_type", length = 50)
    private String bodyPartType;

    @Column(name = "body_part_charset", length = 20)
    private String bodyPartCharset;

    @Column(name = "ftbp_file_name", length = 255)
    private String ftbpFileName;

    @Column(name = "ftbp_object_size", length = 20)
    private String ftbpObjectSize;

    @Column(name = "ftbp_last_mod", length = 20)
    private String ftbpLastMod;

    @Column(name = "message_signed", length = 20)
    private String messageSigned;

    @Column(name = "rejection_reason", length = 64)
    private String rejectionReason;

    @Column(name = "rejection_diagnostic", length = 64)
    private String rejectionDiagnostic;

    /** 0=không yêu cầu delivery report, 1=có */
    @Column(name = "amhs_delivery_report")
    private Boolean amhsDeliveryReport = false;

    /** Content type. Ví dụ: text/plain, application/xml */
    @Column(name = "content_type", length = 128)
    private String contentType;

    /**
     * Trạng thái tổng. Phản ánh tình trạng của tất cả gwout_dispatch con.
     * 0=PENDING, 1=TRANSFORMED, 2=PUBLISHED, 3=FAILED, 4=UNROUTED, 5=RESOLVED, 6=CANCELLED
     */
    @Column(name = "status")
    private Integer status = OutboundStatus.PENDING.getValue();
}


