package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * GwinHistory Table — Represents archived gwin records.
 */
@Entity
@Table(name = "gwin_history")
public class GwinHistory {

    public GwinHistory() {}

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

    public Long getMsgid() { return msgid; }
    public void setMsgid(Long msgid) { this.msgid = msgid; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getAmqpProperties() { return amqpProperties; }
    public void setAmqpProperties(String amqpProperties) { this.amqpProperties = amqpProperties; }
    public Byte getPriority() { return priority; }
    public void setPriority(Byte priority) { this.priority = priority; }
    public LocalDateTime getTime() { return time; }
    public void setTime(LocalDateTime time) { this.time = time; }
    public String getPayloadContent() { return payloadContent; }
    public void setPayloadContent(String payloadContent) { this.payloadContent = payloadContent; }
    public String getBodyType() { return bodyType; }
    public void setBodyType(String bodyType) { this.bodyType = bodyType; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getAddressingSource() { return addressingSource; }
    public void setAddressingSource(String addressingSource) { this.addressingSource = addressingSource; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
