package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to update an existing routing rule (all fields optional)")
public class UpdateRoutingRequest {

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

    @Schema(description = "Priority AMHS (SS/DD/FF/GG/KK)", example = "FF")
    private String priorityAmhs;

    @Schema(description = "Priority SWIM (0-9)", example = "3")
    private Integer prioritySwim;

    @Schema(description = "Enable/disable this rule",
            example = "true")
    private Boolean active;

    @Schema(description = "Optional note/description",
            example = "Updated routing rule")
    private String note;
}
