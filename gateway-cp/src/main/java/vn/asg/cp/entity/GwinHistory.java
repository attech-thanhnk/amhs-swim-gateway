package vn.asg.cp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GwinHistory Table — Represents archived gwin records.
 */
@Entity
@Table(name = "gwin_history")
@Data
@NoArgsConstructor
public class GwinHistory {


    @Id
    private Long msgid;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "source", length = 200)
    private String source;

    @Column(name = "subject", length = 100)
    private String subject;

    @Column(name = "amqp_properties", columnDefinition = "TEXT")
    private String amqpProperties;

    @Column(name = "priority")
    private Byte priority = 2;

    @Column(name = "time")
    private LocalDateTime time;

    @Column(name = "payload_content", columnDefinition = "MEDIUMTEXT")
    private String payloadContent;

    @Column(name = "body_type", length = 10)
    private String bodyType = "text";

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "origin", length = 200)
    private String origin;

    @Column(name = "address", length = 1000)
    private String address;

    @Column(name = "addressing_source", length = 200)
    private String addressingSource;

    @Column(name = "status")
    private Integer status = MessageStatus.IN_PENDING.getValue();
}
