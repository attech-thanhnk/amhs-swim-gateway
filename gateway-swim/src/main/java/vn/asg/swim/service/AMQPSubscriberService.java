package vn.asg.swim.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.Gwin;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.MessageStatus;
import vn.asg.swim.model.ResolvedAddressing;
import vn.asg.swim.repository.GwinRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nhận bản tin SWIM, kiểm tra hợp lệ và lưu vào bảng gwin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AMQPSubscriberService {

    private final ConnectionManagerService connectionManager;
    private final RoutingService routingService;
    private final MessageConversionService conversionService;
    private final AddressingResolverService addressingResolver;
    private final AlertService alertService;
    private final GwinRepository gwinRepository;
    private final MessageValidationService validationService;
    private final AuthorizationService authorizationService;
    private final AtsmhsServiceLevelResolver atsmhsResolver;
    private final ConfigService configService;
    private final MessageDetectService detectService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // Tập hợp an toàn đa luồng để tránh ConcurrentModificationException khi
    // subscribe/unsubscribe đồng thời
    private final List<Session> activeSessions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile List<String> currentSubscribedQueues = new CopyOnWriteArrayList<>();

    /**
     * Bắt đầu tiến trình subscribe bất đồng bộ sau khi khởi tạo.
     */
    @PostConstruct
    public void startSubscribing() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            subscribeAll();
        });
        t.setDaemon(true);
        t.setName("amqp-subscriber-init");
        t.start();
    }

    /**
     * Thực hiện subscribe toàn bộ các queue/topic inbound đang hoạt động.
     */
    public synchronized void subscribeAll() {
        if (!connectionManager.getConnected().get()) {
            log.warn("AMQP not connected, subscriber will retry later");
            return;
        }

        stopAll();
        running.set(true);

        List<String> queues = routingService.getActiveInboundTopics();
        if (queues.isEmpty()) {
            log.warn("No active inbound queues found for subscription");
            currentSubscribedQueues.clear();
            return;
        }

        for (String queue : queues) {
            subscribeQueue(queue);
        }
        currentSubscribedQueues = new CopyOnWriteArrayList<>(queues);
        log.info("Subscribed to {} queues: {}", queues.size(), queues);
    }

    /**
     * Subscribe queue: khởi tạo session và consumer, giải phóng nếu lỗi.
     */
    private void subscribeQueue(String queue) {
        Session session = null;
        try {
            session = connectionManager.createSession();
            MessageConsumer consumer = connectionManager.createConsumer(session, queue);
            consumer.setMessageListener(msg -> {
                try {
                    handleMessage(msg, queue);
                } catch (Exception e) {
                    log.error("Error handling AMQP message from queue {}: {}", queue, e.getMessage(), e);
                }
            });

            // Chỉ add vào list khi subscribe thành công
            activeSessions.add(session);
            log.debug("Subscribed successfully to queue: {}", queue);

        } catch (JMSException e) {
            log.error("Failed to subscribe to queue {}: {}", queue, e.getMessage());
            // Giải phóng session nếu subscribe thất bại
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Kiểm tra tính hợp lệ bản tin AMQP, phân giải địa chỉ và ghi vào Gwin.
     */
    @Transactional
    public void handleMessage(Message amqpMsg, String queue) throws JMSException {
        String amqpMsgId = amqpMsg.getJMSMessageID();
        if (amqpMsgId == null || amqpMsgId.isBlank()) {
            if (configService.isStrictComplianceMode()) {
                log.error("SWIM message rejected: Mandatory field 'message-id' is missing");
                alertService.create("VALIDATION_ERROR", "ERROR",
                        "Message rejected: Mandatory field 'message-id' (JMSMessageID) is missing",
                        "gwin", null);
                return;
            } else {
                amqpMsgId = "GW-GEN-" + UUID.randomUUID().toString();
                log.info("AMQP message has no JMSMessageID, generated synthetic ID: {}", amqpMsgId);
            }
        }
        log.info("Received AMQP message: {} from topic: {}", amqpMsgId, queue);

        // Chống lặp bản tin (Loopback prevention).
        String originGw = amqpMsg.getStringProperty("amhs_gateway_id");
        if (configService.getGatewayId().equals(originGw)) {
            log.info("Loopback detected for message {}. Dropping message to prevent infinite loop.", amqpMsgId);
            return;
        }

        // Loại bỏ bản tin trùng lặp (Deduplication).
        if (gwinRepository.existsByMessageId(amqpMsgId)) {
            log.warn("AMQP message {} already exists in gwin. Ignoring duplicate.", amqpMsgId);
            return;
        }

        // Kiểm tra quyền hạn người dùng (Authorization).
        if (!authorizationService.isSwimUserAuthorized(amqpMsg)) {
            log.warn("AMQP message {} UNAUTHORIZED - rejected by authorization policy", amqpMsgId);
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_WARNING,
                    "Unauthorized SWIM message rejected: " + amqpMsgId,
                    "gwin", null);
            conversionService.logSwimToAmhs(amqpMsgId, null, "REJECTED", "unauthorized",
                    "SWIM user not authorized");
            return;
        }

        // 0. Trích xuất body bản tin.
        String textPayload = null;
        byte[] binaryPayload = null;
        if (amqpMsg instanceof TextMessage tm) {
            textPayload = tm.getText();
        } else if (amqpMsg instanceof BytesMessage bm) {
            byte[] buf = new byte[(int) bm.getBodyLength()];
            bm.readBytes(buf);
            // Chuyển đổi sang text nếu có định dạng XML/JSON/Plain text.
            String asUtf8 = new String(buf, StandardCharsets.UTF_8).stripLeading();
            if (asUtf8.startsWith("<") || isProbablyText(asUtf8)) {
                textPayload = asUtf8;
            } else {
                binaryPayload = buf;
            }
        } else {
            log.warn("Unsupported message type {} for {}, storing empty body",
                    amqpMsg.getClass().getSimpleName(), amqpMsgId);
        }

        String finalContent = textPayload != null ? textPayload
                : (binaryPayload != null ? Base64.getEncoder().encodeToString(binaryPayload) : null);

        // Kiểm tra tính hợp lệ bản tin theo EUR Doc 047.
        MessageValidationService.ValidationResult validationResult = validationService.validateSwimToAmhs(amqpMsgId,
                amqpMsg,
                finalContent);

        if (!validationResult.isValid()) {
            log.error("AMQP message {} validation FAILED: {}", amqpMsgId, validationResult.getErrorMessage());

            // Từ chối bản tin, tạo log và cảnh báo nếu validation thất bại.
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_ERROR,
                    "Message validation failed: " + amqpMsgId + " - " + validationResult.getErrorMessage(),
                    "gwin", null);

            conversionService.logSwimToAmhs(amqpMsgId, null, "REJECTED", "validation-failed",
                    validationResult.getErrorMessage());

            // Bỏ qua không lưu gwin nếu validation thất bại.
            return;
        }

        // 1. Phân giải độ ưu tiên (JMS Priority).
        int priority;
        try {
            priority = amqpMsg.getJMSPriority();
        } catch (Exception e) {
            log.warn("Cannot read JMS priority for {}, defaulting to 2", amqpMsgId);
            priority = 2;
        }

        // 2. Trích xuất Content-Type và Subject.
        String contentType = amqpMsg.getStringProperty("JMS_AMQP_CONTENT_TYPE");
        String subject = amqpMsg.getStringProperty("amhs_subject");
        if (subject == null)
            subject = "SWIM_INTERWORKING";

        // Trích xuất các thuộc tính ứng dụng AMQP theo đặc tả.
        String atsPriority = amqpMsg.getStringProperty("ats_priority");
        String amhsAtsFt = amqpMsg.getStringProperty("amhs_ats_ft");
        String amhsAtsOhi = amqpMsg.getStringProperty("amhs_ats_ohi");
        String amhsIpmId = amqpMsg.getStringProperty("amhs_ipm_id");
        String amhsBodypartType = amqpMsg.getStringProperty("amhs_bodypart_type");
        String amhsContentEncoding = amqpMsg.getStringProperty("amhs_content_encoding");
        String amhsMessageSigned = amqpMsg.getStringProperty("amhs_message_signed");

        // ats_priority ghi đè JMSPriority theo đặc tả.
        if (atsPriority != null && !atsPriority.isBlank()) {
            priority = vn.asg.swim.model.AmqpProperties.mapAtsPriorityToAmqp(atsPriority);
            log.debug("AMQP {}: ats_priority={} → priority={}", amqpMsgId, atsPriority, priority);
        }

        // Chuyển đổi các thuộc tính ứng dụng sang định dạng JSON.
        java.util.Map<String, String> props = new java.util.LinkedHashMap<>();
        if (atsPriority != null)
            props.put("ats_priority", atsPriority);
        if (amhsAtsFt != null)
            props.put("amhs_ats_ft", amhsAtsFt);
        if (amhsAtsOhi != null)
            props.put("amhs_ats_ohi", amhsAtsOhi);
        if (amhsIpmId != null)
            props.put("amhs_ipm_id", amhsIpmId);
        if (amhsBodypartType != null)
            props.put("amhs_bodypart_type", amhsBodypartType);
        if (amhsContentEncoding != null)
            props.put("amhs_content_encoding", amhsContentEncoding);
        if (amhsMessageSigned != null)
            props.put("amhs_message_signed", amhsMessageSigned);
        if (contentType != null)
            props.put("content_type", contentType);
        if (subject != null)
            props.put("subject", subject);

        String tempJson = "{}";
        try {
            tempJson = objectMapper.writeValueAsString(props);
        } catch (Exception e) {
            log.error("Failed to serialize AMQP properties to JSON for message {}: {}", amqpMsgId, e.getMessage());
        }
        final String amqpPropertiesJson = tempJson;

        // 3. Phân giải địa chỉ AMHS gửi và nhận (Addressing resolution).
        ResolvedAddressing resolved = addressingResolver.resolve(amqpMsg, queue, finalContent);

        // Kiểm tra cấp độ dịch vụ ATSMHS theo đặc tả.
        if (resolved.isResolved()) {
            String serviceLevel = atsmhsResolver.resolve(contentType, resolved.recipients());
            boolean hasBinaryContent = binaryPayload != null;

            // Chế độ BASIC không hỗ trợ nội dung nhị phân (binary).
            if (!atsmhsResolver.validateContent(serviceLevel, contentType, hasBinaryContent)) {
                log.error("AMQP message {} REJECTED: BASIC ATSMHS mode cannot handle binary content", amqpMsgId);
                alertService.create(
                        GwAlert.TYPE_VALIDATION_ERROR,
                        GwAlert.SEV_ERROR,
                        "Binary content rejected in BASIC ATSMHS mode: " + amqpMsgId,
                        "gwin", null);
                conversionService.logSwimToAmhs(amqpMsgId, resolved.originator(), "REJECTED",
                        "atsmhs-validation-failed", "Binary content not supported in BASIC mode");
                return;
            }

            log.debug("AMQP {}: ATSMHS service level = {}", amqpMsgId, serviceLevel);
        }

        // 4. Khởi tạo thực thể Gwin.
        Gwin gwin = new Gwin();
        gwin.setMessageId(amqpMsgId);
        gwin.setSource(queue);
        gwin.setSubject(subject);
        gwin.setAmqpProperties(amqpPropertiesJson); // Lưu thuộc tính AMQP.
        gwin.setPriority((byte) Math.min(Math.max(priority, 0), 9));
        gwin.setTime(LocalDateTime.now());
        gwin.setPayloadContent(finalContent);
        gwin.setBodyType("text");
        gwin.setContentType(contentType);
        gwin.setOrigin(resolved.originator());
        gwin.setAddress(resolved.recipients());
        gwin.setAddressingSource(resolved.source());

        try {
            String effectiveType = subject;
            if ("SWIM_INTERWORKING".equals(subject)) {
                String detected = detectService.detect(finalContent);
                if (!"UNKNOWN".equals(detected)) {
                    effectiveType = detected;
                }
            }

            try {
                String tac = conversionService.toAmhs(finalContent, effectiveType);
                gwin.setText(tac);
                gwin.setStatus(resolved.isResolved() ? MessageStatus.IN_PENDING.getValue() : MessageStatus.IN_UNROUTED.getValue());
            } catch (Exception e) {
                log.error("AMQP {} Conversion FAILED: {}", amqpMsgId, e.getMessage());
                gwin.setText("CONVERSION_FAILED: " + e.getMessage() + "\n" + finalContent);
                gwin.setStatus(MessageStatus.IN_UNROUTED.getValue());
            }

            try {
                gwinRepository.save(gwin);
            } catch (DataIntegrityViolationException e) {
                log.warn("AMQP message {} already exists (race condition). Ignoring.", amqpMsgId);
                return;
            }

            String actionTag = "received-" + resolved.source().toLowerCase().replaceAll("[^a-z0-9]", "_");
            conversionService.logSwimToAmhs(amqpMsgId, resolved.originator(),
                    gwin.getStatus().equals(MessageStatus.IN_PENDING.getValue()) ? "OK" : "UNROUTED",
                    actionTag,
                    resolved.isResolved() ? null : "MISSING_AMHS_RECIPIENTS",
                    amhsIpmId);

        } catch (Exception e) {
            log.error("AMQP {} Fatal Error: {}", amqpMsgId, e.getMessage());
        }
    }

    /**
     * Dừng kết nối và đóng toàn bộ session active.
     */
    public synchronized void stopAll() {
        running.set(false);
        activeSessions.forEach(s -> {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        });
        activeSessions.clear();
    }

    /**
     * Dọn dẹp tài nguyên khi bean bị hủy.
     */
    @PreDestroy
    public void destroy() {
        stopAll();
    }

    /**
     * Nhận biết dữ liệu văn bản.
     */
    private boolean isProbablyText(String s) {
        if (s == null || s.isEmpty())
            return false;
        // Đếm các ký tự điều khiển.
        long controlChars = s.chars()
                .filter(c -> c < 32 && c != '\t' && c != '\n' && c != '\r')
                .count();
        // Trả về true nếu ít hơn 5% là ký tự điều khiển.
        return controlChars < s.length() * 0.05;
    }

    /**
     * Định kỳ kiểm tra sự thay đổi cấu hình định tuyến inbound để re-subscribe.
     */
    @Scheduled(fixedDelay = 10000)
    public void checkRoutingChanges() {
        if (!running.get() || !connectionManager.getConnected().get()) {
            return;
        }
        try {
            List<String> activeQueues = routingService.getActiveInboundTopics();
            if (!activeQueues.equals(currentSubscribedQueues)) {
                log.info("Detected changes in active inbound routing rules. Current subscribed: {}, New active: {}. Re-subscribing...", 
                        currentSubscribedQueues, activeQueues);
                subscribeAll();
            }
        } catch (Exception e) {
            log.error("Error checking routing changes for re-subscription: {}", e.getMessage());
        }
    }
}
