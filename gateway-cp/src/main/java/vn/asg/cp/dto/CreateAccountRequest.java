package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "Request to create a new AMQP/AMHS account")
public class CreateAccountRequest {

    public CreateAccountRequest() {}

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
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public Boolean getTlsEnabled() { return tlsEnabled; }
    public void setTlsEnabled(Boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }
    public String getSaslMechanism() { return saslMechanism; }
    public void setSaslMechanism(String saslMechanism) { this.saslMechanism = saslMechanism; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
