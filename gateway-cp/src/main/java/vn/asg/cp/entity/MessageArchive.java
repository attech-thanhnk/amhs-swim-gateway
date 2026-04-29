package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Bảng message_archive — lưu trữ dài hạn điện văn (30 ngày).
 */
@Entity
@Table(name = "message_archive")
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class MessageArchive {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "msg_id", length = 100)
    private String msgId;

    @Column(name = "mts_id", length = 100)
    private String mtsId;

    @Column(name = "ipm_id", length = 100)
    private String ipmId;

    @Column(name = "amqp_message_id", length = 256)
    private String amqpMessageId;

    @Column(name = "recipients", columnDefinition = "TEXT")
    private String recipients;

    @Column(name = "priority", length = 2)
    private String priority;

    /** AMHS_TO_SWIM / SWIM_TO_AMHS */
    @Column(name = "direction", length = 20)
    private String direction;

    @Column(name = "timestamp")
    private Instant timestamp;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "processing_status", length = 20)
    private String processingStatus;
}
