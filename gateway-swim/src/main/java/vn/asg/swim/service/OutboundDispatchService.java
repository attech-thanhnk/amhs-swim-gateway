package vn.asg.swim.service;

import jakarta.jms.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.GwoutDispatch;
import vn.asg.swim.entity.MessageStatus;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.repository.GwoutDispatchRepository;
import vn.asg.swim.repository.GwoutRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Xử lý tiến trình gửi tin đi từ hàng đợi gwout_dispatch (chiều AMHS sang
 * SWIM).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundDispatchService {

    private final ConnectionManagerService connectionManager;
    private final MessageDetectService detectService;
    private final RoutingService routingService;
    private final MessageConversionService conversionService;
    private final MessageValidationService validationService;
    private final AuthorizationService authorizationService;
    private final ConfigService configService;
    private final AlertService alertService;
    private final GwoutDispatchRepository gwoutDispatchRepository;
    private final GwoutRepository gwoutRepository;

    /**
     * Thực hiện kiểm tra, phân tích và chuyển đổi bản tin AMHS sang định dạng SWIM
     * (JSON).
     * Trạng thái sau khi hoàn tất sẽ được đặt thành TRANSFORMED (2) hoặc FAILED
     * (4).
     */
    @Transactional
    public void convertOutboundMessage(Gwout gwout) {
        // Kiểm tra định dạng origin (8 ký tự, in hoa, không có số, không có khoảng trắng)
        String origin = gwout.getOrigin();
        if (origin == null || !origin.matches("^[A-Z]{8}$")) {
            log.warn("gwout#{} rejected: origin '{}' is invalid (must be 8 uppercase alphabetic characters, no digits, no spaces)",
                    gwout.getMsgid(), origin);
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setPayloadContent("Rejected: Originator must be exactly 8 uppercase alphabetic characters (no digits, no spaces)");
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "REJECTED", "invalid_origin_format");
            return;
        }

        // Kiểm tra xem origin có khớp với originator nào trong bảng routing (direction = OUT)
        // if (!routingService.existsOriginatorOut(origin)) {
        //     log.warn("gwout#{} rejected: origin '{}' not found in routing rules (direction = OUT)",
        //             gwout.getMsgid(), origin);
        //     gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
        //     gwout.setPayloadContent("Rejected: Origin not configured in routing rules (direction = OUT)");
        //     gwoutRepository.save(gwout);
        //     conversionService.logAmhsToSwim(gwout, null, "REJECTED", "origin_not_in_routing");
        //     return;
        // }

        // Kiểm tra kích thước bản tin (MAX_PAYLOAD_SIZE)
        int maxPayloadSize = configService.getInt("MAX_PAYLOAD_SIZE", 2097152);
        if (gwout.getText() != null && gwout.getText().length() > maxPayloadSize) {
            log.warn("gwout#{} rejected: payload size {} exceeds MAX_PAYLOAD_SIZE {}",
                    gwout.getMsgid(), gwout.getText().length(), maxPayloadSize);
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setPayloadContent("Rejected: Message size exceeds maximum allowed payload size");
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "REJECTED", "payload_size_exceeded");
            return;
        }

        // Kiểm tra Content-Type (ALLOWED_CONTENT_TYPES)
        if (gwout.getContentType() != null && !gwout.getContentType().isEmpty()) {
            List<String> allowedContentTypes;
            try {
                allowedContentTypes = configService.getCommaSeparatedConfig("ALLOWED_CONTENT_TYPES");
            } catch (Exception e) {
                allowedContentTypes = java.util.Arrays.asList("application/json", "application/xml");
            }
            String cleanContentType = gwout.getContentType().split(";")[0].trim().toLowerCase();
            boolean isAllowed = false;
            for (String allowed : allowedContentTypes) {
                if (allowed.trim().toLowerCase().equalsIgnoreCase(cleanContentType)) {
                    isAllowed = true;
                    break;
                }
            }
            if (!isAllowed) {
                log.warn("gwout#{} rejected: Content-Type '{}' is not supported",
                        gwout.getMsgid(), gwout.getContentType());
                gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
                gwout.setPayloadContent("Unsupported Content-Type");
                gwoutRepository.save(gwout);
                conversionService.logAmhsToSwim(gwout, null, "REJECTED", "unsupported_content_type");
                return;
            }
        }
        // CTSW011 - CTSW013: Phát hiện bản tin Probe từ AMHS
        boolean isProbe = "probe".equalsIgnoreCase(gwout.getBodyType()) || "PROBE".equalsIgnoreCase(gwout.getText());
        if (isProbe) {
            log.info("Processing AMHS Probe for gwout#{}", gwout.getMsgid());
            processAmhsProbe(gwout);
            return;
        }

        // 1. Kiểm tra tính hợp lệ bản tin
        MessageValidationService.ValidationResult dirResult = validationService.validateAmhsToSwim(gwout.getText(),
                gwout.getAddress());
        if (!dirResult.isValid()) {
            log.warn("gwout#{} rejected by validation: {}", gwout.getMsgid(), dirResult.getErrorMessage());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + " rejected: " + dirResult.getErrorMessage(),
                    "gwout", gwout.getMsgid());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setPayloadContent("Validation failed: " + dirResult.getErrorMessage());
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "REJECTED",
                    "validation_failed: " + dirResult.getErrorMessage());
            return;
        }

        // 2. Kiểm tra quyền của người gửi
        if (!authorizationService.isAmhsUserAuthorized(gwout.getOrigin())) {
            log.warn("gwout#{} REJECTED: AMHS originator '{}' not authorized",
                    gwout.getMsgid(), gwout.getOrigin());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "Unauthorized AMHS originator: " + gwout.getOrigin()
                            + " (gwout#" + gwout.getMsgid() + ")",
                    "gwout", gwout.getMsgid());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setPayloadContent("Authorization failed: Originator '" + gwout.getOrigin() + "' not authorized");
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "REJECTED", "unauthorized_originator: " + gwout.getOrigin());
            return;
        }

        // CTSW016: Kiểm thử EIT/Body Part Type của bản tin đi
        if (gwout.getBodyPartType() != null) {
            if ("401".equals(gwout.getBodyPartType().trim())) {
                gwout.setBodyPartType("ia5-text-body-part");
            }
            MessageValidationService.ValidationResult eitResult = validationService
                    .validateBodyPartType(gwout.getBodyPartType());
            if (!eitResult.isValid()) {
                log.warn("gwout#{} rejected by EIT validation: {}", gwout.getMsgid(), eitResult.getErrorMessage());
                alertService.create(
                        GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                        "gwout#" + gwout.getMsgid() + " rejected: " + eitResult.getErrorMessage(),
                        "gwout", gwout.getMsgid());
                gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
                gwout.setPayloadContent("EIT validation failed: " + eitResult.getErrorMessage());
                gwoutRepository.save(gwout);
                conversionService.logAmhsToSwim(gwout, null, "REJECTED",
                        "unsupported_eit: " + eitResult.getErrorMessage());
                return;
            }
        }

        // 3. Kiểm tra thời hạn hiệu lực bản tin (TTL)
        if (gwout.getAmhsTtl() != null && gwout.getAmhsTtl().isBefore(LocalDateTime.now())) {
            log.info("gwout#{} TTL expired, marking as published", gwout.getMsgid());
            gwout.setStatus(MessageStatus.OUT_PUBLISHED.getValue()); // coi như thành công nhưng skip
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "SKIP", "ttl_expired");
            return;
        }

        String body = gwout.getText();
        String messageType;
        try {
            messageType = detectService.detect(body);
        } catch (Exception e) {
            log.error("gwout#{} failed to detect message type: {}", gwout.getMsgid(), e.getMessage());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setPayloadContent("Type detection failed: " + e.getMessage());
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "ERROR", "type_detection_failed: " + e.getMessage());
            return;
        }

        Routing rule;
        try {
            var ruleOpt = routingService.findBestMatchOut(messageType);
            if (ruleOpt.isEmpty()) {
                throw new RuntimeException("No routing rule for type=" + messageType);
            }
            rule = ruleOpt.get();
        } catch (Exception e) {
            log.error("gwout#{} failed to find routing rule: {}", gwout.getMsgid(), e.getMessage());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setPayloadContent("Routing failed: " + e.getMessage());
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "ERROR", "routing_failed: " + e.getMessage());
            return;
        }

        // Bắt đầu convert
        String convertedBody;
        try {
            if (Boolean.TRUE.equals(rule.getConvertToJson())) {
                boolean isAlreadyJson = gwout.getPayloadContent() != null
                        && gwout.getPayloadContent().trim().startsWith("{");
                if (!isAlreadyJson) {
                    convertedBody = conversionService.toSwim(body, messageType, gwout.getOptionalHeading(), gwout.getSubject());
                    gwout.setPayloadContent(convertedBody);
                }
            } else {
                log.debug("Routing rule for {} specifies TAC output. Forwarding original body.", messageType);
                if (gwout.getPayloadContent() == null || gwout.getPayloadContent().isBlank()
                        || gwout.getPayloadContent().trim().startsWith("{")) {
                    gwout.setPayloadContent(body);
                }
            }
            gwout.setStatus(MessageStatus.OUT_TRANSFORMED.getValue()); // Thành công -> TRANSFORMED
            if (gwout.getAmhsPriority() != null) {
                Integer swimPri = convertToSwimPriority(gwout.getAmhsPriority());
                gwout.setSwimPriority(swimPri);
            }
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "OK", "transformed_to_json");
            log.info("gwout#{} successfully converted -> status=TRANSFORMED", gwout.getMsgid());
        } catch (Exception e) {
            log.error("gwout#{} conversion failed: {}", gwout.getMsgid(), e.getMessage());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setPayloadContent("Conversion failed: " + e.getMessage());
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "ERROR", "conversion_failed: " + e.getMessage());
        }
    }

    /**
     * Chuyển đổi số độ ưu tiên SWIM/AMQP sang danh sách mã ký tự AMHS tương ứng.
     * 
     * @param priority Số độ ưu tiên (Header.priority)
     * @return Danh sách các mã ký tự AMHS khả thi (ATS Priority)
     */
    public String convertToAmhsCodes(int priority) {
        return switch (priority) {
            case 8 -> "SS";
            case 7 -> "FF";
            case 6 -> "GG";
            default -> "KK"; // Trả về danh sách rỗng nếu số không hợp lệ
        };
    }

    /**
     * Chuyển đổi mã ký tự AMHS sang số độ ưu tiên SWIM/AMQP tương ứng.
     * 
     * @param amhsCode Mã ký tự AMHS (không phân biệt chữ hoa/thường)
     * @return Số độ ưu tiên SWIM/AMQP, hoặc null nếu mã không hợp lệ
     */
    public Integer convertToSwimPriority(String amhsCode) {
        if (amhsCode == null) {
            return null;
        }

        return switch (amhsCode.trim().toUpperCase()) {
            case "SS" -> 8;
            case "DD", "FF" -> 7;
            case "GG", "KK" -> 6;
            default -> null; // Trả về null nếu mã truyền vào không nằm trong bảng
        };
    }

    /**
     * Xử lý bản ghi phân phối tin đi từ hàng đợi (chỉ thực hiện publish dữ liệu đã
     * convert).
     */
    @Transactional
    public void processDispatch(GwoutDispatch dispatch) {
        dispatch.setStatus(GwoutDispatch.STATUS_PROCESSING);
        gwoutDispatchRepository.save(dispatch);

        Gwout gwout = gwoutRepository.findById(dispatch.getGwoutId()).orElse(null);
        if (gwout == null) {
            handleFailure(dispatch, GwoutDispatch.STEP_ROUTING,
                    new RuntimeException("gwout#" + dispatch.getGwoutId() + " not found"));
            return;
        }

        // Đọc nội dung JSON/TAC đã convert từ trước
        String convertedBody = gwout.getPayloadContent();
        if (convertedBody == null || convertedBody.isBlank()) {
            handleFailure(dispatch, GwoutDispatch.STEP_PUBLISH,
                    new RuntimeException("payload_content is empty for gwout#" + gwout.getMsgid()));
            return;
        }

        if (dispatch.getTopic() == null || dispatch.getTopic().isBlank()) {
            handleFailure(dispatch, GwoutDispatch.STEP_PUBLISH,
                    new RuntimeException("publish topic is empty/null for dispatch#" + dispatch.getId()));
            return;
        }

        // Đánh dấu trạng thái PUBLISHING trước khi gửi
        dispatch.setStatus(GwoutDispatch.STATUS_PUBLISHING);
        gwoutDispatchRepository.save(dispatch);

        try {
            publish(gwout, dispatch.getTopic(), dispatch.getRecipient(), convertedBody,
                    gwout.getContentType());

            // Gửi tin thành công -> cập nhật trạng thái SENT
            dispatch.setStatus(GwoutDispatch.STATUS_SENT);
            dispatch.setSentAt(LocalDateTime.now());
            gwoutDispatchRepository.save(dispatch);
            log.info("dispatch#{} SENT -> topic={}", dispatch.getId(), dispatch.getTopic());

        } catch (Exception e) {
            handleFailure(dispatch, GwoutDispatch.STEP_PUBLISH, e);
            return;
        }

        checkAndUpdateGwoutStatus(dispatch.getGwoutId());
    }

    /**
     * Gửi bản tin lên AMQP broker và giải phóng tài nguyên khi hoàn tất.
     */
    private void publish(Gwout gwout, String topic, String recipient,
            String body, String contentType) throws JMSException {
        Session session = null;
        MessageProducer producer = null;
        try {
            session = connectionManager.createSession();
            producer = connectionManager.createProducer(session, topic);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            Message message;
            if ("ftbp".equalsIgnoreCase(gwout.getBodyType())) {
                BytesMessage bytesMsg = session.createBytesMessage();
                byte[] binaryData;
                try {
                    binaryData = java.util.Base64.getDecoder().decode(body);
                } catch (Exception e) {
                    binaryData = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                bytesMsg.writeBytes(binaryData);
                message = bytesMsg;
            } else {
                message = session.createTextMessage(body);
            }

            String ct = contentType != null ? contentType
                    : ("ftbp".equalsIgnoreCase(gwout.getBodyType()) ? "application/octet-stream" : "application/json");
            message.setStringProperty("JMS_AMQP_CONTENT_TYPE", ct);

            if (gwout.getOrigin() != null) {
                message.setStringProperty("amhs_originator", gwout.getOrigin());
            }
            if (recipient != null) {
                message.setStringProperty("amhs_recipients", recipient);
            }
            if (gwout.getAmhsid() != null) {
                message.setStringProperty("amhs_ipm_id", gwout.getAmhsid());
            }
            if (gwout.getAmhsPriority() != null) {
                message.setStringProperty("amhs_ats_pri", gwout.getAmhsPriority());
            }
            if (gwout.getSwimPriority() != null) {
                message.setJMSPriority(gwout.getSwimPriority());
            }
            if (gwout.getFilingTime() != null) {
                message.setStringProperty("amhs_ats_ft", gwout.getFilingTime());
            }
            if (gwout.getOptionalHeading() != null) {
                message.setStringProperty("amhs_ats_ohi", gwout.getOptionalHeading());
            }
            if (gwout.getBodyType() != null) {
                if ("ftbp".equalsIgnoreCase(gwout.getBodyType())) {
                    message.setStringProperty("amhs_bodypart_type", "file-transfer-body-part");
                    message.setStringProperty("amhs_ftbp_file_name", "attachment.bin");
                    if (gwout.getAmhsRegisteredId() != null) {
                        message.setStringProperty("amhs_registered_identifier", gwout.getAmhsRegisteredId());
                    }
                } else {
                    String bodyPartType = "text".equals(gwout.getBodyType())
                            ? "ia5-text-body-part"
                            : "file-transfer-body-part";
                    message.setStringProperty("amhs_bodypart_type", bodyPartType);
                }
            }
            message.setStringProperty("amhs_content_encoding", "IA5");
            message.setStringProperty("amhs_message_signed", "unsigned");
            message.setStringProperty("amhs_gateway_id", configService.getGatewayId());
            message.setJMSMessageID(UUID.randomUUID().toString());
            message.setJMSTimestamp(System.currentTimeMillis());

            producer.send(message);

        } finally {
            // Giải phóng tài nguyên theo thứ tự ngược lại để tránh rò rỉ
            if (producer != null) {
                try {
                    producer.close();
                } catch (Exception ignored) {
                }
            }
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Xử lý lỗi phân phối bản tin, tự động lập lịch retry hoặc chuyển thành trạng
     * thái DEAD.
     */
    private void handleFailure(GwoutDispatch dispatch, String step, Exception e) {
        log.error("dispatch#{} FAILED at step={}: {}", dispatch.getId(), step, e.getMessage());
        dispatch.setLastError(step + ": " + e.getMessage());
        dispatch.setFailedStep(step);
        dispatch.setRetryCount(dispatch.getRetryCount() + 1);

        int maxRetry = configService.getInt("RETRY_MAX_COUNT");
        if (dispatch.getRetryCount() >= maxRetry) {
            dispatch.setStatus(GwoutDispatch.STATUS_DEAD);
            alertService.create(
                    GwAlert.TYPE_MESSAGE_DEAD, GwAlert.SEV_CRITICAL,
                    "gwout_dispatch#" + dispatch.getId() + " DEAD at step " + step,
                    "gwout_dispatch", dispatch.getId());
        } else {
            dispatch.setStatus(GwoutDispatch.STATUS_FAILED);
            dispatch.setNextRetryAt(calcNextRetry(dispatch.getRetryCount()));
        }
        gwoutDispatchRepository.save(dispatch);
        checkAndUpdateGwoutStatus(dispatch.getGwoutId());
    }

    /**
     * Kiểm tra trạng thái toàn bộ các bản ghi phân phối để cập nhật trạng thái
     * chung của bản tin gốc (Gwout).
     */
    private void checkAndUpdateGwoutStatus(Long gwoutId) {
        List<GwoutDispatch> all = gwoutDispatchRepository.findByGwoutId(gwoutId);
        if (all.isEmpty())
            return;

        boolean allDone = all.stream()
                .allMatch(d -> GwoutDispatch.STATUS_SENT.equals(d.getStatus())
                        || GwoutDispatch.STATUS_DEAD.equals(d.getStatus()));
        if (!allDone)
            return;

        boolean hasDead = all.stream().anyMatch(d -> GwoutDispatch.STATUS_DEAD.equals(d.getStatus()));
        gwoutRepository.findById(gwoutId).ifPresent(gwout -> {
            gwout.setStatus(hasDead ? MessageStatus.OUT_FAILED.getValue() : MessageStatus.OUT_PUBLISHED.getValue());
            gwoutRepository.save(gwout);
        });
    }

    /**
     * Tính toán thời gian thực hiện retry tiếp theo dựa trên exponential backoff.
     */
    private LocalDateTime calcNextRetry(int retryCount) {
        int[] delays = {
                configService.getInt("RETRY_DELAY_1ST_SECONDS"),
                configService.getInt("RETRY_DELAY_2ND_SECONDS"),
                configService.getInt("RETRY_DELAY_3RD_SECONDS")
        };
        int delay;
        if (retryCount <= delays.length) {
            delay = delays[retryCount - 1];
        } else {
            // Tính delay bằng exponential backoff (max mũ 6).
            int lastDelay = delays[delays.length - 1];
            delay = lastDelay * (int) Math.pow(2, Math.min(retryCount - delays.length, 6));
        }
        return LocalDateTime.now().plusSeconds(delay);
    }

    /**
     * Creates gwout_dispatch for each recipient in gwout.address.
     */
    @Transactional
    public void createDispatches(Gwout gwout) {
        String address = gwout.getAddress();
        if (address == null || address.isBlank()) {
            log.warn("gwout#{} has no recipients, skipping", gwout.getMsgid());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + " has no recipients",
                    "gwout", gwout.getMsgid());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwoutRepository.save(gwout);
            return;
        }

        List<String> recipients = java.util.Arrays.stream(address.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> {
                    if (s.matches("^[A-Z]{8}$"))
                        return true;
                    log.warn("gwout#{} contains invalid AFTN address: {}", gwout.getMsgid(), s);
                    return false;
                })
                .distinct()
                .toList();

        if (recipients.isEmpty()) {
            log.warn("gwout#{} has no valid AFTN recipients after filtering", gwout.getMsgid());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + " has no valid AFTN recipients",
                    "gwout", gwout.getMsgid());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwoutRepository.save(gwout);
            return;
        }

        // Detect message type and routing rule to resolve the correct publish topic
        String messageType;
        try {
            messageType = detectService.detect(gwout.getText());
        } catch (Exception e) {
            log.error("gwout#{} failed to detect type in dispatch creation: {}", gwout.getMsgid(), e.getMessage());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setPayloadContent("Type detection failed: " + e.getMessage());
            gwoutRepository.save(gwout);
            return;
        }

        var ruleOpt = routingService.findBestMatchOut(messageType);
        if (ruleOpt.isEmpty()) {
            log.warn("gwout#{} has no routing rule matching type '{}'", gwout.getMsgid(), messageType);
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setPayloadContent("Routing failed: No active routing rule found for type '" + messageType + "'");
            gwoutRepository.save(gwout);
            return;
        }

        String topic = ruleOpt.get().getSendTopic();
        if (topic == null || topic.isBlank()) {
            log.error("gwout#{} matching routing rule has null/empty send_topic", gwout.getMsgid());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setPayloadContent("Routing failed: send_topic is empty in matching routing rule");
            gwoutRepository.save(gwout);
            return;
        }

        for (String recipient : recipients) {
            GwoutDispatch dispatch = new GwoutDispatch();
            dispatch.setGwoutId(gwout.getMsgid());
            dispatch.setRecipient(recipient);
            dispatch.setTopic(topic);
            dispatch.setMessageType(messageType);
            dispatch.setStatus(GwoutDispatch.STATUS_PENDING);
            gwoutDispatchRepository.save(dispatch);
        }

        gwoutRepository.save(gwout);
        log.debug("gwout#{} -> {} dispatch(es) created with topic={}", gwout.getMsgid(), recipients.size(), topic);
    }

    /**
     * CTSW011 - CTSW013: Xử lý bản tin Probe nhận từ AMHS.
     */
    private void processAmhsProbe(Gwout gwout) {
        // 1. CTSW013: Kiểm tra tính hợp lệ của Originator (Xác thực)
        String originator = gwout.getOrigin();
        if (!authorizationService.isAmhsUserAuthorized(originator)) {
            log.warn("Probe gwout#{} REJECTED: AMHS originator '{}' not authorized", gwout.getMsgid(), originator);
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "Unauthorized AMHS originator for Probe: " + originator,
                    "gwout", gwout.getMsgid());

            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setPayloadContent("NDR: Unknown originator '" + originator + "'");
            gwout.setRejectionReason("unknown-originator");
            gwoutRepository.save(gwout);

            conversionService.logAmhsToSwim(gwout, null, "REJECTED", "ndr_unknown_originator: " + originator);
            return;
        }

        // 2. CTSW012: Kiểm tra tính hợp lệ của Recipients
        String recipients = gwout.getAddress();
        if (recipients == null || recipients.isBlank()) {
            rejectProbe(gwout, "Recipients list is empty", "empty-recipients");
            return;
        }

        String[] recipientArray = recipients.trim().split("\\s+");
        for (String recipient : recipientArray) {
            // Kiểm tra định dạng địa chỉ AFTN
            var formatResult = validationService.validateAftnAddress(recipient, "Recipient");
            if (!formatResult.isValid()) {
                rejectProbe(gwout, "Invalid recipient format: " + recipient, "invalid-recipient-format");
                return;
            }

            // Kiểm tra địa chỉ người nhận có tồn tại trong cấu hình định tuyến không
            if (!isRecipientKnown(recipient)) {
                rejectProbe(gwout, "Unknown recipient: " + recipient, "unknown-recipient");
                return;
            }
        }

        // 3. CTSW011: Hợp lệ -> Phát sinh Delivery Report (DR)
        log.info("Probe gwout#{} validated successfully. Generating Delivery Report (DR).", gwout.getMsgid());
        gwout.setStatus(MessageStatus.OUT_PUBLISHED.getValue()); // Coi như đã xử lý thành công
        gwout.setPayloadContent("DR: Probe verified successfully. Delivery Report generated.");
        gwoutRepository.save(gwout);

        conversionService.logAmhsToSwim(gwout, null, "OK", "dr_generated_probe");
    }

    private void rejectProbe(Gwout gwout, String reason, String rejectionCode) {
        log.warn("Probe gwout#{} REJECTED: {}", gwout.getMsgid(), reason);
        gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
        gwout.setPayloadContent("NDR: " + reason);
        gwout.setRejectionReason(rejectionCode);
        gwoutRepository.save(gwout);
        conversionService.logAmhsToSwim(gwout, null, "REJECTED", "ndr_" + rejectionCode + ": " + reason);
    }

    private boolean isRecipientKnown(String recipient) {
        // 1. Kiểm tra trong whitelist cấu hình địa chỉ AMHS
        String whitelist = configService.get("AUTHORIZED_AMHS_ADDRESSES");
        if (whitelist != null && containsExact(whitelist, recipient)) {
            return true;
        }

        // 2. Kiểm tra địa chỉ mặc định
        String defaultOrig = configService.getDefaultOriginator();
        if (defaultOrig != null && defaultOrig.equalsIgnoreCase(recipient)) {
            return true;
        }

        // 3. Kiểm tra xem có cấu hình trong bất kỳ rule IN nào không
        return routingService.isRecipientConfigured(recipient);
    }

    private boolean containsExact(String configValue, String target) {
        if (configValue == null || configValue.isBlank() || target == null || target.isBlank()) {
            return false;
        }
        String[] items = configValue.split("[,;\\s]+");
        for (String item : items) {
            if (item.trim().equalsIgnoreCase(target.trim())) {
                return true;
            }
        }
        return false;
    }
}
