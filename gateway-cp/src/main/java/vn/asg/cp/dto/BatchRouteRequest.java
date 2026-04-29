package vn.asg.cp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO cho batch routing nhiều UNROUTED messages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchRouteRequest {

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
}
