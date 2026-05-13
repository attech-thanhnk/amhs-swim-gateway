package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "Request to update an existing routing rule (all fields optional)")
public class UpdateRoutingRequest {

    public UpdateRoutingRequest() {}

    @Schema(description = "Direction: IN (SWIM→AMHS) or OUT (AMHS→SWIM)",
            example = "OUT",
            allowableValues = {"IN", "OUT"})
    private String direction;

    @Schema(description = "AMQP topic to subscribe (for IN direction)",
            example = "ats/met/metar")
    private String receiveTopic;

    @Schema(description = "Optional content filter (for IN direction)",
            example = "METAR")
    private String messageFilter;

    @Schema(description = "Space-separated AFTN addresses (for IN direction)",
            example = "VVHHZTZX VVTSZDYX")
    private String recipients;

    @Schema(description = "AFTN originator address (for IN direction)",
            example = "VVHHZQZX")
    private String originator;

    @Schema(description = "Message type to detect (for OUT direction)",
            example = "METAR")
    private String messageType;

    @Schema(description = "AMQP topic to publish (for OUT direction)",
            example = "ats/met/metar")
    private String sendTopic;

    @Schema(description = "Priority (0-255, lower number = higher priority)",
            example = "100")
    private Integer priority;

    @Schema(description = "Enable/disable this rule",
            example = "true")
    private Boolean active;

    @Schema(description = "Output format choice: true=JSON, false=TAC Forward",
            example = "true")
    private Boolean convertToJson;

    @Schema(description = "Optional note/description",
            example = "Updated routing rule")
    private String note;

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getReceiveTopic() { return receiveTopic; }
    public void setReceiveTopic(String receiveTopic) { this.receiveTopic = receiveTopic; }
    public String getMessageFilter() { return messageFilter; }
    public void setMessageFilter(String messageFilter) { this.messageFilter = messageFilter; }
    public String getRecipients() { return recipients; }
    public void setRecipients(String recipients) { this.recipients = recipients; }
    public String getOriginator() { return originator; }
    public void setOriginator(String originator) { this.originator = originator; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getSendTopic() { return sendTopic; }
    public void setSendTopic(String sendTopic) { this.sendTopic = sendTopic; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Boolean getConvertToJson() { return convertToJson; }
    public void setConvertToJson(Boolean convertToJson) { this.convertToJson = convertToJson; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
