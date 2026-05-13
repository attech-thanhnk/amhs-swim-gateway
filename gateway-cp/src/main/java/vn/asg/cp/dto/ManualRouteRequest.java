package vn.asg.cp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO cho manual routing UNROUTED message.
 */
public class ManualRouteRequest {

    public ManualRouteRequest() {}

    public ManualRouteRequest(String originator, String recipients, String note) {
        this.originator = originator;
        this.recipients = recipients;
        this.note = note;
    }

    /**
     * AFTN originator address (8 chars).
     */
    @NotBlank(message = "Originator is required")
    @Pattern(regexp = "[A-Z]{8}", message = "Originator must be 8 uppercase letters")
    private String originator;

    /**
     * Space-separated AFTN recipient addresses.
     * Ví dụ: "VVHHZTZX VVTSZDYX"
     */
    @NotBlank(message = "Recipients are required")
    @Size(max = 500, message = "Recipients max 500 characters")
    private String recipients;

    /**
     * Operator note (optional).
     */
    @Size(max = 500, message = "Note max 500 characters")
    private String note;

    public String getOriginator() { return originator; }
    public void setOriginator(String originator) { this.originator = originator; }
    public String getRecipients() { return recipients; }
    public void setRecipients(String recipients) { this.recipients = recipients; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
