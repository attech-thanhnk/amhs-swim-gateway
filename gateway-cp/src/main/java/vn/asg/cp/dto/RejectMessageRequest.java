package vn.asg.cp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO để reject UNROUTED message.
 */
public class RejectMessageRequest {

    public RejectMessageRequest() {}

    public RejectMessageRequest(String reason, String note) {
        this.reason = reason;
        this.note = note;
    }

    /**
     * Lý do reject.
     */
    @NotBlank(message = "Rejection reason is required")
    @Size(max = 200, message = "Reason max 200 characters")
    private String reason;

    /**
     * Ghi chú chi tiết (optional).
     */
    @Size(max = 500, message = "Note max 500 characters")
    private String note;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
