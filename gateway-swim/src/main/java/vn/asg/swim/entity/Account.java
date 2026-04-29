package vn.asg.swim.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Account entity — Manages AMQP or X.400 connections.
 * This configuration is used by the SWIM Component to establish connectivity.
 */
@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
public class Account {

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
}
