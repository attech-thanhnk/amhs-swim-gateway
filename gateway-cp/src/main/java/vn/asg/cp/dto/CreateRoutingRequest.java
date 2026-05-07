package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to create a new routing rule")
public class CreateRoutingRequest {

    @Schema(description = "Direction: IN (SWIM→AMHS) or OUT (AMHS→SWIM)",
            example = "OUT",
            allowableValues = {"IN", "OUT"},
            required = true)
    private String direction;

    // ========== INBOUND DIRECTION (SWIM → AMHS) ==========
    @Schema(description = "AMQP topic to subscribe (required for IN direction)",
            example = "ats/met/metar")
    private String receiveTopic;

    @Schema(description = "Optional content filter (for IN direction)",
            example = "METAR")
    private String messageFilter;

    @Schema(description = "Space-separated AFTN addresses (required for IN direction)",
            example = "VVHHZTZX VVTSZDYX")
    private String recipients;

    @Schema(description = "AFTN originator address (required for IN direction)",
            example = "VVHHZQZX")
    private String originator;

    // ========== OUTBOUND DIRECTION (AMHS → SWIM) ==========
    @Schema(description = "Message type to detect (required for OUT direction)",
            example = "METAR")
    private String messageType;

    @Schema(description = "AMQP topic to publish (required for OUT direction)",
            example = "ats/met/metar")
    private String sendTopic;

    // ========== COMMON PROPERTIES ==========
    @Schema(description = "Priority (0-255, lower number = higher priority)",
            example = "100")
    private Integer priority;

      @Schema(description = "Priority SWIM (0-9)", example = "3")
    private Integer prioritySwim;

    @Schema(description = "Priority AMHS (SS/DD/FF/GG/KK)", example = "FF")
    private String priorityAmhs;

    @Schema(description = "Enable/disable this rule",
            example = "true")
    private Boolean active;

    @Schema(description = "Optional note/description",
            example = "METAR routing to SWIM")
    private String note;
}
