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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        // Trích xuất body bản tin ban đầu
        String textPayload = null;
        byte[] binaryPayload = null;
        if (amqpMsg instanceof TextMessage tm) {
            textPayload = tm.getText();
        } else if (amqpMsg instanceof BytesMessage bm) {
            byte[] buf = new byte[(int) bm.getBodyLength()];
            bm.readBytes(buf);
            if (buf.length >= 2 && ((buf[0] == (byte) 0xFF && buf[1] == (byte) 0xFE) || (buf[0] == (byte) 0xFE && buf[1] == (byte) 0xFF))) {
                textPayload = new String(buf, StandardCharsets.UTF_16);
            } else {
                String text = new String(buf, StandardCharsets.UTF_8);
                if (text.stripLeading().startsWith("<") || isProbablyText(text.stripLeading())) {
                    textPayload = text;
                } else {
                    textPayload = text.replace("\u0000", "");
                    binaryPayload = buf;
                }
            }
        }

        String finalContent = textPayload != null ? textPayload
                : (binaryPayload != null ? Base64.getEncoder().encodeToString(binaryPayload) : null);

        // Phân tích cú pháp JSON root Node sơ bộ để phát hiện Envelope JSON
        JsonNode root = null;
        if (finalContent != null && finalContent.trim().startsWith("{")) {
            try {
                root = objectMapper.readTree(finalContent);
            } catch (Exception ignored) {}
        }

        boolean isEnvelopeJson = false;
        if (root != null) {
            isEnvelopeJson = root.has("properties") || root.has("application-properties")
                    || root.has("applicationProperties") || root.has("application_properties")
                    || root.has("header");
        }

        String swimCompression = getAppProperty(amqpMsg, root, isEnvelopeJson, "swim_compression");
        if (swimCompression == null || swimCompression.isBlank()) {
            swimCompression = getAppProperty(amqpMsg, root, isEnvelopeJson, "swim-compression");
        }

        boolean payloadConflict = false;
        boolean payloadMissing = false;
        boolean payloadMismatch = false;

        if (isEnvelopeJson && root != null) {
            JsonNode amqpValNode = root.get("amqp-value");
            if (amqpValNode == null || amqpValNode.isNull()) amqpValNode = root.get("amqp_value");
            JsonNode dataNode = root.get("data");

            boolean hasAmqpValue = amqpValNode != null && !amqpValNode.isNull();
            boolean hasData = dataNode != null && !dataNode.isNull();

            if (hasAmqpValue && hasData) {
                payloadConflict = true;
                log.warn("AMQP Envelope Payload conflict: both amqp-value and data are present");
            } else if (!hasAmqpValue && !hasData) {
                payloadMissing = true;
                log.warn("AMQP Envelope Payload missing: both amqp-value and data are empty");
            } else if (hasAmqpValue) {
                if (amqpValNode.isContainerNode()) {
                    try {
                        textPayload = objectMapper.writeValueAsString(amqpValNode);
                    } catch (Exception e) {
                        textPayload = amqpValNode.toString();
                    }
                } else {
                    textPayload = amqpValNode.asText();
                }
                binaryPayload = null;
                finalContent = textPayload;
            } else { // hasData
                String dataStr = dataNode.asText();
                if (dataStr.matches("^[0-9a-fA-F]+$") && dataStr.length() % 2 == 0) {
                    binaryPayload = hexStringToByteArray(dataStr);
                } else {
                    binaryPayload = dataStr.getBytes(StandardCharsets.UTF_8);
                }
                
                if ("gzip".equalsIgnoreCase(swimCompression) && binaryPayload != null && binaryPayload.length > 0) {
                    binaryPayload = decompressGzip(binaryPayload);
                }
                
                textPayload = new String(binaryPayload, StandardCharsets.UTF_8);
                finalContent = textPayload;
            }
        } else {
            // Không phải Envelope JSON, kiểm tra nén GZIP trực tiếp
            if ("gzip".equalsIgnoreCase(swimCompression) && binaryPayload != null && binaryPayload.length > 0) {
                binaryPayload = decompressGzip(binaryPayload);
                textPayload = new String(binaryPayload, StandardCharsets.UTF_8);
                finalContent = textPayload;
            }
        }

        // Tái phân tích cú pháp JSON root Node từ nội dung giải nén/chuyển đổi cuối cùng
        root = null;
        if (finalContent != null && finalContent.trim().startsWith("{")) {
            try {
                root = objectMapper.readTree(finalContent);
            } catch (Exception ignored) {}
        }

        // Trích xuất các trường dữ liệu tiêu chuẩn
        String contentType = getMsgProperty(amqpMsg, root, isEnvelopeJson, "content-type");
        if (contentType == null || contentType.isBlank()) {
            contentType = getMsgProperty(amqpMsg, root, isEnvelopeJson, "contentType");
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = getMsgProperty(amqpMsg, root, isEnvelopeJson, "content_type");
        }

        boolean contentTypeSupported = true;
        if (contentType != null && !contentType.isBlank()) {
            String ct = contentType.toLowerCase();
            if (!ct.contains("text/plain") && !ct.contains("application/json") && !ct.contains("application/octet-stream")) {
                contentTypeSupported = false;
                log.warn("AMQP: Unsupported content-type '{}'", contentType);
            }
            if (ct.contains("text/") || ct.contains("json") || ct.contains("xml")) {
                binaryPayload = null;
                if (finalContent != null) {
                    finalContent = finalContent.replace("\u0000", "");
                }
            }
        }

        if (isEnvelopeJson && contentType != null && !contentType.isBlank()) {
            boolean hasAmqpValue = root != null && (root.has("amqp-value") || root.has("amqp_value"));
            boolean hasData = root != null && root.has("data");
            if ((contentType.toLowerCase().contains("text/") || contentType.toLowerCase().contains("json")) && hasData && !hasAmqpValue) {
                payloadMismatch = true;
                log.warn("AMQP Payload mismatch: content-type is text/json but only binary data is present");
            }
        }

        boolean dataValid = (finalContent != null && !finalContent.isBlank()) && !payloadConflict && !payloadMissing && !payloadMismatch;

        // 1. Trích xuất messageId
        String rawMsgId = null;
        if (isEnvelopeJson && root != null) {
            // If envelope, it must be in properties
            rawMsgId = getMsgProperty(amqpMsg, root, isEnvelopeJson, "message-id");
            if (rawMsgId == null || rawMsgId.isBlank()) rawMsgId = getMsgProperty(amqpMsg, root, isEnvelopeJson, "messageId");
            if (rawMsgId == null || rawMsgId.isBlank()) rawMsgId = getMsgProperty(amqpMsg, root, isEnvelopeJson, "message_id");
        } else {
            // Direct flight plan json has messageId inside root
            if (root != null) {
                String[] jsonFields = { "messageId", "message_id", "amhs_message_id", "message-id" };
                for (String field : jsonFields) {
                    JsonNode n = root.get(field);
                    if (n != null && !n.isNull() && !n.asText().isBlank()) {
                        rawMsgId = n.asText().trim();
                        break;
                    }
                }
            }
            if (rawMsgId == null || rawMsgId.isBlank()) {
                rawMsgId = amqpMsg.getJMSMessageID();
            }
            if (rawMsgId == null || rawMsgId.isBlank()) {
                String[] properties = { "message_id", "messageId", "amhs_message_id", "message-id" };
                for (String property : properties) {
                    rawMsgId = amqpMsg.getStringProperty(property);
                    if (rawMsgId != null && !rawMsgId.isBlank()) {
                        break;
                    }
                }
            }
        }

        boolean hasMessageId = (rawMsgId != null && !rawMsgId.isBlank());
        String amqpMsgId = hasMessageId ? rawMsgId : null;
        log.info("Received AMQP message: {} from topic: {}", amqpMsgId, queue);

        // Chống lặp bản tin (Loopback prevention)
        String originGw = amqpMsg.getStringProperty("amhs_gateway_id");
        if (configService.getGatewayId().equals(originGw)) {
            log.info("Loopback detected for message {}. Dropping message to prevent infinite loop.", amqpMsgId);
            return;
        }

        // Loại bỏ bản tin trùng lặp (Deduplication)
        if (amqpMsgId != null && !amqpMsgId.isBlank() && gwinRepository.existsByMessageId(amqpMsgId)) {
            log.warn("AMQP message {} already exists in gwin. Ignoring duplicate.", amqpMsgId);
            return;
        }

        // Kiểm tra quyền hạn người dùng (Authorization)
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

        // 2. Phân giải độ ưu tiên
        Integer rawPriority = null;
        boolean priorityValid = true;
        boolean priorityFound = false;

        // 2.1 Kiểm tra amhs_ats_pri / ats_priority trước
        String atsPriProp = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_ats_pri");
        if (atsPriProp == null || atsPriProp.isBlank()) atsPriProp = getAppProperty(amqpMsg, root, isEnvelopeJson, "ats_priority");
        if (atsPriProp != null && !atsPriProp.isBlank()) {
            String trimmed = atsPriProp.trim().toUpperCase();
            if (trimmed.equals("SS") || trimmed.equals("DD") || trimmed.equals("FF") || trimmed.equals("GG") || trimmed.equals("KK")) {
                rawPriority = vn.asg.swim.model.AmqpProperties.mapAtsPriorityToAmqp(trimmed);
                priorityFound = true;
            } else {
                priorityValid = false;
                log.warn("AMQP {}: Invalid ATS priority string '{}'", amqpMsgId, atsPriProp);
            }
        }

        // 2.2 JSON root priority
        if (!priorityFound && root != null && !isEnvelopeJson) {
            JsonNode priNode = root.get("priority");
            if (priNode == null || priNode.isNull()) priNode = root.get("amqpPriority");
            if (priNode == null || priNode.isNull()) priNode = root.get("priority ");
            if (priNode != null && !priNode.isNull()) {
                priorityFound = true;
                String txt = priNode.asText().trim();
                if (txt.matches("-?\\d+")) {
                    rawPriority = Integer.parseInt(txt);
                } else {
                    priorityValid = false;
                    log.warn("AMQP {}: JSON priority is text/non-numeric '{}'", amqpMsgId, txt);
                }
            }
        }

        // 2.3 AMQP Properties priority
        if (!priorityFound) {
            try {
                String amqpPriStr = getMsgProperty(amqpMsg, root, isEnvelopeJson, "amqpPriority");
                if (amqpPriStr == null || amqpPriStr.isBlank()) amqpPriStr = getMsgProperty(amqpMsg, root, isEnvelopeJson, "amqp_priority");
                if (amqpPriStr == null || amqpPriStr.isBlank()) amqpPriStr = getMsgProperty(amqpMsg, root, isEnvelopeJson, "priority");
                
                if (amqpPriStr != null && !amqpPriStr.isBlank()) {
                    priorityFound = true;
                    String trimmed = amqpPriStr.trim();
                    if (trimmed.matches("-?\\d+")) {
                        rawPriority = Integer.parseInt(trimmed);
                    } else {
                        priorityValid = false;
                        log.warn("AMQP {}: Priority property is text/non-numeric '{}'", amqpMsgId, amqpPriStr);
                    }
                }
            } catch (Exception e) {
                log.warn("Error reading priority property for {}: {}", amqpMsgId, e.getMessage());
            }
        }

        // 2.4 JMSPriority header fallback
        if (!priorityFound) {
            try {
                int jmsPri = amqpMsg.getJMSPriority();
                if (jmsPri >= 0) {
                    rawPriority = jmsPri;
                    priorityFound = true;
                }
            } catch (Exception ignored) {}
        }

        // 2.5 Range check 0-9
        if (priorityValid && priorityFound && rawPriority != null) {
            if (rawPriority < 0 || rawPriority > 9) {
                priorityValid = false;
                log.warn("AMQP {}: Priority value {} is out of range 0-9", amqpMsgId, rawPriority);
            }
        } else if (!priorityFound) {
            priorityValid = false;
            log.warn("AMQP {}: Mandatory priority field is missing", amqpMsgId);
        }

        int priority = (priorityValid && rawPriority != null) ? rawPriority : 2;
        String atsPriority = priorityValid ? vn.asg.swim.model.AmqpProperties.mapPriorityToAmhs(priority) : null;

        // 3. Trích xuất & kiểm tra creation-time
        boolean creationTimeValid = true;
        String amhsAtsFt = null;
        boolean creationTimeFieldFound = false;
        boolean creationTimeFieldIsNull = false;
        boolean creationTimeFieldIsZero = false;

        JsonNode tsNode = null;
        if (isEnvelopeJson && root != null) {
            JsonNode propsNode = root.get("properties");
            if (propsNode != null && !propsNode.isNull()) {
                tsNode = propsNode.get("creation-time");
                if (tsNode == null) tsNode = propsNode.get("creationTime");
                if (tsNode == null) tsNode = propsNode.get("creation_time");
                if (tsNode == null) tsNode = propsNode.get("timestamp");
            }
        } else if (root != null) {
            tsNode = root.get("creationTime");
            if (tsNode == null || tsNode.isNull()) tsNode = root.get("dof");
            if (tsNode == null || tsNode.isNull()) tsNode = root.get("timestamp");
        }

        if (tsNode != null) {
            creationTimeFieldFound = true;
            if (tsNode.isNull()) {
                creationTimeFieldIsNull = true;
            } else if (tsNode.isNumber()) {
                long val = tsNode.asLong();
                if (val == 0) {
                    creationTimeFieldIsZero = true;
                } else if (val > 1000000000L) { // Epoch MS
                    LocalDateTime dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(val), java.time.ZoneOffset.UTC);
                    amhsAtsFt = dt.format(java.time.format.DateTimeFormatter.ofPattern("ddHHmm"));
                } else { // dof like 260706
                    String txt = String.valueOf(val);
                    if (txt.length() == 6) {
                        String yy = txt.substring(0, 2);
                        String mm = txt.substring(2, 4);
                        String dd = txt.substring(4, 6);
                        amhsAtsFt = dd + mm + yy;
                    } else {
                        amhsAtsFt = txt;
                    }
                }
            } else {
                String txt = tsNode.asText().trim();
                if (txt.isBlank() || "null".equalsIgnoreCase(txt)) {
                    creationTimeFieldIsNull = true;
                } else if ("0".equals(txt) || "000000".equals(txt)) {
                    creationTimeFieldIsZero = true;
                } else {
                    boolean isDof = (root != null && root.has("dof") && tsNode == root.get("dof"));
                    if (isDof && txt.length() == 6) {
                        try {
                            String yy = txt.substring(0, 2);
                            String mm = txt.substring(2, 4);
                            String dd = txt.substring(4, 6);
                            amhsAtsFt = dd + mm + yy;
                        } catch (Exception e) {
                            amhsAtsFt = txt;
                        }
                    } else {
                        amhsAtsFt = txt;
                    }
                }
            }
        }

        if (!creationTimeFieldFound) {
            String atsFt = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_ats_ft");
            if (atsFt == null || atsFt.isBlank()) atsFt = getAppProperty(amqpMsg, root, isEnvelopeJson, "creation_time");
            if (atsFt == null || atsFt.isBlank()) atsFt = getAppProperty(amqpMsg, root, isEnvelopeJson, "creation-time");

            if (atsFt != null && !atsFt.isBlank()) {
                creationTimeFieldFound = true;
                String trimmed = atsFt.trim();
                if ("0".equals(trimmed) || "000000".equals(trimmed) || "null".equalsIgnoreCase(trimmed)) {
                    creationTimeFieldIsZero = true;
                } else {
                    amhsAtsFt = trimmed;
                }
            }
        }

        if (!creationTimeFieldFound) {
            try {
                long jmsTimestamp = amqpMsg.getJMSTimestamp();
                if (jmsTimestamp > 0) {
                    creationTimeFieldFound = true;
                    LocalDateTime dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(jmsTimestamp), java.time.ZoneOffset.UTC);
                    amhsAtsFt = dt.format(java.time.format.DateTimeFormatter.ofPattern("ddHHmm"));
                }
            } catch (Exception ignored) {}
        }

        if (creationTimeFieldFound) {
            if (creationTimeFieldIsNull || creationTimeFieldIsZero) {
                creationTimeValid = false;
                log.warn("AMQP {}: creationTime field found but invalid (null or zero)", amqpMsgId);
            }
        } else {
            amhsAtsFt = LocalDateTime.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("ddHHmm"));
        }

        // 4. Trích xuất các thuộc tính tiêu chuẩn khác
        String subject = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_subject");
        if (subject == null || subject.isBlank()) {
            subject = getMsgProperty(amqpMsg, root, isEnvelopeJson, "subject");
        }
        if (subject == null || subject.isBlank()) {
            subject = "SWIM_INTERWORKING";
        }
        if (subject.length() > 128) {
            subject = subject.substring(0, 128);
        }

        String amhsAtsOhi = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_ats_ohi");
        if (amhsAtsOhi != null && !amhsAtsOhi.isBlank()) {
            int maxOhiLen = (priority >= 5) ? 48 : 53;
            if (amhsAtsOhi.length() > maxOhiLen) {
                amhsAtsOhi = amhsAtsOhi.substring(0, maxOhiLen);
            }
        }

        String amhsIpmId = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_ipm_id");
        String amhsBodypartType = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_bodypart_type");
        String amhsContentEncoding = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_content_encoding");
        String amhsMessageSigned = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_message_signed");

        String amhsFtbpFileName = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_ftbp_file_name");
        String amhsFtbpObjectSize = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_ftbp_object_size");
        String amhsFtbpLastMod = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_ftbp_last_mod");
        String amhsRegisteredIdentifier = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_registered_identifier");
        String amhsUserVisibleString = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_user_visible_string");
        
        String notificationRequests = null;
        List<String> notifList = getAppPropertyAsList(amqpMsg, root, isEnvelopeJson, "notification_requests");
        if (notifList == null || notifList.isEmpty()) {
            notifList = getAppPropertyAsList(amqpMsg, root, isEnvelopeJson, "notification-requests");
        }
        if (notifList != null && !notifList.isEmpty()) {
            notificationRequests = String.join(",", notifList);
        }

        // Phân giải địa chỉ
        ResolvedAddressing resolved = addressingResolver.resolve(amqpMsg, queue, finalContent);

        // 5. Trích xuất người nhận (Recipients Fallback Chain)
        // Step 1: Lấy từ AMQP Header / Envelope Application Properties
        List<String> recipientsList = getAppPropertyAsList(amqpMsg, root, isEnvelopeJson, "amhs_recipients");
        if (recipientsList.isEmpty()) {
            recipientsList = getAppPropertyAsList(amqpMsg, root, isEnvelopeJson, "recipients");
        }
        if (recipientsList.isEmpty()) {
            recipientsList = getAppPropertyAsList(amqpMsg, root, isEnvelopeJson, "addressees");
        }

        // Step 2: Lấy từ Direct JSON Payload body (nếu payload là JSON trực tiếp)
        if (recipientsList.isEmpty() && root != null && !isEnvelopeJson && root.hasNonNull("recipients")) {
            JsonNode recipientNode = root.get("recipients");
            String amhsRecipientsValue = recipientNode.asText().trim();
            if (!amhsRecipientsValue.isEmpty()) {
                String[] parts = amhsRecipientsValue.split("[,\\s]+");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isBlank() && !recipientsList.contains(trimmed)) {
                        recipientsList.add(trimmed);
                    }
                }
            }
        }

        // Step 3: Fallback sang kết quả phân giải địa chỉ (AddressingResolver)
        if (recipientsList.isEmpty() && resolved != null && resolved.recipients() != null) {
            String[] parts = resolved.recipients().trim().split("\\s+");
            for (String part : parts) {
                if (!part.isBlank() && !recipientsList.contains(part)) {
                    recipientsList.add(part);
                }
            }
        }

        boolean recipientsValid = true;
        if (recipientsList.isEmpty()) {
            recipientsValid = false;
            log.warn("AMQP {}: Mandatory recipients field is missing or empty", amqpMsgId);
        } else {
            int maxRecipients = configService.getMaxMsgRecipients();
            if (recipientsList.size() > maxRecipients) {
                recipientsValid = false;
                log.warn("AMQP {}: Recipients count {} exceeds maximum {}", amqpMsgId, recipientsList.size(), maxRecipients);
            }
        }

        String amhsRecipients = String.join(" ", recipientsList);

        // Phân giải originator nâng cao
        String amhsOriginator = getAppProperty(amqpMsg, root, isEnvelopeJson, "amhs_originator");
        if (amhsOriginator == null || amhsOriginator.isBlank()) {
            amhsOriginator = getAppProperty(amqpMsg, root, isEnvelopeJson, "originator");
        }
        if (amhsOriginator == null || amhsOriginator.isBlank()) {
            amhsOriginator = resolved != null ? resolved.originator() : null;
        }
        if (amhsOriginator == null || amhsOriginator.isBlank() || "UNKNOWNX".equalsIgnoreCase(amhsOriginator)) {
            amhsOriginator = configService.getDefaultOriginator();
            log.warn("AMQP {}: Originator is unknown or invalid. Using default originator: {}", amqpMsgId, amhsOriginator);
        }

        // Cập nhật lại resolved với originator và recipients hoàn chỉnh
        if (resolved == null || !resolved.isResolved()) {
            resolved = new ResolvedAddressing(amhsOriginator, amhsRecipients, ResolvedAddressing.SOURCE_AMQP_PROPERTY);
        } else {
            resolved = new ResolvedAddressing(amhsOriginator, amhsRecipients, resolved.source());
        }

        // Chuyển đổi các thuộc tính ứng dụng sang định dạng JSON
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
        if (amhsFtbpFileName != null)
            props.put("amhs_ftbp_file_name", amhsFtbpFileName);
        if (amhsFtbpObjectSize != null)
            props.put("amhs_ftbp_object_size", amhsFtbpObjectSize);
        if (amhsFtbpLastMod != null)
            props.put("amhs_ftbp_last_mod", amhsFtbpLastMod);
        if (amhsRegisteredIdentifier != null)
            props.put("amhs_registered_identifier", amhsRegisteredIdentifier);
        if (amhsUserVisibleString != null)
            props.put("amhs_user_visible_string", amhsUserVisibleString);
        if (notificationRequests != null)
            props.put("notification_requests", notificationRequests);
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

        // Kiểm tra tính hợp lệ bản tin theo EUR Doc 047
        MessageValidationService.ValidationResult validationResult = validationService.validateSwimToAmhs(
                amqpMsgId != null ? amqpMsgId : "",
                amqpMsg,
                finalContent);

        boolean isCompliant = hasMessageId && priorityValid && creationTimeValid && dataValid && recipientsValid && contentTypeSupported && validationResult.isValid();

        // NẾU KHÔNG THỎA MÃN 1 TRONG CÁC ĐIỀU KIỆN, VẪN LƯU VÀO GWIN VỚI STATUS = 4 (IN_FAILED)
        if (!isCompliant) {
            List<String> errors = new ArrayList<>();
            if (!hasMessageId) errors.add("Missing messageId");
            if (!priorityValid) errors.add("Invalid priority: " + rawPriority + " (must be 0-9)");
            if (!creationTimeValid) errors.add("Invalid creation-time (must be != 0)");
            if (!dataValid) {
                if (payloadConflict) errors.add("Payload conflict - both amqp-value and data are present");
                else if (payloadMissing) errors.add("Payload missing - both amqp-value and data are empty");
                else if (payloadMismatch) errors.add("Payload mismatch - content-type is text/json but only binary data is present");
                else errors.add("Mandatory field 'data/amqp-value' is missing or empty");
            }
            if (!recipientsValid) errors.add("Mandatory field 'amhs_recipients' is missing or empty (or count exceeds max)");
            if (!contentTypeSupported) errors.add("Unsupported content-type: " + contentType);
            if (!validationResult.isValid()) errors.addAll(validationResult.getErrors());

            String errorMessage = String.join("; ", errors);
            log.error("AMQP message {} validation FAILED: {}", amqpMsgId, errorMessage);

            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_ERROR,
                    "Message validation failed: " + amqpMsgId + " - " + errorMessage,
                    "gwin", null);

            conversionService.logSwimToAmhs(amqpMsgId, resolved.originator(), "REJECTED", "validation-failed",
                    errorMessage);

            Gwin failedGwin = new Gwin();
            failedGwin.setMessageId(amqpMsgId);
            failedGwin.setSource(queue);
            failedGwin.setSubject(subject);
            failedGwin.setAmhsRecipients(amhsRecipients);
            failedGwin.setAmqpProperties(amqpPropertiesJson);
            failedGwin.setPriority((byte) Math.min(Math.max(priority, 0), 9));
            failedGwin.setTime(LocalDateTime.now());
            failedGwin.setPayloadContent(finalContent);
            String bodyType = "file-transfer-body-part".equalsIgnoreCase(amhsBodypartType) ? "ftbp" : "text";
            failedGwin.setBodyType(bodyType);
            failedGwin.setContentType(contentType);
            failedGwin.setOrigin(resolved.originator());
            failedGwin.setAddress(resolved.recipients());
            failedGwin.setAddressingSource(resolved.source());
            failedGwin.setText("VALIDATION_FAILED: " + errorMessage);
            failedGwin.setStatus(MessageStatus.IN_FAILED.getValue()); // status = 4

            try {
                gwinRepository.save(failedGwin);
            } catch (DataIntegrityViolationException e) {
                log.warn("AMQP message {} already exists (race condition). Ignoring.", amqpMsgId);
            }
            return;
        }

        // Kiểm tra cấp độ dịch vụ ATSMHS theo đặc tả
        if (resolved.isResolved()) {
            // Use resolved service level override if available
            String overrideMode = getAppProperty(amqpMsg, root, isEnvelopeJson, "atsmhs_service_level");
            if (overrideMode == null || overrideMode.isBlank()) {
                overrideMode = getAppProperty(amqpMsg, root, isEnvelopeJson, "atsmhs-service-level");
            }
            
            String serviceLevel = atsmhsResolver.resolve(overrideMode, contentType, resolved.recipients());
            boolean hasBinaryContent = binaryPayload != null;

            // Chế độ BASIC không hỗ trợ nội dung nhị phân (binary)
            if (!atsmhsResolver.validateContent(serviceLevel, contentType, hasBinaryContent)) {
                log.error("AMQP message {} REJECTED: BASIC ATSMHS mode cannot handle binary content", amqpMsgId);
                alertService.create(
                        GwAlert.TYPE_VALIDATION_ERROR,
                        GwAlert.SEV_ERROR,
                        "Binary content rejected in BASIC ATSMHS mode: " + amqpMsgId,
                        "gwin", null);
                conversionService.logSwimToAmhs(amqpMsgId, resolved.originator(), "REJECTED",
                        "atsmhs-validation-failed", "Binary content not supported in BASIC mode");

                Gwin failedGwin = new Gwin();
                failedGwin.setMessageId(amqpMsgId);
                failedGwin.setSource(queue);
                failedGwin.setSubject(subject);
                failedGwin.setAmhsRecipients(amhsRecipients);
                failedGwin.setAmqpProperties(amqpPropertiesJson);
                failedGwin.setPriority((byte) Math.min(Math.max(priority, 0), 9));
                failedGwin.setTime(LocalDateTime.now());
                failedGwin.setPayloadContent(finalContent);
                String bodyType = "file-transfer-body-part".equalsIgnoreCase(amhsBodypartType) ? "ftbp" : "text";
                failedGwin.setBodyType(bodyType);
                failedGwin.setContentType(contentType);
                failedGwin.setOrigin(resolved.originator());
                failedGwin.setAddress(resolved.recipients());
                failedGwin.setAddressingSource(resolved.source());
                failedGwin.setText("ATSMHS_VALIDATION_FAILED: Binary content not supported in BASIC mode");
                failedGwin.setStatus(MessageStatus.IN_FAILED.getValue()); // status = 4

                try {
                    gwinRepository.save(failedGwin);
                } catch (DataIntegrityViolationException e) {
                    log.warn("AMQP message {} already exists (race condition). Ignoring.", amqpMsgId);
                }
                return;
            }

            log.debug("AMQP {}: ATSMHS service level = {}", amqpMsgId, serviceLevel);
        }

        // Khởi tạo thực thể Gwin cho bản tin hợp lệ
        Gwin gwin = new Gwin();
        gwin.setMessageId(amqpMsgId);
        gwin.setSource(queue);
        gwin.setSubject(subject);
        gwin.setAmhsRecipients(amhsRecipients);
        gwin.setAmqpProperties(amqpPropertiesJson);
        gwin.setPriority((byte) Math.min(Math.max(priority, 0), 9));
        gwin.setTime(LocalDateTime.now());
        gwin.setPayloadContent(finalContent);
        String bodyType = "file-transfer-body-part".equalsIgnoreCase(amhsBodypartType) ? "ftbp" : "text";
        gwin.setBodyType(bodyType);
        gwin.setContentType(contentType);
        if (resolved != null) {
            gwin.setOrigin(resolved.originator());
            gwin.setAddress(resolved.recipients());
            gwin.setAddressingSource(resolved.source());
        }

        try {
            String effectiveType = subject;
            if (root != null) {
                if (root.hasNonNull("messageType")) {
                    effectiveType = root.get("messageType").asText().trim();
                } else if (root.hasNonNull("message_type")) {
                    effectiveType = root.get("message_type").asText().trim();
                } else if (root.hasNonNull("msgType")) {
                    effectiveType = root.get("msgType").asText().trim();
                }
            }
            if ("SWIM_INTERWORKING".equalsIgnoreCase(effectiveType) || effectiveType == null || effectiveType.isBlank()) {
                String detected = detectService.detect(finalContent);
                if (!"UNKNOWN".equals(detected)) {
                    effectiveType = detected;
                }
            }

            try {
                String tac = conversionService.toAmhs(finalContent, effectiveType);
                gwin.setText(tac);
                gwin.setStatus(resolved != null && resolved.isResolved() ? MessageStatus.IN_PENDING.getValue() : MessageStatus.IN_UNROUTED.getValue());
            } catch (Exception e) {
                log.error("AMQP {} Conversion FAILED: {}", amqpMsgId, e.getMessage());
                gwin.setText("CONVERSION_FAILED: " + e.getMessage() + "\n" + finalContent);
                gwin.setStatus(MessageStatus.IN_FAILED.getValue());
                alertService.create(
                        GwAlert.TYPE_CONVERT_ERROR,
                        GwAlert.SEV_WARNING,
                        "Conversion failed: " + e.getMessage(),
                        "gwin", null);
            }

            try {
                gwinRepository.save(gwin);
            } catch (DataIntegrityViolationException e) {
                log.warn("AMQP message {} already exists (race condition). Ignoring.", amqpMsgId);
                return;
            }

            String actionTag = "received-" + (resolved != null ? resolved.source().toLowerCase().replaceAll("[^a-z0-9]", "_") : "unresolved");
            conversionService.logSwimToAmhs(amqpMsgId, resolved != null ? resolved.originator() : null,
                    gwin.getStatus().equals(MessageStatus.IN_PENDING.getValue()) ? "OK" : "UNROUTED",
                    actionTag,
                    (resolved != null && resolved.isResolved()) ? null : "MISSING_AMHS_RECIPIENTS",
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

    private String getMsgProperty(Message msg, JsonNode root, boolean isEnvelopeJson, String key) {
        if (isEnvelopeJson && root != null) {
            JsonNode props = root.get("properties");
            if (props != null && !props.isNull()) {
                JsonNode n = props.get(key);
                if (n == null) n = props.get(key.replace("-", "_"));
                if (n == null) n = props.get(key.replace("_", "-"));
                if (n != null && !n.isNull()) {
                    return n.asText();
                }
            }
        }
        try {
            if ("content-type".equals(key)) {
                String ct = msg.getStringProperty("JMS_AMQP_CONTENT_TYPE");
                if (ct != null) return ct;
            }
            String val = msg.getStringProperty(key);
            if (val == null) val = msg.getStringProperty(key.replace("-", "_"));
            if (val == null) val = msg.getStringProperty(key.replace("_", "-"));
            return val;
        } catch (Exception e) {
            return null;
        }
    }

    private String getAppProperty(Message msg, JsonNode root, boolean isEnvelopeJson, String key) {
        if (isEnvelopeJson && root != null) {
            JsonNode appProps = root.get("application-properties");
            if (appProps == null || appProps.isNull()) appProps = root.get("applicationProperties");
            if (appProps == null || appProps.isNull()) appProps = root.get("application_properties");
            if (appProps != null && !appProps.isNull()) {
                JsonNode n = appProps.get(key);
                if (n == null) n = appProps.get(key.replace("-", "_"));
                if (n == null) n = appProps.get(key.replace("_", "-"));
                if (n != null && !n.isNull()) {
                    return n.asText();
                }
            }
        }
        try {
            String val = msg.getStringProperty(key);
            if (val == null) val = msg.getStringProperty(key.replace("-", "_"));
            if (val == null) val = msg.getStringProperty(key.replace("_", "-"));
            return val;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> getAppPropertyAsList(Message msg, JsonNode root, boolean isEnvelopeJson, String key) {
        List<String> list = new ArrayList<>();
        if (isEnvelopeJson && root != null) {
            JsonNode appProps = root.get("application-properties");
            if (appProps == null || appProps.isNull()) appProps = root.get("applicationProperties");
            if (appProps == null || appProps.isNull()) appProps = root.get("application_properties");
            if (appProps != null && !appProps.isNull()) {
                JsonNode n = appProps.get(key);
                if (n == null) n = appProps.get(key.replace("-", "_"));
                if (n == null) n = appProps.get(key.replace("_", "-"));
                if (n != null && !n.isNull()) {
                    if (n.isArray()) {
                        n.forEach(item -> {
                            if (item != null && !item.isNull()) list.add(item.asText());
                        });
                        return list;
                    } else {
                        list.add(n.asText());
                        return list;
                    }
                }
            }
        }
        try {
            String val = msg.getStringProperty(key);
            if (val == null) val = msg.getStringProperty(key.replace("-", "_"));
            if (val == null) val = msg.getStringProperty(key.replace("_", "-"));
            if (val != null && !val.isBlank()) {
                String[] parts = val.trim().split("[,\\s]+");
                for (String part : parts) {
                    if (!part.isBlank()) list.add(part);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private byte[] decompressGzip(byte[] compressed) {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(compressed);
             java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(bais);
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to decompress GZIP data: {}", e.getMessage());
            return compressed;
        }
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
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
