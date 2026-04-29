package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to update a configuration value")
public class UpdateConfigRequest {

    @Schema(description = "Configuration value",
            example = "5",
            required = true)
    private String value;
}
