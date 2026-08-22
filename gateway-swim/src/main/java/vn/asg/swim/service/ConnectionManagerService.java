package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
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
     * Định kỳ mỗi 5 giây rà soát trạng thái tài khoản trong CSDL để tự động ngắt/kết nối lại khi có sự thay đổi từ UI.
     */
    @Scheduled(fixedDelay = 5000)
    public synchronized void monitorConnectionState() {
        var activeAcc = accountRepository.findFirstByProtocolAndStatusIgnoreCase("AMQP", "ACTIVE").orElse(null);

        if (activeAcc == null) {
            if (connected.get() || BIND_CONNECTED.equals(bindStatus)) {
                log.info("AMQP account disabled/removed in DB. Closing active connection.");
                disconnectInternal();
            }
            return;
        }

        // Chỉ tiến hành connect khi mất kết nối hoặc chuyển đổi tài khoản active.
        // Nếu kết nối đang hoat động tốt nhưng DB mang trạng thái CONNECTING -> đồng bộ DB sang CONNECTED thay vì tear-down kết nối.
        if (!connected.get() || (activeAccountId != null && !activeAccountId.equals(activeAcc.getId()))) {
            log.info("Active AMQP account change or reconnect requested for account '{}' (bindStatus={}). Connecting...",
                    activeAcc.getAccountName(), activeAcc.getBindStatus());
            connect();
        } else if (connected.get() && !BIND_CONNECTED.equals(activeAcc.getBindStatus())) {
            // Kết nối thực tế đang tốt nhưng cột bind_status trong CSDL nói khác (CONNECTING,
            // hoặc DISCONNECTED do một tiến trình khác - ví dụ test tích hợp - ghi đè lên).
            // Control Position chỉ đọc cột này nên phải đồng bộ lại, nếu không CP sẽ báo
            // "mất kết nối" vĩnh viễn cho tới lần restart kế tiếp.
            log.info("Đồng bộ lại bind_status: CSDL đang là '{}' trong khi kết nối AMQP vẫn hoạt động",
                    activeAcc.getBindStatus());
            updateBindStatus(BIND_CONNECTED);
        }
    }

    public synchronized void disconnectInternal() {
        try {
            if (connection != null) {
                try {
                    connection.setExceptionListener(null);
                } catch (Exception ignored) {}
                connection.close();
            }
        } catch (Exception ignored) {
        } finally {
            connection = null;
            connected.set(false);
            // updateBindStatus() chỉ ghi được vào CSDL khi activeAccountId còn giá trị,
            // nên phải cập nhật trạng thái TRƯỚC khi xóa nó - nếu không Control Position
            // sẽ vẫn thấy CONNECTED dù kết nối đã bị đóng.
            updateBindStatus(BIND_DISCONNECTED);
            activeAccountId = null;
        }
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
            // amqp.idleTimeout=120000 (2 phút) giữ kết nối thông suốt khi rảnh;
            // failover:(...)?failover.maxReconnectAttempts=-1 tự động khôi phục kết nối ngầm khi mạng giật lag.
            String baseAmqpUrl = String.format("%s://%s:%d?amqp.idleTimeout=120000&transport.tcpKeepAlive=true&amqp.saslMechanisms=PLAIN",
                    scheme, currentHost, currentPort);
            String url = String.format("failover:(%s)?failover.maxReconnectAttempts=-1&failover.initialReconnectDelay=2000&failover.reconnectDelay=2000&failover.maxReconnectDelay=10000",
                    baseAmqpUrl);

            log.info("Connecting to AMQP broker at {} as '{}'...", url, currentUser);
            updateBindStatus(BIND_CONNECTING);

            JmsConnectionFactory factory = new JmsConnectionFactory(url);
            factory.setUsername(currentUser);
            factory.setPassword(currentPass);

            Connection newConn = null;
            try {
                newConn = factory.createConnection();
                final Connection targetConn = newConn;
                newConn.setExceptionListener(ex -> {
                    if (this.connection != targetConn && targetConn != null) {
                        log.debug("Ignored exception on superseded AMQP connection: {}", ex.getMessage());
                        return;
                    }
                    log.error("AMQP connection exception: {}", ex.getMessage());
                    connected.set(false);
                    updateBindStatus(BIND_DISCONNECTED);
                    alertService.create(GwAlert.TYPE_CONNECTION_LOST, GwAlert.SEV_CRITICAL,
                            "AMQP connection lost: " + ex.getMessage(), null, null);
                    scheduleReconnect();
                });
                newConn.start();

                // Đóng kết nối cũ trước khi gán kết nối mới để tránh rò rỉ tài nguyên, hủy ExceptionListener cũ để tránh cascade loop
                Connection oldConn = this.connection;
                if (oldConn != null && oldConn != newConn) {
                    try {
                        oldConn.setExceptionListener(null);
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
                // Kết nối đã khôi phục -> đóng các cảnh báo mất kết nối còn treo,
                // nếu không Control Position vẫn báo đỏ cho tới khi có người bấm tay.
                alertService.resolveOpenAlerts(GwAlert.TYPE_CONNECTION_LOST,
                        "AMQP connection restored: " + url);

            } catch (Exception ex) {
                if (newConn != null) {
                    try {
                        newConn.setExceptionListener(null);
                        newConn.close();
                    } catch (Exception ignored) {}
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
        if (connected.get()) {
            return;
        }
        int attempt = reconnectAttempt.incrementAndGet();
        long delay = Math.min(1000L * (1L << Math.min(attempt - 1, 5)), MAX_BACKOFF_MS);
        log.warn("Scheduling AMQP reconnect in {}ms (attempt #{})", delay, attempt);
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delay);
                if (!connected.get()) {
                    connect();
                }
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
