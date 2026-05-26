package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to update a configuration value")
public class UpdateConfigRequest {

    public UpdateConfigRequest() {}

    @Schema(description = "Configuration value",
            example = "5",
            required = true)
    private String value;

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
