package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to update an existing account (all fields optional)")
public class UpdateAccountRequest {

    @Schema(description = "Host address or IP",
            example = "127.0.0.1")
    private String host;

    @Schema(description = "Port number",
            example = "5672")
    private Integer port;

    @Schema(description = "Configuration JSON",
            example = "{\"username\":\"admin\",\"password\":\"newpass\"}")
    private String configJson;

    @Schema(description = "Enable TLS/SSL",
            example = "true")
    private Boolean tlsEnabled;

    @Schema(description = "SASL mechanism",
            example = "PLAIN")
    private String saslMechanism;

    @Schema(description = "Optional note",
            example = "Updated configuration")
    private String note;
}
