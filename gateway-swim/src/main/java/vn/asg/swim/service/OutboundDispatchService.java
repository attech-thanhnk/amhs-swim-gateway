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
 * Xử lý tiến trình gửi tin đi từ hàng đợi gwout_dispatch (chiều AMHS sang SWIM).
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
     * Thực hiện kiểm tra, phân tích và chuyển đổi bản tin AMHS sang định dạng SWIM (JSON).
     * Trạng thái sau khi hoàn tất sẽ được đặt thành TRANSFORMED (2) hoặc FAILED (4).
     */
    @Transactional
    public void convertOutboundMessage(Gwout gwout) {
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
            conversionService.logAmhsToSwim(gwout, null, "REJECTED", "validation_failed: " + dirResult.getErrorMessage());
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

        String convertedBody;
        try {
            if (Boolean.TRUE.equals(rule.getConvertToJson())) {
                boolean isAlreadyJson = gwout.getPayloadContent() != null && gwout.getPayloadContent().trim().startsWith("{");
                if (!isAlreadyJson) {
                    convertedBody = conversionService.toSwim(body, messageType);
                    gwout.setPayloadContent(convertedBody);
                }
            } else {
                log.debug("Routing rule for {} specifies TAC output. Forwarding original body.", messageType);
                if (gwout.getPayloadContent() == null || gwout.getPayloadContent().isBlank() || gwout.getPayloadContent().trim().startsWith("{")) {
                    gwout.setPayloadContent(body);
                }
            }
            gwout.setStatus(MessageStatus.OUT_TRANSFORMED.getValue()); // Thành công -> TRANSFORMED
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
     * Xử lý bản ghi phân phối tin đi từ hàng đợi (chỉ thực hiện publish dữ liệu đã convert).
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

            TextMessage message = session.createTextMessage(body);
            String ct = contentType != null ? contentType : "application/json";
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
            if (gwout.getPriority() != null) {
                String atsPri = vn.asg.swim.model.AmqpProperties.mapPriorityToAts(gwout.getPriority());
                message.setStringProperty("amhs_ats_pri", atsPri);
                message.setJMSPriority(gwout.getPriority());
            }
            if (gwout.getFilingTime() != null) {
                message.setStringProperty("amhs_ats_ft", gwout.getFilingTime());
            }
            if (gwout.getOptionalHeading() != null) {
                message.setStringProperty("amhs_ats_ohi", gwout.getOptionalHeading());
            }
            if (gwout.getBodyType() != null) {
                String bodyPartType = "text".equals(gwout.getBodyType())
                        ? "ia5-text-body-part"
                        : "file-transfer-body-part";
                message.setStringProperty("amhs_bodypart_type", bodyPartType);
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
                try { producer.close(); } catch (Exception ignored) {}
            }
            if (session != null) {
                try { session.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Xử lý lỗi phân phối bản tin, tự động lập lịch retry hoặc chuyển thành trạng thái DEAD.
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
     * Kiểm tra trạng thái toàn bộ các bản ghi phân phối để cập nhật trạng thái chung của bản tin gốc (Gwout).
     */
    private void checkAndUpdateGwoutStatus(Long gwoutId) {
        List<GwoutDispatch> all = gwoutDispatchRepository.findByGwoutId(gwoutId);
        if (all.isEmpty()) return;

        boolean allDone = all.stream()
                .allMatch(d -> GwoutDispatch.STATUS_SENT.equals(d.getStatus())
                        || GwoutDispatch.STATUS_DEAD.equals(d.getStatus()));
        if (!allDone) return;

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

        gwout.setStatus(MessageStatus.OUT_PUBLISHING.getValue());
        gwoutRepository.save(gwout);
        log.debug("gwout#{} -> {} dispatch(es) created with topic={}", gwout.getMsgid(), recipients.size(), topic);
    }
}
