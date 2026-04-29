package vn.asg.cp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO để reject UNROUTED message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectMessageRequest {

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
}
