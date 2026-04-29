package vn.asg.cp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO cho manual routing UNROUTED message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualRouteRequest {

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
}
