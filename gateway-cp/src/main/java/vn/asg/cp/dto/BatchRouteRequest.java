package vn.asg.cp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


import java.util.List;

/**
 * Request DTO cho batch routing nhiều UNROUTED messages.
 */
public class BatchRouteRequest {

    public BatchRouteRequest() {
    }

    /**
     * List of message IDs to route.
     */
    @NotEmpty(message = "Message IDs list cannot be empty")
    private List<Long> msgids;

    /**
     * AFTN originator address (8 chars).
     */
    @NotBlank(message = "Originator is required")
    @Pattern(regexp = "[A-Z]{8}", message = "Originator must be 8 uppercase letters")
    private String originator;

    /**
     * Space-separated AFTN recipient addresses.
     */
    @NotBlank(message = "Recipients are required")
    @Size(max = 500, message = "Recipients max 500 characters")
    private String recipients;

    /**
     * Note (optional).
     */
    @Size(max = 500, message = "Note max 500 characters")
    private String note;

    public BatchRouteRequest(List<Long> msgids, String originator, String recipients, String note) {
        this.msgids = msgids;
        this.originator = originator;
        this.recipients = recipients;
        this.note = note;
    }

    public List<Long> getMsgids() {
        return msgids;
    }

    public void setMsgids(List<Long> msgids) {
        this.msgids = msgids;
    }

    public String getOriginator() {
        return originator;
    }

    public void setOriginator(String originator) {
        this.originator = originator;
    }

    public String getRecipients() {
        return recipients;
    }

    public void setRecipients(String recipients) {
        this.recipients = recipients;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
