package vn.asg.cp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Bảng message_conversion_log — traffic log sau mỗi lần chuyển đổi điện văn.
 */
@Entity
@Table(name = "message_conversion_log")
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class MessageConversionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "date", length = 8)
    private String date;

    /** AMHS / SWIM */
    @Column(name = "type", length = 4)
    private String type;

    /** IN / OUT */
    @Column(name = "category", length = 12)
    private String category;

    @Column(name = "message_id", length = 256)
    private String messageId;

    @Column(name = "ipm_id", length = 256)
    private String ipmId;

    // Message Transfer System Identifier
    @Column(name = "mts_id", length = 256)
    private String mtsId;

    @Column(name = "amqp_message_id", length = 256)
    private String amqpMessageId;

    @Column(name = "priority", length = 12)
    private String priority;

    @Column(name = "ohi", length = 255)
    private String ohi;

    @Column(name = "origin", length = 128)
    private String origin;

    @Column(name = "filing_time", length = 20)
    private String filingTime;

    @Column(name = "subject", length = 512)
    private String subject;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "converted_time")
    private LocalDateTime convertedTime;

    /** OK / ERROR / REJECT / UNROUTED */
    @Column(name = "status", length = 8)
    private String status;

    /** convert-as-amqp / convert-as-ipm / reject */
    @Column(name = "action_taken", length = 255)
    private String actionTaken;

    @Column(name = "rejection_source", length = 20)
    private String rejectionSource;

    @Column(name = "rejection_reason", length = 64)
    private String rejectionReason;

    @Column(name = "rejection_diagnostic", columnDefinition = "TEXT")
    private String rejectionDiagnostic;

    @Column(name = "supplementary_info", length = 512)
    private String supplementaryInfo;

    @Column(name = "remark", length = 1000)
    private String remark;
}
