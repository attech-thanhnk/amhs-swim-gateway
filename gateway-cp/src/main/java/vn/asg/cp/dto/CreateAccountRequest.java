package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to create a new AMQP/AMHS account")
public class CreateAccountRequest {

    @Schema(description = "Account name (unique identifier)",
            example = "solace-broker-primary",
            required = true)
    private String accountName;

    @Schema(description = "Protocol type: AMQP or X400",
            example = "AMQP",
            allowableValues = {"AMQP", "X400"},
            required = true)
    private String protocol;

    @Schema(description = "Host address or IP",
            example = "127.0.0.1",
            required = true)
    private String host;

    @Schema(description = "Port number",
            example = "5672",
            required = true)
    private Integer port;

    @Schema(description = "Configuration JSON (username, password, vpn, etc.)",
            example = "{\"username\":\"admin\",\"password\":\"admin\",\"vpn\":\"default\"}")
    private String configJson;

    @Schema(description = "Enable TLS/SSL",
            example = "false")
    private Boolean tlsEnabled;

    @Schema(description = "SASL mechanism (PLAIN, EXTERNAL, etc.)",
            example = "PLAIN")
    private String saslMechanism;

    @Schema(description = "Optional note",
            example = "Primary Solace broker")
    private String note;
}
