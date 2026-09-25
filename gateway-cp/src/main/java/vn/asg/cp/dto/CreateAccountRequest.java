package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new AMQP/AMHS account")
public class CreateAccountRequest {

    @NotBlank(message = "Account name is required")
    @Size(max = 100, message = "Account name max 100 characters")
    @Schema(description = "Account name (unique identifier)",
            example = "solace-broker-primary",
            required = true)
    private String accountName;

    @NotBlank(message = "Protocol is required")
    @Schema(description = "Protocol type: AMQP or X400",
            example = "AMQP",
            allowableValues = {"AMQP", "X400"},
            required = true)
    private String protocol;

    @NotBlank(message = "Host is required")
    @Schema(description = "Host address or IP",
            example = "127.0.0.1",
            required = true)
    private String host;

    @NotNull(message = "Port is required")
    @Min(value = 1, message = "Port must be greater than 0")
    @Max(value = 65535, message = "Port must be less than or equal to 65535")
    @Schema(description = "Port number",
            example = "5672",
            required = true)
    private Integer port;

    @NotBlank(message = "Configuration JSON is required")
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

