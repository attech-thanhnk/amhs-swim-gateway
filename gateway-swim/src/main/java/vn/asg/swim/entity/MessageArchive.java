package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * MessageArchive entity — Long-term message storage (30 days).
 */
@Entity
@Table(name = "message_archive")
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

    public MessageArchive() {}

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }
    public String getMtsId() { return mtsId; }
    public void setMtsId(String mtsId) { this.mtsId = mtsId; }
    public String getIpmId() { return ipmId; }
    public void setIpmId(String ipmId) { this.ipmId = ipmId; }
    public String getAmqpMessageId() { return amqpMessageId; }
    public void setAmqpMessageId(String amqpMessageId) { this.amqpMessageId = amqpMessageId; }
    public String getRecipients() { return recipients; }
    public void setRecipients(String recipients) { this.recipients = recipients; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
}
