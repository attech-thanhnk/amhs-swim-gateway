package vn.asg.cp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bảng gwout — Message nhận từ AMHS Component, chờ SWIM Component publish lên AMQP.
 */
@Entity
@Table(name = "gwout")
@Data
@NoArgsConstructor
public class Gwout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long msgid;

    @Column(name = "amhsid", length = 200)
    private String amhsid;

    @Column(name = "amhs_priority", length = 10)
    private String amhsPriority;

    @Column(name = "time")
    private LocalDateTime time;

    @Column(name = "filing_time", length = 6)
    private String filingTime;

    @Column(name = "TEXT", columnDefinition = "MEDIUMTEXT")
    private String text;

    @Column(name = "body_type", length = 10)
    private String bodyType = "text";

    @Column(name = "origin", length = 200)
    private String origin;

    @Column(name = "address", columnDefinition = "MEDIUMTEXT")
    private String address;

    @Column(name = "optional_heading", length = 255)
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

    @Column(name = "origin_eit", length = 255)
    private String originEit;

    @Column(name = "x400_content_type")
    private Integer x400ContentType;

    @Column(name = "number_of_attachment")
    private Integer numberOfAttachment;

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

    @Column(name = "rejection_source", length = 20)
    private String rejectionSource;

    @Column(name = "amhs_delivery_report")
    private Boolean amhsDeliveryReport = false;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "status")
    private Integer status = OutboundStatus.PENDING.getValue();
}
