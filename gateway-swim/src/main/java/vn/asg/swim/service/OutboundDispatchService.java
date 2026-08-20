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
import vn.asg.swim.repository.GwoutDispatchRepository;
import vn.asg.swim.repository.GwoutRepository;

import java.time.LocalDateTime;
import java.util.List;

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
     * Thực hiện kiểm tra, phân tích bản tin AMHS và chuyển tiếp nguyên văn sang SWIM.
     * Trạng thái sau khi hoàn tất sẽ được đặt thành TRANSFORMED (2) hoặc FAILED
     * (5).
     */
    @Transactional
    public void processOutboundMessage(Gwout gwout) {
        String origin = gwout.getOrigin();
        if (origin == null || !origin.matches("^[A-Z]{8}$")) {
            log.warn("gwout#{} rejected: origin '{}' is invalid (must be 8 uppercase alphabetic characters, no digits, no spaces)",
                    gwout.getMsgid(), origin);
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setRejectionReason("invalid-origin-format");
            gwout.setRejectionDiagnostic("invalid-arguments");
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwimRejected(gwout, "invalid_origin_format", "invalid-arguments",
                    "unable to convert to AMQP due to unrecognized originator O/R address");
            return;
        }

        // CTSW011 - CTSW013: Phát hiện bản tin Probe từ AMHS
        boolean isProbe = "probe".equalsIgnoreCase(gwout.getBodyType()) || "PROBE".equalsIgnoreCase(gwout.getText());
        if (isProbe) {
            log.info("Processing AMHS Probe for gwout#{}", gwout.getMsgid());
            processAmhsProbe(gwout);
            return;
        }

        MessageValidationService.ValidationResult dirResult = validationService.validateAmhsToSwim(gwout.getText(),
                gwout.getAddress());
        if (!dirResult.isValid()) {
            log.warn("gwout#{} rejected by validation: {}", gwout.getMsgid(), dirResult.getErrorMessage());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + " rejected: " + dirResult.getErrorMessage(),
                    "gwout", gwout.getMsgid());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setRejectionReason("validation-failed");
            gwout.setRejectionDiagnostic(ndrDiagnosticFor(dirResult.getErrorMessage()));
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwimRejected(gwout, "validation_failed: " + dirResult.getErrorMessage(),
                    ndrDiagnosticFor(dirResult.getErrorMessage()), null);
            return;
        }

        if (!authorizationService.isAmhsUserAuthorized(gwout.getOrigin())) {
            log.warn("gwout#{} REJECTED: AMHS originator '{}' not authorized", gwout.getMsgid(), gwout.getOrigin());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "Unauthorized AMHS originator: " + gwout.getOrigin()
                            + " (gwout#" + gwout.getMsgid() + ")",
                    "gwout", gwout.getMsgid());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setRejectionReason("unauthorized-originator");
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "REJECTED", "unauthorized_originator: " + gwout.getOrigin());
            return;
        }

        // CTSW016: Kiểm thử EIT/Body Part Type của bản tin đi
        if (gwout.getBodyPartType() != null) {
            String rawType = gwout.getBodyPartType().trim();
            if ("401".equals(rawType)) {
                gwout.setBodyPartType("ia5-text-body-part");
            } else if ("402".equals(rawType)) {
                gwout.setBodyPartType("general-text-body-part");
            } else if ("403".equals(rawType)) {
                gwout.setBodyPartType("file-transfer-body-part");
            }

            // Tự động chuẩn hóa mã số thô thành chuỗi chuẩn ICAO trước khi xác thực
            MessageValidationService.ValidationResult eitResult = validationService.validateBodyPartType(gwout.getBodyPartType());
            if (!eitResult.isValid()) {
                log.warn("gwout#{} rejected by EIT validation: {}", gwout.getMsgid(), eitResult.getErrorMessage());
                alertService.create(
                        GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                        "gwout#" + gwout.getMsgid() + " rejected: " + eitResult.getErrorMessage(),
                        "gwout", gwout.getMsgid());
                gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
                gwout.setRejectionReason("unsupported-eit");
                gwout.setRejectionDiagnostic("content-syntax-error");
                gwoutRepository.save(gwout);
                conversionService.logAmhsToSwimRejected(gwout, "unsupported_eit: " + eitResult.getErrorMessage(),
                        "content-syntax-error", "unable to convert to AMQP due to unsupported body part type");
                return;
            }
        }

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
            gwout.setRejectionReason("type-detection-failed");
            gwout.setRejectionDiagnostic("content-syntax-error");
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "ERROR", "type_detection_failed: " + e.getMessage());
            return;
        }

        try {
            if (routingService.findBestMatchOut(messageType).isEmpty()) {
                throw new RuntimeException("No routing rule for type=" + messageType);
            }
        } catch (Exception e) {
            log.error("gwout#{} failed to find routing rule: {}", gwout.getMsgid(), e.getMessage());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setRejectionReason("no-routing-rule");
            gwout.setRejectionDiagnostic("unrecognised-OR-name");
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "ERROR", "routing_failed: " + e.getMessage());
            return;
        }

        // Giữ nguyên nội dung bản tin gốc, không convert theo chiều nào (theo ICAO Doc 047)
        try {
            gwout.setStatus(MessageStatus.OUT_TRANSFORMED.getValue());
            if (gwout.getAmhsPriority() != null) {
                gwout.setSwimPriority(vn.asg.swim.model.AmqpProperties.mapAtsPriorityToAmqp(gwout.getAmhsPriority()));
            }
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "OK", "forwarded_unchanged");
            log.info("gwout#{} forwarded unchanged -> status=OUT_TRANSFORMED", gwout.getMsgid());
        } catch (Exception e) {
            log.error("gwout#{} processing failed: {}", gwout.getMsgid(), e.getMessage());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setRejectionReason("processing-failed");
            gwout.setRejectionDiagnostic("system-failure");
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "ERROR", "processing_failed: " + e.getMessage());
        }
    }

    /**
     * Xử lý bản ghi phân phối tin đi từ hàng đợi (chỉ thực hiện publish dữ liệu đã
     * chuyển tiếp).
     */
    @Transactional
    public void processDispatch(GwoutDispatch dispatch) {
        Gwout gwout = gwoutRepository.findById(dispatch.getGwoutId()).orElse(null);
        if (gwout == null) {
            handleFailure(dispatch, GwoutDispatch.STEP_ROUTING,
                    new RuntimeException("gwout#" + dispatch.getGwoutId() + " not found"));
            return;
        }

        // Đọc nội dung bản tin gốc (không convert theo chiều nào, theo ICAO Doc 047)
        String payloadContent = gwout.getText();
        if (payloadContent == null || payloadContent.isBlank()) {
            handleFailure(dispatch, GwoutDispatch.STEP_PUBLISH,
                    new RuntimeException("text is empty for gwout#" + gwout.getMsgid()));
            return;
        }

        if (dispatch.getTopic() == null || dispatch.getTopic().isBlank()) {
            handleFailure(dispatch, GwoutDispatch.STEP_PUBLISH,
                    new RuntimeException("publish topic is empty/null for dispatch#" + dispatch.getId()));
            return;
        }

        // EUR Doc 047 §4.4.3.4.4: 1 IPM AMHS chỉ sinh ra 1 message AMQP duy nhất.
        // Gộp mọi dispatch cùng gwout + cùng topic còn sẵn sàng xử lý vào 1 lần publish,
        // amhs_recipients chứa danh sách đầy đủ (phân cách dấu phẩy) thay vì gửi trùng N lần.
        LocalDateTime now = LocalDateTime.now();
        List<GwoutDispatch> group = gwoutDispatchRepository.findByGwoutId(gwout.getMsgid()).stream()
                .filter(d -> dispatch.getTopic().equals(d.getTopic()))
                .filter(d -> GwoutDispatch.STATUS_PENDING.equals(d.getStatus())
                        || GwoutDispatch.STATUS_FAILED.equals(d.getStatus()))
                .filter(d -> d.getNextRetryAt() == null || !d.getNextRetryAt().isAfter(now))
                .toList();

        boolean stillEligible = group.stream()
                .anyMatch(d -> java.util.Objects.equals(d.getId(), dispatch.getId()));
        if (!stillEligible) {
            return; // Đã được publish gộp bởi 1 dispatch anh em khác trong cùng batch
        }

        String combinedRecipients = group.stream()
                .map(GwoutDispatch::getRecipient)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));

        for (GwoutDispatch d : group) {
            d.setStatus(GwoutDispatch.STATUS_PUBLISHING);
            gwoutDispatchRepository.save(d);
        }

        try {
            String amqpMessageId = publish(gwout, dispatch.getTopic(), combinedRecipients, payloadContent, gwout.getContentType());
            gwout.setAmqpMessageId(amqpMessageId);
            gwout.setMessageSigned("unsigned");
            gwoutRepository.save(gwout);

            LocalDateTime sentAt = LocalDateTime.now();
            for (GwoutDispatch d : group) {
                d.setStatus(GwoutDispatch.STATUS_SENT);
                d.setSentAt(sentAt);
                gwoutDispatchRepository.save(d);
            }
            log.info("gwout#{} -> {} dispatch(es) merged into 1 AMQP publish -> topic={}, recipients={}",
                    gwout.getMsgid(), group.size(), dispatch.getTopic(), combinedRecipients);

        } catch (Exception e) {
            for (GwoutDispatch d : group) {
                handleFailure(d, GwoutDispatch.STEP_PUBLISH, e);
            }
            return;
        }

        checkAndUpdateGwoutStatus(gwout.getMsgid());
    }

    /**
     * Gửi bản tin lên AMQP broker và giải phóng tài nguyên khi hoàn tất.
     * Trả về AMQP message-id do broker/SWIM component sinh ra (§4.4.3.3.1).
     */
    private String publish(Gwout gwout, String topic, String recipient,
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

            // EUR Doc 047 v3.0 §4.4.3.3.3: content-type chỉ được text/plain;charset="utf-8" hoặc application/octet-stream
            String ct = contentType != null ? contentType
                    : ("ftbp".equalsIgnoreCase(gwout.getBodyType()) ? "application/octet-stream" : "text/plain; charset=\"utf-8\"");
            message.setStringProperty("JMS_AMQP_CONTENT_TYPE", ct);

            if (gwout.getOrigin() != null) {
                message.setStringProperty("amhs_originator", gwout.getOrigin());
            }
            if (recipient != null) {
                message.setStringProperty("amhs_recipients", recipient);
            }
            // EUR Doc 047 §4.4.3.4.1: amhs_ipm_id chứa IPM-Identifier, không phải MTS-Identifier (amhsid)
            if (gwout.getIpmId() != null) {
                message.setStringProperty("amhs_ipm_id", gwout.getIpmId());
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
            // EUR Doc 047 Table 7 §4.4.3.4.9: dùng body part type đã chuẩn hóa (có thể là
            // general-text-body-part), không tự ý suy giảm về ia5-text-body-part từ bodyType thô
            String bodyPartType = gwout.getBodyPartType() != null ? gwout.getBodyPartType()
                    : ("ftbp".equalsIgnoreCase(gwout.getBodyType()) ? "file-transfer-body-part" : "ia5-text-body-part");
            message.setStringProperty("amhs_bodypart_type", bodyPartType);
            if ("file-transfer-body-part".equals(bodyPartType)) {
                // EUR Doc 047 §4.4.3.4.2 Table 4: các property FTBP là T1 (conditionally translated) —
                // chỉ gán khi biết dữ liệu thật (từ Gwout.ftbp*, nếu nguồn AMHS đã cung cấp), không bịa giá trị
                if (gwout.getFtbpFileName() != null) {
                    message.setStringProperty("amhs_ftbp_file_name", gwout.getFtbpFileName());
                }
                if (gwout.getFtbpObjectSize() != null) {
                    message.setStringProperty("amhs_ftbp_object_size", gwout.getFtbpObjectSize());
                }
                if (gwout.getFtbpLastMod() != null) {
                    message.setStringProperty("amhs_ftbp_last_mod", gwout.getFtbpLastMod());
                }
                if (gwout.getAmhsRegisteredId() != null) {
                    message.setStringProperty("amhs_registered_identifier", gwout.getAmhsRegisteredId());
                }
            } else if ("ia5-text".equals(bodyPartType) || "ia5-text-body-part".equals(bodyPartType)) {
                message.setStringProperty("amhs_content_encoding", "IA5");
            } else if ("general-text-body-part".equals(bodyPartType) && gwout.getBodyPartCharset() != null) {
                message.setStringProperty("amhs_content_encoding", gwout.getBodyPartCharset());
            }
            message.setStringProperty("amhs_message_signed", "unsigned");
            message.setStringProperty("amhs_gateway_id", configService.getGatewayId());
            // EUR Doc 047 §4.4.3.3.1: message-id shall be generated by the SWIM component, not the ITCU
            message.setJMSTimestamp(System.currentTimeMillis());

            producer.send(message);
            return message.getJMSMessageID();

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
            gwout.setRejectionReason("invalid-recipients");
            gwout.setRejectionDiagnostic("unrecognised-OR-name");
            gwoutRepository.save(gwout);
            return;
        }

        String messageType;
        try {
            messageType = detectService.detect(gwout.getText());
        } catch (Exception e) {
            log.error("gwout#{} failed to detect type in dispatch creation: {}", gwout.getMsgid(), e.getMessage());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setRejectionReason("type-detection-failed");
            gwout.setRejectionDiagnostic("content-syntax-error");
            gwoutRepository.save(gwout);
            return;
        }

        var ruleOpt = routingService.findBestMatchOut(messageType);
        if (ruleOpt.isEmpty()) {
            log.warn("gwout#{} has no routing rule matching type '{}'", gwout.getMsgid(), messageType);
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setRejectionReason("no-routing-rule");
            gwout.setRejectionDiagnostic("unrecognised-OR-name");
            gwoutRepository.save(gwout);
            return;
        }

        String topic = ruleOpt.get().getSendTopic();
        if (topic == null || topic.isBlank()) {
            log.error("gwout#{} matching routing rule has null/empty send_topic", gwout.getMsgid());
            gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
            gwout.setRejectionReason("empty-send-topic");
            gwout.setRejectionDiagnostic("system-failure");
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
     * Suy ra non-delivery-diagnostic-code theo ma trận EUR Doc 047 §4.4.2.6/§4.4.2.7
     * từ thông báo lỗi của MessageValidationService (đã gắn sẵn mã chẩn đoán trong ngoặc).
     */
    private String ndrDiagnosticFor(String errorMessage) {
        if (errorMessage == null) return null;
        if (errorMessage.contains("content-too-long")) return "content-too-long";
        if (errorMessage.contains("too-many-recipients")) return "too-many-recipients";
        return null;
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
            gwout.setRejectionReason("unknown-originator");
            gwoutRepository.save(gwout);

            conversionService.logAmhsToSwim(gwout, null, "REJECTED", "ndr_unknown_originator: " + originator);
            return;
        }

        // 2. CTSW012: Kiểm tra tính hợp lệ của Recipients
        String recipients = gwout.getAddress();
        if (recipients == null || recipients.isBlank()) {
            rejectProbe(gwout, "Recipients list is empty", "empty-recipients", null);
            return;
        }

        String[] recipientArray = recipients.trim().split("\\s+");
        for (String recipient : recipientArray) {
            var formatResult = validationService.validateAftnAddress(recipient, "Recipient");
            if (!formatResult.isValid()) {
                // EUR Doc 047 §4.4.6.5: address conversion into an AF-address failed
                rejectProbe(gwout, "Invalid recipient format: " + recipient, "invalid-recipient-format",
                        "unrecognised-OR-name");
                return;
            }

            if (!isRecipientKnown(recipient)) {
                rejectProbe(gwout, "Unknown recipient: " + recipient, "unknown-recipient", "unrecognised-OR-name");
                return;
            }
        }

        // 3. CTSW011: Hợp lệ -> Phát sinh Delivery Report (DR)
        log.info("Probe gwout#{} validated successfully. Generating Delivery Report (DR).", gwout.getMsgid());
        gwout.setStatus(MessageStatus.OUT_PUBLISHED.getValue()); // Coi như đã xử lý thành công
        gwoutRepository.save(gwout);

        conversionService.logAmhsToSwim(gwout, null, "OK", "dr_generated_probe");
    }

    private void rejectProbe(Gwout gwout, String reason, String rejectionCode, String ndrDiagnostic) {
        log.warn("Probe gwout#{} REJECTED: {}", gwout.getMsgid(), reason);
        gwout.setStatus(MessageStatus.OUT_FAILED.getValue());
        gwout.setRejectionReason(rejectionCode);
        gwout.setRejectionDiagnostic(ndrDiagnostic);
        gwoutRepository.save(gwout);
        conversionService.logAmhsToSwimRejected(gwout, "ndr_" + rejectionCode + ": " + reason, ndrDiagnostic, null);
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
