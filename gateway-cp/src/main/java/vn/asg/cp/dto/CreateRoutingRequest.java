package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Request to create a new routing rule")
public class CreateRoutingRequest {

    @NotBlank(message = "Direction is required (IN/OUT)")
    @Pattern(regexp = "^(IN|OUT)$", message = "Direction must be IN or OUT")
    @Schema(description = "Direction: IN (SWIM→AMHS) or OUT (AMHS→SWIM)",
            example = "OUT",
            allowableValues = {"IN", "OUT"},
            required = true)
    private String direction;

    // ========== INBOUND DIRECTION (SWIM → AMHS) ==========
    @Schema(description = "AMQP topic to subscribe (required for IN direction)",
            example = "ats/met/metar")
    private String receiveTopic;

    @Schema(description = "AFTN recipient addresses. OUT: decides send_topic (comma/space separated, \"*\" suffix = prefix wildcard). IN: display label only",
            example = "VVHHZTZX VVTSZDYX")
    private String recipients;

    // ========== OUTBOUND DIRECTION (AMHS → SWIM) ==========
    @Schema(description = "AMQP topic to publish (required for OUT direction)",
            example = "ats/met/metar")
    private String sendTopic;

    // ========== COMMON PROPERTIES ==========
    @Min(value = 0, message = "Priority must be between 0 and 255")
    @Max(value = 255, message = "Priority must be between 0 and 255")
    @Schema(description = "Priority (0-255, lower number = higher priority)",
            example = "100")
    private Integer priority;

    @Schema(description = "Enable/disable this rule",
            example = "true")
    private Boolean active;

    @Schema(description = "Optional note/description",
            example = "METAR routing to SWIM")
    private String note;
}

