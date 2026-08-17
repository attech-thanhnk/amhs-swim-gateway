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

        String swimCompression = getAppProperty(amqpMsg, "swim_compression");
        if (swimCompression == null || swimCompression.isBlank()) {
            swimCompression = getAppProperty(amqpMsg, "swim-compression");
        }

        if ("gzip".equalsIgnoreCase(swimCompression) && binaryPayload != null && binaryPayload.length > 0) {
            binaryPayload = decompressGzip(binaryPayload);
            textPayload = new String(binaryPayload, StandardCharsets.UTF_8);
            finalContent = textPayload;
        }


        // Trích xuất các trường dữ liệu tiêu chuẩn
        String contentType = getMsgProperty(amqpMsg, "content-type");
        if (contentType == null || contentType.isBlank()) {
            contentType = getMsgProperty(amqpMsg, "contentType");
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = getMsgProperty(amqpMsg, "content_type");
        }

        // EUR Doc 047 v3.0 §4.4.3.3.3 / §4.5.1.6-1.7: chỉ 2 content-type hợp lệ
        boolean contentTypeSupported = true;
        if (contentType != null && !contentType.isBlank()) {
            String ct = contentType.toLowerCase();
            if (!ct.contains("text/plain") && !ct.contains("application/octet-stream")) {
                contentTypeSupported = false;
                log.warn("AMQP: Unsupported content-type '{}'", contentType);
            }
            if (ct.contains("text/")) {
                binaryPayload = null;
                if (finalContent != null) {
                    finalContent = finalContent.replace("\u0000", "");
                }
            }
        }

        boolean dataValid = (finalContent != null && !finalContent.isBlank());

        // 1. Trích xuất messageId — chỉ từ header/properties tầng AMQP, không bao giờ lấy từ payload nghiệp vụ
        String rawMsgId = amqpMsg.getJMSMessageID();
        if (rawMsgId == null || rawMsgId.isBlank()) {
            String[] properties = { "message_id", "messageId", "amhs_message_id", "message-id" };
            for (String property : properties) {
                rawMsgId = amqpMsg.getStringProperty(property);
                if (rawMsgId != null && !rawMsgId.isBlank()) {
                    break;
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
        String atsPriProp = getAppProperty(amqpMsg, "amhs_ats_pri");
        if (atsPriProp == null || atsPriProp.isBlank()) atsPriProp = getAppProperty(amqpMsg, "ats_priority");
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

        // 2.2 AMQP Properties priority
        if (!priorityFound) {
            try {
                String amqpPriStr = getMsgProperty(amqpMsg, "amqpPriority");
                if (amqpPriStr == null || amqpPriStr.isBlank()) amqpPriStr = getMsgProperty(amqpMsg, "amqp_priority");
                if (amqpPriStr == null || amqpPriStr.isBlank()) amqpPriStr = getMsgProperty(amqpMsg, "priority");
                
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

        // 2.3 JMSPriority header fallback
        if (!priorityFound) {
            try {
                int jmsPri = amqpMsg.getJMSPriority();
                if (jmsPri >= 0) {
                    rawPriority = jmsPri;
                    priorityFound = true;
                }
            } catch (Exception ignored) {}
        }

        // 2.4 Range check 0-9
        if (priorityValid && priorityFound && rawPriority != null) {
            if (rawPriority < 0 || rawPriority > 9) {
                priorityValid = false;
                log.warn("AMQP {}: Priority value {} is out of range 0-9", amqpMsgId, rawPriority);
            }
        } else if (!priorityFound) {
            priorityValid = false;
            log.warn("AMQP {}: Mandatory priority field is missing", amqpMsgId);
        }

        int priority = (priorityValid && rawPriority != null) ? rawPriority : 4;
        String atsPriority = priorityValid ? vn.asg.swim.model.AmqpProperties.mapPriorityToAmhs(priority) : null;

        // 3. Trích xuất & kiểm tra creation-time — chỉ từ properties/application-properties tầng AMQP
        boolean creationTimeValid = true;
        String amhsAtsFt = null;
        boolean creationTimeFieldFound = false;

        if (!creationTimeFieldFound) {
            String atsFt = getAppProperty(amqpMsg, "amhs_ats_ft");
            if (atsFt == null || atsFt.isBlank()) atsFt = getAppProperty(amqpMsg, "creation_time");
            if (atsFt == null || atsFt.isBlank()) atsFt = getAppProperty(amqpMsg, "creation-time");

            if (atsFt != null && !atsFt.isBlank()) {
                creationTimeFieldFound = true;
                String trimmed = atsFt.trim();
                if ("0".equals(trimmed) || "000000".equals(trimmed) || "null".equalsIgnoreCase(trimmed)) {
                    creationTimeValid = false;
                    log.warn("AMQP {}: creationTime field found but invalid (null or zero)", amqpMsgId);
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

        if (!creationTimeFieldFound) {
            amhsAtsFt = LocalDateTime.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("ddHHmm"));
        }

        // 4. Trích xuất các thuộc tính tiêu chuẩn khác
        // EUR Doc 047 §4.5.2.3: omit subject entirely if neither property is present, không gán placeholder
        String subject = getAppProperty(amqpMsg, "amhs_subject");
        if (subject == null || subject.isBlank()) {
            subject = getMsgProperty(amqpMsg, "subject");
        }
        if (subject != null && subject.length() > 128) {
            subject = subject.substring(0, 128);
        }

        String amhsAtsOhi = getAppProperty(amqpMsg, "amhs_ats_ohi");
        if (amhsAtsOhi != null && !amhsAtsOhi.isBlank()) {
            // EUR Doc 047 v3.0 §4.5.2.11 (d,e): priority < 6 -> 53 ký tự; priority >= 6 -> 48 ký tự
            int maxOhiLen = (priority >= 6) ? 48 : 53;
            if (amhsAtsOhi.length() > maxOhiLen) {
                amhsAtsOhi = amhsAtsOhi.substring(0, maxOhiLen);
            }
        }

        String amhsIpmId = getAppProperty(amqpMsg, "amhs_ipm_id");
        String amhsBodypartType = getAppProperty(amqpMsg, "amhs_bodypart_type");
        String amhsContentEncoding = getAppProperty(amqpMsg, "amhs_content_encoding");
        String amhsMessageSigned = getAppProperty(amqpMsg, "amhs_message_signed");

        String amhsFtbpFileName = getAppProperty(amqpMsg, "amhs_ftbp_file_name");
        String amhsFtbpObjectSize = getAppProperty(amqpMsg, "amhs_ftbp_object_size");
        String amhsFtbpLastMod = getAppProperty(amqpMsg, "amhs_ftbp_last_mod");
        String amhsRegisteredIdentifier = getAppProperty(amqpMsg, "amhs_registered_identifier");
        String amhsUserVisibleString = getAppProperty(amqpMsg, "amhs_user_visible_string");
        
        String notificationRequests = null;
        List<String> notifList = getAppPropertyAsList(amqpMsg, "notification_requests");
        if (notifList == null || notifList.isEmpty()) {
            notifList = getAppPropertyAsList(amqpMsg, "notification-requests");
        }
        if (notifList != null && !notifList.isEmpty()) {
            notificationRequests = String.join(",", notifList);
        }

        ResolvedAddressing resolved = addressingResolver.resolve(amqpMsg, queue);

        // 5. Trích xuất người nhận (Recipients Fallback Chain)
        // Step 1: Lấy từ AMQP Application Properties
        List<String> recipientsList = getAppPropertyAsList(amqpMsg, "amhs_recipients");
        if (recipientsList.isEmpty()) {
            recipientsList = getAppPropertyAsList(amqpMsg, "recipients");
        }
        if (recipientsList.isEmpty()) {
            recipientsList = getAppPropertyAsList(amqpMsg, "addressees");
        }


        // Step 2: Fallback sang kết quả phân giải địa chỉ (AddressingResolver)
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
            // EUR Doc 047 §3.3.2.4: 0 hoặc không cấu hình = không giới hạn
            int maxRecipients = configService.getMaxMsgRecipients();
            if (maxRecipients > 0 && recipientsList.size() > maxRecipients) {
                recipientsValid = false;
                log.warn("AMQP {}: Recipients count {} exceeds maximum {}", amqpMsgId, recipientsList.size(), maxRecipients);
            }
        }

        // EUR Doc 047 §4.5.2.9: lọc từng recipient theo format AF-address (8 ký tự).
        // Recipient lỗi CHỈ bị loại + báo Control Position, KHÔNG làm cả bản tin bị từ chối,
        // trừ khi TẤT CẢ recipient đều không dịch được (per-recipient tolerant, not all-or-nothing).
        if (recipientsValid) {
            List<String> validRecipients = new ArrayList<>();
            List<String> invalidRecipients = new ArrayList<>();
            for (String recipient : recipientsList) {
                if (validationService.validateAftnAddress(recipient, "Recipient").isValid()) {
                    validRecipients.add(recipient);
                } else {
                    invalidRecipients.add(recipient);
                }
            }
            if (!invalidRecipients.isEmpty()) {
                log.warn("AMQP {}: {} recipient(s) could not be translated to an AF-address: {}",
                        amqpMsgId, invalidRecipients.size(), invalidRecipients);
                alertService.create(
                        GwAlert.TYPE_VALIDATION_ERROR,
                        GwAlert.SEV_WARNING,
                        "AMQP " + amqpMsgId + ": unrecognised recipient(s) dropped: " + invalidRecipients,
                        "gwin", null);
            }
            if (validRecipients.isEmpty()) {
                recipientsValid = false;
                log.warn("AMQP {}: none of the recipients could be translated to an AF-address", amqpMsgId);
            } else {
                recipientsList = validRecipients;
            }
        }

        String amhsRecipients = String.join(" ", recipientsList);

        // Phân giải originator nâng cao
        String amhsOriginator = getAppProperty(amqpMsg, "amhs_originator");
        if (amhsOriginator == null || amhsOriginator.isBlank()) {
            amhsOriginator = getAppProperty(amqpMsg, "originator");
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
            if (!dataValid) errors.add("Mandatory field 'data/amqp-value' is missing or empty");
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
            String bodyType = deriveBodyType(amhsBodypartType, contentType);
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
            String overrideMode = getAppProperty(amqpMsg, "atsmhs_service_level");
            if (overrideMode == null || overrideMode.isBlank()) {
                overrideMode = getAppProperty(amqpMsg, "atsmhs-service-level");
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
                String bodyType = deriveBodyType(amhsBodypartType, contentType);
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

        Gwin gwin = new Gwin();
        gwin.setMessageId(amqpMsgId);
        gwin.setSource(queue);
        gwin.setSubject(subject);
        gwin.setAmhsRecipients(amhsRecipients);
        gwin.setAmqpProperties(amqpPropertiesJson);
        gwin.setPriority((byte) Math.min(Math.max(priority, 0), 9));
        gwin.setTime(LocalDateTime.now());
        gwin.setPayloadContent(finalContent);
        String bodyType = deriveBodyType(amhsBodypartType, contentType);
        gwin.setBodyType(bodyType);
        gwin.setContentType(contentType);
        if (resolved != null) {
            gwin.setOrigin(resolved.originator());
            gwin.setAddress(resolved.recipients());
            gwin.setAddressingSource(resolved.source());
        }

        try {
            // Giữ nguyên nội dung bản tin gốc, không convert theo chiều nào (theo ICAO Doc 047)
            gwin.setText(finalContent);
            gwin.setStatus(resolved != null && resolved.isResolved() ? MessageStatus.IN_PENDING.getValue() : MessageStatus.IN_UNROUTED.getValue());

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

    /**
     * EUR Doc 047 §4.5.2.4: khi amhs_bodypart_type có mặt (AMHS-aware), dùng trực
     * tiếp; nếu vắng mặt (AMHS-unaware), suy luận từ content-type (b): octet-stream
     * -> file-transfer-body-part, còn lại -> general-text-body-part.
     */
    private String deriveBodyType(String amhsBodypartType, String contentType) {
        if (amhsBodypartType != null) {
            return "file-transfer-body-part".equalsIgnoreCase(amhsBodypartType) ? "ftbp" : "text";
        }
        if (contentType != null && contentType.toLowerCase().contains("octet-stream")) {
            return "ftbp";
        }
        return "text";
    }

    private String getMsgProperty(Message msg, String key) {
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

    private String getAppProperty(Message msg, String key) {
        try {
            String val = msg.getStringProperty(key);
            if (val == null) val = msg.getStringProperty(key.replace("-", "_"));
            if (val == null) val = msg.getStringProperty(key.replace("_", "-"));
            return val;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> getAppPropertyAsList(Message msg, String key) {
        List<String> list = new ArrayList<>();
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

    /**
     * Nhận biết dữ liệu văn bản.
     */
    private boolean isProbablyText(String s) {
        if (s == null || s.isEmpty())
            return false;
        long controlChars = s.chars()
                .filter(c -> c < 32 && c != '\t' && c != '\n' && c != '\r')
                .count();
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
