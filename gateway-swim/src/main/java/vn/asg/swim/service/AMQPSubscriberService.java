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
import vn.asg.swim.entity.InboundStatus;
import vn.asg.swim.model.ResolvedAddressing;
import vn.asg.swim.repository.GwinRepository;
import vn.asg.swim.util.AmqpMessageIdUtil;

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
    private final AlertService alertService;
    private final GwinRepository gwinRepository;
    private final MessageValidationService validationService;
    private final AuthorizationService authorizationService;
    private final AtsmhsServiceLevelResolver atsmhsResolver;
    private final ConfigService configService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
                    try {
                        alertService.create(
                                GwAlert.TYPE_MESSAGE_DEAD,
                                GwAlert.SEV_ERROR,
                                "[SWIM->AMHS] Message dropped, unhandled error on queue " + queue + ": "
                                        + e.getMessage(),
                                "gwin", null);
                    } catch (Exception alertFailure) {
                        log.error("Failed to raise Control Position alert for dropped message: {}",
                                alertFailure.getMessage());
                    }
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
        // Đọc content-type trước để xác định kiểu nội dung
        String contentType = getMsgProperty(amqpMsg, "content-type");
        if (contentType == null || contentType.isBlank()) {
            contentType = getMsgProperty(amqpMsg, "contentType");
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = getMsgProperty(amqpMsg, "content_type");
        }
        boolean declaredBinary = contentType != null
                && contentType.toLowerCase().contains("application/octet-stream");
        boolean declaredText = !declaredBinary && contentType != null
                && contentType.toLowerCase().contains("text/");

        String textPayload = null;
        byte[] binaryPayload = null;
        String bodyDecodeError = null;
        if (amqpMsg instanceof TextMessage tm) {
            try {
                textPayload = tm.getText();
            } catch (JMSException e) {
                bodyDecodeError = "content-type/content mismatch: payload is not valid UTF-8";
                log.warn("AMQP: content-type declares text but body bytes are not decodable as declared charset: {}",
                        e.getMessage());
            }
        } else if (amqpMsg instanceof BytesMessage bm) {
            byte[] buf = new byte[(int) bm.getBodyLength()];
            bm.readBytes(buf);
            if (declaredBinary) {
                binaryPayload = buf;
            } else if (declaredText) {
                try {
                    textPayload = strictUtf8Decode(buf);
                } catch (java.nio.charset.CharacterCodingException e) {
                    bodyDecodeError = "content-type/content mismatch: payload is not valid UTF-8";
                    log.warn("AMQP: content-type declares '{}' but body bytes are not valid UTF-8", contentType);
                }
            } else if (buf.length >= 2 && ((buf[0] == (byte) 0xFF && buf[1] == (byte) 0xFE) || (buf[0] == (byte) 0xFE && buf[1] == (byte) 0xFF))) {
                textPayload = new String(buf, StandardCharsets.UTF_16);
            } else {
                String text = new String(buf, StandardCharsets.UTF_8);
                if (text.stripLeading().startsWith("<") || isProbablyText(text.stripLeading())) {
                    textPayload = text;
                } else {
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
            finalContent = Base64.getEncoder().encodeToString(binaryPayload);
        }

        // Kiểm tra tính hợp lệ của content-type
        boolean contentTypeSupported = (bodyDecodeError == null);
        String contentTypeError = bodyDecodeError;
        if (contentType == null || contentType.isBlank()) {
            contentTypeSupported = false;
            contentTypeError = "Mandatory field 'content-type' is missing";
            log.warn("AMQP: Mandatory content-type property is missing");
        } else {
            String ct = contentType.toLowerCase();
            if (!ct.contains("text/plain") && !ct.contains("application/octet-stream")) {
                contentTypeSupported = false;
                contentTypeError = "Unsupported content-type: " + contentType;
                log.warn("AMQP: Unsupported content-type '{}'", contentType);
            }
            if (ct.contains("utf-16")) {
                contentTypeSupported = false;
                contentTypeError = "Unsupported charset in content-type (only utf-8): " + contentType;
                log.warn("AMQP: Unsupported charset utf-16 in content-type '{}'", contentType);
            }
            if (ct.contains("text/")) {
                if (finalContent != null) {
                    finalContent = finalContent.replace("\u0000", "");
                }
            }
        }

        boolean dataValid = bodyDecodeError != null || (finalContent != null && !finalContent.isBlank());

        // 1. Trích xuất messageId — chỉ từ header/properties tầng AMQP, không bao giờ lấy từ payload nghiệp vụ
        String rawMsgId = cleanAmqpMessageId(amqpMsg.getJMSMessageID());
        if (rawMsgId == null) {
            String[] properties = { "message_id", "messageId", "amhs_message_id" };
            for (String property : properties) {
                rawMsgId = cleanAmqpMessageId(safeGetStringProperty(amqpMsg, property));
                if (rawMsgId != null) {
                    break;
                }
            }
        }

        boolean hasMessageId = (rawMsgId != null);
        String amqpMsgId = rawMsgId;
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
                    "[SWIM->AMHS] Unauthorized SWIM message rejected: " + amqpMsgId,
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
                String trimmed = atsFt.trim();
                boolean placeholder = "0".equals(trimmed) || "000000".equals(trimmed) || "null".equalsIgnoreCase(trimmed);
                if (!placeholder && trimmed.matches("^\\d{6}$")) {
                    creationTimeFieldFound = true;
                    amhsAtsFt = trimmed;
                } else if (!placeholder) {
                    try {
                        long epochMs = Long.parseLong(trimmed);
                        if (epochMs > 0) {
                            creationTimeFieldFound = true;
                            LocalDateTime dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), java.time.ZoneOffset.UTC);
                            amhsAtsFt = dt.format(java.time.format.DateTimeFormatter.ofPattern("ddHHmm"));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (!creationTimeFieldFound) {
                    log.warn("AMQP {}: amhs_ats_ft '{}' sai định dạng - dùng creation-time của AMQP thay thế",
                            amqpMsgId, trimmed);
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
            creationTimeValid = false;
            log.warn("AMQP {}: Mandatory creation-time field is missing", amqpMsgId);
        }

        String subject = getAppProperty(amqpMsg, "amhs_subject");
        if (subject == null || subject.isBlank()) {
            subject = getMsgProperty(amqpMsg, "subject");
        }
        if (subject != null && subject.length() > 128) {
            subject = subject.substring(0, 128);
        }

        String amhsAtsOhi = getAppProperty(amqpMsg, "amhs_ats_ohi");
        if (amhsAtsOhi != null && !amhsAtsOhi.isBlank()) {
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

        if (amhsRegisteredIdentifier != null && !amhsRegisteredIdentifier.isBlank()
                && !vn.asg.swim.model.AmqpProperties.isDefaultRegisteredIdentifier(amhsRegisteredIdentifier)
                && (amhsUserVisibleString == null || amhsUserVisibleString.isBlank())) {
            log.warn("AMQP {}: amhs_registered_identifier '{}' is non-default OID but missing amhs_user_visible_string",
                    amqpMsgId, amhsRegisteredIdentifier);
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_WARNING,
                    "[SWIM->AMHS] AMQP " + amqpMsgId + ": amhs_registered_identifier '" + amhsRegisteredIdentifier
                            + "' is not default OID but missing amhs_user_visible_string",
                    "gwin", null);
        }
        
        String notificationRequests = null;
        List<String> notifList = getAppPropertyAsList(amqpMsg, "notification_requests");
        if (notifList == null || notifList.isEmpty()) {
            notifList = getAppPropertyAsList(amqpMsg, "notification-requests");
        }
        if (notifList != null && !notifList.isEmpty()) {
            notificationRequests = String.join(",", notifList);
        }

        List<String> recipientsList = getAppPropertyAsList(amqpMsg, "amhs_recipients");

        boolean recipientsValid = true;
        String recipientsError = null;
        if (recipientsList.isEmpty()) {
            recipientsValid = false;
            recipientsError = "Mandatory field 'amhs_recipients' is missing or empty";
            log.warn("AMQP {}: Mandatory amhs_recipients field is missing or empty", amqpMsgId);
        } else {
            int maxRecipients = configService.getMaxMsgRecipients();
            if (maxRecipients > 0 && recipientsList.size() > maxRecipients) {
                recipientsValid = false;
                recipientsError = String.format("Recipients count %d in 'amhs_recipients' exceeds maximum %d",
                        recipientsList.size(), maxRecipients);
                log.warn("AMQP {}: Recipients count {} exceeds maximum {}", amqpMsgId, recipientsList.size(), maxRecipients);
            }
        }

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
                        "[SWIM->AMHS] AMQP " + amqpMsgId + ": unrecognised recipient(s) dropped: " + invalidRecipients,
                        "gwin", null);
            }
            if (validRecipients.isEmpty()) {
                recipientsValid = false;
                recipientsError = "None of the recipients in 'amhs_recipients' could be translated to an AF-address";
                log.warn("AMQP {}: none of the recipients could be translated to an AF-address", amqpMsgId);
            } else {
                recipientsList = validRecipients;
            }
        }

        String amhsRecipients = String.join(" ", recipientsList);

        String amhsOriginator = getAppProperty(amqpMsg, "amhs_originator");
        if (amhsOriginator == null || amhsOriginator.isBlank()
                || !validationService.validateAftnAddress(amhsOriginator, "amhs_originator").isValid()) {
            String invalidOriginator = amhsOriginator;
            amhsOriginator = configService.getDefaultOriginator();
            log.warn("AMQP {}: Originator '{}' is unknown or invalid. Using default originator: {}",
                    amqpMsgId, invalidOriginator, amhsOriginator);
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_WARNING,
                    "[SWIM->AMHS] AMQP " + amqpMsgId + ": invalid/unknown amhs_originator '" + invalidOriginator
                            + "', falling back to default originator " + amhsOriginator,
                    "gwin", null);
        }

        ResolvedAddressing resolved = new ResolvedAddressing(amhsOriginator, amhsRecipients,
                recipientsValid ? ResolvedAddressing.SOURCE_AMQP_PROPERTY : ResolvedAddressing.SOURCE_UNRESOLVED);

        String atsmhsServiceLevel = configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL);
        if (atsmhsServiceLevel == null || atsmhsServiceLevel.isBlank()) {
            atsmhsServiceLevel = "CONTENT_BASED";
        }
        atsmhsServiceLevel = atsmhsServiceLevel.trim().toUpperCase();

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
        String amhsNotificationRequest = resolveNotificationRequest(notificationRequests, atsPriority);
        if (amhsNotificationRequest != null)
            props.put("amhs_notification_request", amhsNotificationRequest);
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

        int payloadByteSize = binaryPayload != null
                ? binaryPayload.length
                : (finalContent != null ? finalContent.getBytes(StandardCharsets.UTF_8).length : 0);
        MessageValidationService.ValidationResult validationResult = validationService.validateSwimToAmhs(
                amqpMsgId != null ? amqpMsgId : "",
                amqpMsg,
                finalContent,
                payloadByteSize);

        boolean isCompliant = hasMessageId && priorityValid && creationTimeValid && dataValid && contentTypeSupported && recipientsValid && validationResult.isValid();

        if (!isCompliant) {
            List<String> errors = new ArrayList<>();
            if (!hasMessageId) errors.add("Missing messageId");
            if (!priorityValid) errors.add("Invalid priority: " + rawPriority + " (must be 0-9)");
            if (!creationTimeValid) errors.add("Mandatory field 'creation-time' is missing or invalid");
            if (!dataValid) errors.add("Mandatory field 'data/amqp-value' is missing or empty");
            if (!contentTypeSupported) errors.add(contentTypeError != null ? contentTypeError
                    : "Unsupported content-type: " + contentType);
            if (!recipientsValid) errors.add(recipientsError != null ? recipientsError : "Mandatory field 'amhs_recipients' is missing or invalid");
            if (!validationResult.isValid()) errors.addAll(validationResult.getErrors());

            String errorMessage = String.join("; ", errors);
            log.error("AMQP message {} validation FAILED: {}", amqpMsgId, errorMessage);

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
            failedGwin.setAtsmhsServiceLevel(atsmhsServiceLevel);
            failedGwin.setRejectionReason("validation-failed");
            failedGwin.setRejectionSource("SWIM");
            failedGwin.setRejectionDiagnostic(errorMessage);
            failedGwin.setStatus(InboundStatus.FAILED.getValue()); // status = 1 (FAILED)

            Long savedMsgid = null;
            try {
                Gwin saved = gwinRepository.save(failedGwin);
                savedMsgid = saved.getMsgid();
            } catch (DataIntegrityViolationException e) {
                logGwinPersistFailure(amqpMsgId, e);
            }

            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_ERROR,
                    "[SWIM->AMHS] Message validation failed: " + amqpMsgId + " - " + errorMessage,
                    "gwin", savedMsgid);
            return;
        }

        if (resolved.isResolved()) {
            boolean hasBinaryContent = declaredBinary || binaryPayload != null;

            if (!atsmhsResolver.validateContent(atsmhsServiceLevel, contentType, hasBinaryContent)) {
                String errorDiagnostic = "Binary content not supported in BASIC mode";
                log.error("AMQP message {} REJECTED: BASIC ATSMHS mode cannot handle binary content", amqpMsgId);

                conversionService.logSwimToAmhs(amqpMsgId, resolved.originator(), "REJECTED",
                        "atsmhs-validation-failed", errorDiagnostic);

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
                failedGwin.setAtsmhsServiceLevel(atsmhsServiceLevel);
                failedGwin.setRejectionReason("atsmhs-validation-failed");
                failedGwin.setRejectionSource("SWIM");
                failedGwin.setRejectionDiagnostic(errorDiagnostic);
                failedGwin.setStatus(InboundStatus.FAILED.getValue()); // status = 1 (FAILED)

                Long savedMsgid = null;
                try {
                    Gwin saved = gwinRepository.save(failedGwin);
                    savedMsgid = saved.getMsgid();
                } catch (DataIntegrityViolationException e) {
                    logGwinPersistFailure(amqpMsgId, e);
                }

                alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_ERROR,
                    "[SWIM->AMHS] Binary content rejected in BASIC ATSMHS mode: " + amqpMsgId,
                    "gwin", savedMsgid);
                return;
            }

            log.debug("AMQP {}: ATSMHS service level = {}", amqpMsgId, atsmhsServiceLevel);
        }

        Gwin gwin = new Gwin();
        gwin.setMessageId(amqpMsgId);
        gwin.setIpmId(amhsIpmId);
        gwin.setAtsPriority(atsPriority);
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
            gwin.setAtsmhsServiceLevel(atsmhsServiceLevel);
        }

        try {
            gwin.setStatus(resolved != null && resolved.isResolved() ? InboundStatus.PENDING.getValue() : InboundStatus.UNROUTED.getValue());

            try {
                gwinRepository.save(gwin);
            } catch (DataIntegrityViolationException e) {
                logGwinPersistFailure(amqpMsgId, e);
                if (!isDuplicateKey(e)) {
                    alertService.create(
                            GwAlert.TYPE_VALIDATION_ERROR,
                            GwAlert.SEV_CRITICAL,
                            "[SWIM->AMHS] Failed to persist message " + amqpMsgId + ": " + rootCauseMessage(e),
                            "gwin", null);
                }
                return;
            }

            String actionTag = "received-" + (resolved != null ? resolved.source().toLowerCase().replaceAll("[^a-z0-9]", "_") : "unresolved");
            conversionService.logSwimToAmhs(amqpMsgId, resolved != null ? resolved.originator() : null,
                    gwin.getStatus().equals(InboundStatus.PENDING.getValue()) ? "OK" : "UNROUTED",
                    actionTag,
                    (resolved != null && resolved.isResolved()) ? null : "MISSING_AMHS_RECIPIENTS",
                    amhsIpmId,
                    gwin.getPayloadContent());


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
     * Xác định giá trị notification-requests cho IPM.
     */
    private String resolveNotificationRequest(String notificationRequests, String atsPriority) {
        if (notificationRequests != null && !notificationRequests.isBlank()) {
            List<String> normalized = new ArrayList<>();
            for (String token : notificationRequests.split(",")) {
                String value = token.trim().toLowerCase();
                if (("rn".equals(value) || "nrn".equals(value) || "ipm-return".equals(value))
                        && !normalized.contains(value)) {
                    normalized.add(value);
                }
            }
            return normalized.isEmpty() ? null : String.join(",", normalized);
        }
        return "SS".equalsIgnoreCase(atsPriority) ? "rn,nrn" : null;
    }

    /**
     * Xác định body type (ftbp hoặc text) dựa trên amhsBodypartType và contentType.
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
    private String cleanAmqpMessageId(String raw) {
        return AmqpMessageIdUtil.clean(raw);
    }

    private String safeGetStringProperty(Message msg, String key) {
        if (key == null || key.isBlank()) return null;
        try {
            String val = msg.getStringProperty(key);
            if (val != null) return val;
        } catch (Exception ignored) {}

        String altKey = key.contains("-") ? key.replace("-", "_") : key.replace("_", "-");
        if (!altKey.equals(key)) {
            try {
                return msg.getStringProperty(altKey);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String getMsgProperty(Message msg, String key) {
        if ("content-type".equals(key) || "content_type".equals(key)) {
            try {
                String ct = msg.getStringProperty("JMS_AMQP_CONTENT_TYPE");
                if (ct != null) return ct;
            } catch (Exception ignored) {}
        }
        if ("subject".equalsIgnoreCase(key)) {
            try {
                String jmsType = msg.getJMSType();
                if (jmsType != null && !jmsType.isBlank()) return jmsType;
            } catch (Exception ignored) {}
            try {
                String s = msg.getStringProperty("JMS_AMQP_SUBJECT");
                if (s != null && !s.isBlank()) return s;
            } catch (Exception ignored) {}
        }
        return safeGetStringProperty(msg, key);
    }

    private String getAppProperty(Message msg, String key) {
        return safeGetStringProperty(msg, key);
    }

    private List<String> getAppPropertyAsList(Message msg, String key) {
        List<String> list = new ArrayList<>();
        try {
            Object obj = msg.getObjectProperty(key);
            if (obj == null) {
                String altKey = key.contains("-") ? key.replace("-", "_") : key.replace("_", "-");
                if (!altKey.equals(key)) {
                    obj = msg.getObjectProperty(altKey);
                }
            }
            if (obj instanceof java.util.Collection<?> col) {
                for (Object item : col) {
                    if (item != null) {
                        String s = item.toString().trim();
                        if (!s.isBlank()) list.add(s);
                    }
                }
                return list;
            } else if (obj instanceof Object[] arr) {
                for (Object item : arr) {
                    if (item != null) {
                        String s = item.toString().trim();
                        if (!s.isBlank()) list.add(s);
                    }
                }
                return list;
            }
        } catch (Exception ignored) {}

        String val = safeGetStringProperty(msg, key);
        if (val != null && !val.isBlank()) {
            String[] parts = val.trim().split("[,\\s]+");
            for (String part : parts) {
                if (!part.isBlank()) list.add(part);
            }
        }
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
     * Phân biệt lỗi trùng khóa với các vi phạm ràng buộc khác.
     */
    private boolean isDuplicateKey(DataIntegrityViolationException e) {
        if (e instanceof org.springframework.dao.DuplicateKeyException) {
            return true;
        }
        String msg = rootCauseMessage(e);
        return msg != null && msg.toLowerCase().contains("duplicate entry");
    }

    private String rootCauseMessage(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause != null ? cause.getMessage() : e.getMessage();
    }

    private void logGwinPersistFailure(String amqpMsgId, DataIntegrityViolationException e) {
        if (isDuplicateKey(e)) {
            log.warn("AMQP message {} already exists (race condition). Ignoring.", amqpMsgId);
        } else {
            log.error("AMQP message {}: failed to persist gwin - {}", amqpMsgId, rootCauseMessage(e));
        }
    }

    /**
     * Decode UTF-8 nghiêm ngặt, báo lỗi nếu bytes không hợp lệ.
     */
    private String strictUtf8Decode(byte[] bytes) throws java.nio.charset.CharacterCodingException {
        java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }

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
        if (!connectionManager.getConnected().get()) {
            return;
        }
        try {
            List<String> activeQueues = routingService.getActiveInboundTopics();
            if (!running.get() || !activeQueues.equals(currentSubscribedQueues) || activeSessions.isEmpty()) {
                log.info("Re-subscribing inbound topics (running={}, activeQueues={}, activeSessions={})...", 
                        running.get(), activeQueues, activeSessions.size());
                subscribeAll();
            }
        } catch (Exception e) {
            log.error("Error checking routing changes for re-subscription: {}", e.getMessage());
        }
    }
}
