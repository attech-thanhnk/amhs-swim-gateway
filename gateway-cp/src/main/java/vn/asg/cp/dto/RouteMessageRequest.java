package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to manually route an unrouted message")
public class RouteMessageRequest {

    @Schema(description = "AFTN originator address (8 characters)",
            example = "VVHHZQZX",
            required = true,
            minLength = 8,
            maxLength = 8)
    private String originator;

    @Schema(description = "Space-separated AFTN recipient addresses",
            example = "VVHHZTZX VVTSZDYX",
            required = true,
            maxLength = 500)
    private String recipients;

    @Schema(description = "Optional note explaining manual routing",
            example = "Manually routed by operator",
            maxLength = 500)
    private String note;
}
