package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.jasypt.encryption.StringEncryptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.*;

import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.repository.AccountRepository;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Quản lý kết nối AMQP 1.0 tới Solace broker.
 * Hỗ trợ xác thực PLAIN, TLS và tự động kết nối lại khi gặp sự cố.
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionManagerService {

    private final AccountRepository accountRepository;
    private final AlertService alertService;
    private final SystemLogService systemLogService;
    private final ApplicationContext applicationContext;

    @Value("${amqp.default.host}")
    private String defaultHost;

    @Value("${amqp.default.port}")
    private Integer defaultPort;

    @Value("${amqp.default.username}")
    private String defaultUsername;

    @Value("${amqp.default.password}")
    private String defaultPassword;

    @Value("${amqp.default.tls:false}")
    private boolean defaultTls;

    private Connection connection;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private String bindStatus = BIND_DISCONNECTED;

    public static final String BIND_CONNECTED = "CONNECTED";
    public static final String BIND_CONNECTING = "CONNECTING";
    public static final String BIND_DISCONNECTED = "DISCONNECTED";

    /**
     * Lấy đối tượng kết nối AMQP hiện tại.
     */
    public Connection getConnection() { return connection; }

    /**
     * Kiểm tra trạng thái kết nối AMQP.
     */
    public AtomicBoolean getConnected() { return connected; }

    /**
     * Lấy trạng thái liên kết.
     */
    public String getBindStatus() { return bindStatus; }

    private Long activeAccountId = null;

    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
    private static final int MAX_BACKOFF_MS = 30_000;

    /**
     * Khởi tạo kết nối AMQP sau khi khởi dựng.
     */
    @PostConstruct
    public void init() {
        connect();
    }

    /**
     * Giải mã mật khẩu nếu ở định dạng mã hóa.
     */
    private String decryptIfEncrypted(String val) {
        if (val != null && val.startsWith("ENC(") && val.endsWith(")")) {
            try {
                StringEncryptor encryptor = applicationContext.getBean(StringEncryptor.class);
                String cipherText = val.substring(4, val.length() - 1);
                return encryptor.decrypt(cipherText);
            } catch (Exception e) {
                log.error("Jasypt decryption failed: {}", e.getMessage());
            }
        }
        return val;
    }

    /**
     * Thực hiện kết nối tới Solace broker.
     */
    public synchronized void connect() {
        try {
            // Ưu tiên lấy cấu hình tài khoản từ cơ sở dữ liệu
            var activeAcc = accountRepository.findFirstByProtocolAndStatusIgnoreCase("AMQP", "ACTIVE").orElse(null);

            String currentHost = defaultHost;
            Integer currentPort = defaultPort;
            String currentUser = defaultUsername;
            String currentPass = defaultPassword;
            boolean currentTls = defaultTls;

            if (activeAcc != null) {
                activeAccountId = activeAcc.getId();
                currentHost = activeAcc.getHost();
                currentPort = activeAcc.getPort();
                String configJson = activeAcc.getConfigJson();
                if (configJson != null && !configJson.isBlank()) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode node = mapper.readTree(configJson);
                        if (node.has("username")) {
                            currentUser = node.get("username").asText();
                        }
                        if (node.has("password")) {
                            currentPass = node.get("password").asText();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse configJson for account {}: {}", activeAcc.getAccountName(), e.getMessage());
                    }
                }
                currentTls = activeAcc.getTlsEnabled() != null ? activeAcc.getTlsEnabled() : currentTls;
                log.info("Using database AMQP account: {}", activeAcc.getAccountName());
            } else {
                activeAccountId = null;
                if (currentHost == null || currentHost.isBlank() ||
                    currentPort == null || 
                    currentUser == null || currentUser.isBlank()) {
                    throw new java.lang.IllegalStateException("AMQP Broker configuration not found in Database and properties!");
                }
                log.warn("No active database configuration found. Falling back to configuration from application.properties.");
            }

            currentPass = decryptIfEncrypted(currentPass);

            String scheme = currentTls ? "amqps" : "amqp";
            String url = String.format("%s://%s:%d", scheme, currentHost, currentPort);

            log.info("Connecting to AMQP broker at {} as '{}'...", url, currentUser);
            updateBindStatus(BIND_CONNECTING);

            JmsConnectionFactory factory = new JmsConnectionFactory(url);
            factory.setUsername(currentUser);
            factory.setPassword(currentPass);

            Connection newConn = null;
            try {
                newConn = factory.createConnection();
                newConn.setExceptionListener(ex -> {
                    log.error("AMQP connection exception: {}", ex.getMessage());
                    connected.set(false);
                    updateBindStatus(BIND_DISCONNECTED);
                    alertService.create(GwAlert.TYPE_CONNECTION_LOST, GwAlert.SEV_CRITICAL,
                            "AMQP connection lost: " + ex.getMessage(), null, null);
                    scheduleReconnect();
                });
                newConn.start();

                // Đóng kết nối cũ trước khi gán kết nối mới để tránh rò rỉ tài nguyên
                Connection oldConn = this.connection;
                if (oldConn != null) {
                    try {
                        oldConn.close();
                        log.debug("Closed old AMQP connection");
                    } catch (Exception ignored) {}
                }

                this.connection = newConn;
                this.connected.set(true);
                updateBindStatus(BIND_CONNECTED);
                this.reconnectAttempt.set(0);

                log.info("AMQP broker connected: {}", url);
                systemLogService.log(GwAlert.SEV_INFO, "SWIM_COMPONENT",
                         "AMQP connection established: " + url);

            } catch (Exception ex) {
                if (newConn != null) {
                    try { newConn.close(); } catch (Exception ignored) {}
                }
                throw ex;
            }

        } catch (Exception e) {
            log.error("AMQP connection failed: {}", e.getMessage());
            connected.set(false);
            updateBindStatus(BIND_DISCONNECTED);
            scheduleReconnect();
        }
    }

    /**
     * Cập nhật trạng thái liên kết kết nối.
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

    /**
     * Lập lịch kết nối lại tự động khi gặp sự cố mất kết nối.
     */
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

    /**
     * Tạo session mới, sao chép kết nối cục bộ để tránh lỗi tương tranh luồng (TOCTOU).
     */
    public Session createSession() throws JMSException {
        Connection conn = this.connection;  // Bản sao cục bộ để tránh lỗi tương tranh luồng
        if (!connected.get() || conn == null) {
            throw new JMSException("AMQP not connected");
        }
        return conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    /**
     * Tạo MessageProducer gửi tin nhắn tới topic chỉ định.
     */
    public MessageProducer createProducer(Session session, String destination) throws JMSException {
        Destination dest = session.createTopic(destination);
        return session.createProducer(dest);
    }

    /**
     * Tạo MessageConsumer nhận tin nhắn từ topic chỉ định.
     */
    public MessageConsumer createConsumer(Session session, String topic) throws JMSException {
        Destination dest = session.createTopic(topic);
        // Sử dụng noLocal=true để tránh nhận lại tin nhắn do chính mình gửi lên (Echo Cancellation)
        return session.createConsumer(dest, null, true);
    }

    /**
     * Đóng kết nối AMQP khi kết thúc chương trình.
     */
    @PreDestroy
    public void shutdown() {
        try {
            if (connection != null) {
                connection.close();
                connected.set(false);
                updateBindStatus(BIND_DISCONNECTED);
                log.info("AMQP connection closed");
            }
        } catch (Exception e) {
            log.warn("Error closing AMQP connection: {}", e.getMessage());
        }
    }
}
