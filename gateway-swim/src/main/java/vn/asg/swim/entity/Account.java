package vn.asg.swim.entity;

import jakarta.persistence.*;


/**
 * Account entity — Manages AMQP or X.400 connections.
 * This configuration is used by the SWIM Component to establish connectivity.
 */
@Entity
@Table(name = "accounts")
public class Account {

    public Account() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_name", unique = true, length = 50)
    private String accountName;

    @Column(name = "protocol", length = 20)
    private String protocol; // AMQP / X400

    @Column(name = "host")
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "status", length = 20)
    private String status; // ACTIVE / INACTIVE

    @Column(name = "bind_status", length = 20)
    private String bindStatus; // CONNECTED / DISCONNECTED / CONNECTING

    @Column(name = "certificate_path", length = 500)
    private String certificatePath;

    @Column(name = "certificate_passphrase", columnDefinition = "TEXT")
    private String certificatePassphrase;

    @Column(name = "sasl_mechanism", length = 20)
    private String saslMechanism;

    @Column(name = "tls_enabled")
    private Boolean tlsEnabled = false;

    @Column(name = "signed_messages_action", length = 30)
    private String signedMessagesAction;

    @Column(name = "unsigned_messages_action", length = 30)
    private String unsignedMessagesAction;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBindStatus() { return bindStatus; }
    public void setBindStatus(String bindStatus) { this.bindStatus = bindStatus; }
    public String getCertificatePath() { return certificatePath; }
    public void setCertificatePath(String certificatePath) { this.certificatePath = certificatePath; }
    public String getCertificatePassphrase() { return certificatePassphrase; }
    public void setCertificatePassphrase(String certificatePassphrase) { this.certificatePassphrase = certificatePassphrase; }
    public String getSaslMechanism() { return saslMechanism; }
    public void setSaslMechanism(String saslMechanism) { this.saslMechanism = saslMechanism; }
    public Boolean getTlsEnabled() { return tlsEnabled; }
    public void setTlsEnabled(Boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }
    public String getSignedMessagesAction() { return signedMessagesAction; }
    public void setSignedMessagesAction(String signedMessagesAction) { this.signedMessagesAction = signedMessagesAction; }
    public String getUnsignedMessagesAction() { return unsignedMessagesAction; }
    public void setUnsignedMessagesAction(String unsignedMessagesAction) { this.unsignedMessagesAction = unsignedMessagesAction; }
}
