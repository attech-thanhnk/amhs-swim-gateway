package vn.asg.swim.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.*;

import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.repository.AccountRepository;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages AMQP 1.0 connections to the Solace broker.
 * Supports PLAIN auth, TLS, and auto-reconnect with exponential backoff.
 * Configuration prioritizes the `accounts` table in the database (synced with
 * CP).
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionManagerService {

    private final AccountRepository accountRepository;
    private final AlertService alertService;
    private final SystemLogService systemLogService;

    @Value("${amqp.default.host:localhost}")
    private String defaultHost;

    @Value("${amqp.default.port:5672}")
    private int defaultPort;

    @Value("${amqp.default.username:admin}")
    private String defaultUsername;

    @Value("${amqp.default.password:admin}")
    private String defaultPassword;

    @Value("${amqp.default.tls:false}")
    private boolean defaultTls;

    @Getter
    private Connection connection;

    @Getter
    private final AtomicBoolean connected = new AtomicBoolean(false);

    @Getter
    private String bindStatus = "DISCONNECTED";

    private Long activeAccountId = null;

    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
    private static final int MAX_BACKOFF_MS = 30_000;

    @PostConstruct
    public void init() {
        connect();
    }

    public synchronized void connect() {
        try {
            // Prioritize Database Account configuration
            var activeAcc = accountRepository.findFirstByProtocolAndStatusIgnoreCase("AMQP", "ACTIVE").orElse(null);

            String currentHost = defaultHost;
            int currentPort = defaultPort;
            String currentUser = defaultUsername;
            String currentPass = defaultPassword;
            boolean currentTls = defaultTls;

            if (activeAcc != null) {
                activeAccountId = activeAcc.getId();
                currentHost = activeAcc.getHost();
                currentPort = activeAcc.getPort() != null ? activeAcc.getPort() : currentPort;
                currentUser = activeAcc.getAccountName();
                currentPass = activeAcc.getCertificatePassphrase();
                currentTls = activeAcc.getTlsEnabled() != null ? activeAcc.getTlsEnabled() : currentTls;

                log.info("**********************************************************");
                log.info("DATABASE (Account: {})", activeAcc.getAccountName());
                log.info("**********************************************************");
            } else {
                activeAccountId = null;
                log.warn("**********************************************************");
                log.warn("FALLBACK (application.properties)");
                log.warn("**********************************************************");
            }

            String scheme = currentTls ? "amqps" : "amqp";
            String url = String.format("%s://%s:%d", scheme, currentHost, currentPort);

            log.info("Connecting to AMQP broker at {} as '{}'...", url, currentUser);
            updateBindStatus("CONNECTING");

            JmsConnectionFactory factory = new JmsConnectionFactory(url);
            factory.setUsername(currentUser);
            factory.setPassword(currentPass);

            Connection conn = factory.createConnection();
            conn.setExceptionListener(ex -> {
                log.error("AMQP connection exception: {}", ex.getMessage());
                connected.set(false);
                updateBindStatus("DISCONNECTED");
                alertService.create(GwAlert.TYPE_CONNECTION_LOST, GwAlert.SEV_CRITICAL,
                        "AMQP connection lost: " + ex.getMessage(), null, null);
                scheduleReconnect();
            });
            conn.start();

            this.connection = conn;
            this.connected.set(true);
            updateBindStatus("CONNECTED");
            this.reconnectAttempt.set(0);

            log.info("AMQP broker connected: {}", url);
            systemLogService.log(GwAlert.SEV_INFO, "SWIM_COMPONENT",
                    "AMQP connection established: " + url);

        } catch (Exception e) {
            log.error("AMQP connection failed: {}", e.getMessage());
            connected.set(false);
            updateBindStatus("DISCONNECTED");
            scheduleReconnect();
        }
    }

    /**
     * Updates connection status both in-memory and in the Database for CP display
     */
    private void updateBindStatus(String status) {
        this.bindStatus = status;
        if (activeAccountId != null) {
            try {
                accountRepository.findById(activeAccountId).ifPresent(acc -> {
                    acc.setBindStatus(status);
                    accountRepository.save(acc);
                });
            } catch (Exception e) {
                log.warn("Failed to sync bindStatus to DB: {}", e.getMessage());
            }
        }
    }

    private void scheduleReconnect() {
        int attempt = reconnectAttempt.incrementAndGet();
        long delay = Math.min(1000L * (1L << Math.min(attempt - 1, 5)), MAX_BACKOFF_MS);
        log.warn("Scheduling AMQP reconnect in {}ms (attempt #{})", delay, attempt);
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delay);
                connect();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        t.setDaemon(true);
        t.setName("amqp-reconnect-" + attempt);
        t.start();
    }

    public Session createSession() throws JMSException {
        if (!connected.get() || connection == null) {
            throw new JMSException("AMQP not connected");
        }
        return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    public MessageProducer createProducer(Session session, String destination) throws JMSException {
        Destination dest = session.createTopic(destination);
        return session.createProducer(dest);
    }

    public MessageConsumer createConsumer(Session session, String topic) throws JMSException {
        Destination dest = session.createTopic(topic);
        return session.createConsumer(dest);
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (connection != null) {
                connection.close();
                connected.set(false);
                updateBindStatus("DISCONNECTED");
                log.info("AMQP connection closed");
            }
        } catch (Exception e) {
            log.warn("Error closing AMQP connection: {}", e.getMessage());
        }
    }
}
