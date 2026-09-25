package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to update an existing account (all fields optional)")
public class UpdateAccountRequest {

    @Size(max = 255, message = "Host max 255 characters")
    @Schema(description = "Host address or IP", example = "127.0.0.1")
    private String host;

    @Min(value = 1, message = "Port must be greater than 0")
    @Max(value = 65535, message = "Port must be less than or equal to 65535")
    @Schema(description = "Port number", example = "5672")
    private Integer port;

    @Schema(description = "Configuration JSON", example = "{\"username\":\"admin\",\"password\":\"newpass\"}")
    private String configJson;

    @Schema(description = "Enable TLS/SSL", example = "true")
    private Boolean tlsEnabled;

    @Schema(description = "SASL mechanism", example = "PLAIN")
    private String saslMechanism;

    @Schema(description = "Optional note", example = "Updated configuration")
    private String note;
}

