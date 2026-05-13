package vn.asg.cp.dto;

import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "Request to update an existing account (all fields optional)")
public class UpdateAccountRequest {

    public UpdateAccountRequest() {}

        @Schema(description = "Host address or IP", example = "127.0.0.1")
        private String host;

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

        public String getHost() {
                return host;
        }

        public void setHost(String host) {
                this.host = host;
        }

        public Integer getPort() {
                return port;
        }

        public void setPort(Integer port) {
                this.port = port;
        }

        public String getConfigJson() {
                return configJson;
        }

        public void setConfigJson(String configJson) {
                this.configJson = configJson;
        }

        public Boolean getTlsEnabled() {
                return tlsEnabled;
        }

        public void setTlsEnabled(Boolean tlsEnabled) {
                this.tlsEnabled = tlsEnabled;
        }

        public String getSaslMechanism() {
                return saslMechanism;
        }

        public void setSaslMechanism(String saslMechanism) {
                this.saslMechanism = saslMechanism;
        }

        public String getNote() {
                return note;
        }

        public void setNote(String note) {
                this.note = note;
        }
}
