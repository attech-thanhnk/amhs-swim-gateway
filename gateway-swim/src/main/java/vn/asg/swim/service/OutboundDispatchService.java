package vn.asg.swim.service;

import jakarta.jms.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.GwoutDispatch;
import vn.asg.swim.entity.OutboundStatus;
import vn.asg.swim.model.AmqpProperties;
import vn.asg.swim.repository.GwoutDispatchRepository;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.util.AmqpMessageIdUtil;

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

    /** Abstract-value X.400 của content-type duy nhất được chấp nhận. */
    private static final int IPM_1988_CONTENT_TYPE = 22;

    private final ConnectionManagerService connectionManager;
    private final RoutingService routingService;
    private final MessageConversionService conversionService;
    private final MessageValidationService validationService;
    private final AuthorizationService authorizationService;
    private final ConfigService configService;
    private final AlertService alertService;
    private final GwoutDispatchRepository gwoutDispatchRepository;
    private final GwoutRepository gwoutRepository;

    /**
     * Từ chối bản tin cho toàn bộ recipient: cập nhật trạng thái, ghi traffic log và cảnh báo.
     *
     * @param alertMessage nội dung cảnh báo gửi Control Position, null nếu nhánh này không báo
     */
    private void rejectMessage(Gwout gwout, String rejectionReason, String diagnosticCode,
            String supplementaryInfo, String actionTaken, String alertMessage) {
        if (alertMessage != null) {
            alertService.create(GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    alertMessage, "gwout", gwout.getMsgid());
        }
        gwout.setStatus(OutboundStatus.FAILED.getValue());
        gwout.setRejectionReason(rejectionReason);
        gwout.setRejectionDiagnostic(diagnosticCode);
        gwout.setRejectionSource("AMHS");
        gwoutRepository.save(gwout);
        conversionService.logAmhsToSwimRejected(gwout, actionTaken, diagnosticCode, supplementaryInfo);
    }

    /**
     * Ghi traffic log cho recipient không chuyển giao được.
     */
    private void logRecipientUndeliverable(Gwout gwout, String recipient, String actionTaken) {
        final String supplementary = "unable to convert to AMQP due to unrecognized recipient O/R address";
        conversionService.logAmhsToSwimRejected(gwout,
                actionTaken + ": " + recipient, "unrecognised-OR-name", supplementary);
    }

    /**
     * Kiểm tra bản tin AMHS và chuyển tiếp sang SWIM.
     */
    @Transactional
    public void processOutboundMessage(Gwout gwout) {
        String origin = gwout.getOrigin();
        if (!MessageValidationService.isValidAftnAddress(origin)) {
            log.warn("gwout#{} rejected: origin '{}' is invalid (must be 8 uppercase alphabetic characters, no digits, no spaces)",
                    gwout.getMsgid(), origin);
            rejectMessage(gwout, "invalid-origin-format", "invalid-arguments",
                    "unable to convert to AMQP due to unrecognized originator O/R address",
                    "invalid_origin_format",
                    "gwout#" + gwout.getMsgid() + " rejected: originator O/R address '" + origin
                            + "' không hợp lệ");
            return;
        }

        // Phát hiện bản tin Probe từ AMHS
        boolean isProbe = "probe".equalsIgnoreCase(gwout.getBodyType()) || "PROBE".equalsIgnoreCase(gwout.getText());
        if (isProbe) {
            log.info("Processing AMHS Probe for gwout#{}", gwout.getMsgid());
            processAmhsProbe(gwout);
            return;
        }

        // Kiểm tra X.400 content-type (IPM 1988)
        Integer x400ContentType = gwout.getX400ContentType();
        if (x400ContentType != null && x400ContentType != IPM_1988_CONTENT_TYPE) {
            log.warn("gwout#{} rejected: content-type {} khác interpersonal-messaging-1988(22)",
                    gwout.getMsgid(), x400ContentType);
            rejectMessage(gwout, "unsupported-content-type", "content-type-not-supported", null,
                    "unsupported_content_type: " + x400ContentType,
                    "gwout#" + gwout.getMsgid() + " rejected: unsupported content-type " + x400ContentType);
            return;
        }

        // Kiểm tra kích thước payload
        MessageValidationService.ValidationResult dirResult = validationService.validateAmhsToSwim(gwout.getText(),
                gwout.getAddress(), payloadByteSize(gwout));
        if (!dirResult.isValid()) {
            log.warn("gwout#{} rejected by validation: {}", gwout.getMsgid(), dirResult.getErrorMessage());
            rejectMessage(gwout, "validation-failed",
                    ndrDiagnosticFor(dirResult.getErrorMessage()),
                    ndrSupplementaryFor(dirResult.getErrorMessage()),
                    "validation_failed: " + dirResult.getErrorMessage(),
                    "gwout#" + gwout.getMsgid() + " rejected: " + dirResult.getErrorMessage());
            return;
        }

        // Kiểm tra ATS-message-header
        MessageValidationService.ValidationResult headerResult =
                validationService.validateAtsMessageHeader(gwout.getAmhsPriority(), gwout.getFilingTime());
        if (!headerResult.isValid()) {
            log.warn("gwout#{} rejected: ATS-message-header sai cú pháp - {}",
                    gwout.getMsgid(), headerResult.getErrorMessage());
            rejectMessage(gwout, "ats-header-syntax-error", "content-syntax-error",
                    "unable to convert to AMQP due to ATS-message-header or Heading Fields syntax error",
                    "ats_header_syntax_error: " + headerResult.getErrorMessage(),
                    "gwout#" + gwout.getMsgid() + " rejected: ATS-message-header syntax error - "
                            + headerResult.getErrorMessage());
            return;
        }

        if (!authorizationService.isAmhsUserAuthorized(gwout.getOrigin())) {
            log.warn("gwout#{} REJECTED: AMHS originator '{}' not authorized", gwout.getMsgid(), gwout.getOrigin());
            rejectMessage(gwout, "unauthorized-originator", "unrecognised-OR-name",
                    "unable to convert to AMQP due to unrecognized originator O/R address",
                    "unauthorized_originator: " + gwout.getOrigin(),
                    "Unauthorized AMHS originator: " + gwout.getOrigin()
                            + " (gwout#" + gwout.getMsgid() + ")");
            return;
        }

        // Kiểm tra encoded-information-types
        MessageValidationService.ValidationResult eitTypeResult =
                validationService.validateEncodedInformationTypes(gwout.getOriginEit());
        if (!eitTypeResult.isValid()) {
            log.warn("gwout#{} rejected by EIT check: {}", gwout.getMsgid(), eitTypeResult.getErrorMessage());
            rejectMessage(gwout, "unsupported-eit", "encoded-information-types-unsupported", null,
                    "unsupported_eit: " + eitTypeResult.getErrorMessage(),
                    "gwout#" + gwout.getMsgid() + " rejected: " + eitTypeResult.getErrorMessage());
            return;
        }

        // Chuẩn hoá bodyPartType
        if (gwout.getBodyPartType() != null) {
            String rawType = gwout.getBodyPartType().trim();
            if ("401".equals(rawType)) {
                gwout.setBodyPartType("ia5-text-body-part");
            } else if ("402".equals(rawType) || "407".equals(rawType)) {
                gwout.setBodyPartType("general-text-body-part");
            } else if ("403".equals(rawType)) {
                gwout.setBodyPartType("file-transfer-body-part");
            }
        }

        // Kiểm tra số lượng body part
        Integer bodyPartCount = gwout.getNumberOfAttachment();
        if (bodyPartCount != null && bodyPartCount > 1) {
            String supplementary = null;
            if (bodyPartCount > 2) {
                supplementary = "unable to convert to AMQP due to multiple body parts";
            } else if (!"file-transfer-body-part".equals(gwout.getBodyPartType())) {
                supplementary = "unable to convert to AMQP due to unsupported body part type";
            }
            if (supplementary != null) {
                log.warn("gwout#{} rejected: {} body part(s), type={}",
                        gwout.getMsgid(), bodyPartCount, gwout.getBodyPartType());
                rejectMessage(gwout, "unsupported-body-parts", "content-syntax-error", supplementary,
                        "unsupported_body_parts: " + bodyPartCount + " parts",
                        "gwout#" + gwout.getMsgid() + " rejected: " + supplementary);
                return;
            }
        }

        // Kiểm tra body part type
        if (gwout.getBodyPartType() != null) {
            MessageValidationService.ValidationResult eitResult = validationService.validateBodyPartType(gwout.getBodyPartType());
            if (!eitResult.isValid()) {
                log.warn("gwout#{} rejected by EIT validation: {}", gwout.getMsgid(), eitResult.getErrorMessage());
                rejectMessage(gwout, "unsupported-eit", "content-syntax-error",
                        "unable to convert to AMQP due to unsupported body part type",
                        "unsupported_eit: " + eitResult.getErrorMessage(),
                        "gwout#" + gwout.getMsgid() + " rejected: " + eitResult.getErrorMessage());
                return;
            }
        }

        // Kiểm tra repertoire
        MessageValidationService.ValidationResult repertoireResult =
                validationService.validateRepertoire(gwout.getBodyPartType(), gwout.getBodyPartCharset());
        if (!repertoireResult.isValid()) {
            String supplementary = repertoireResult.getErrorMessage().contains("unsupported-body-part-type")
                    ? "unable to convert to AMQP due to unsupported body part type"
                    : "unable to convert to AMQP due to unsupported encoded-information-types";
            log.warn("gwout#{} rejected by repertoire check: {}", gwout.getMsgid(), repertoireResult.getErrorMessage());
            rejectMessage(gwout, "unsupported-repertoire", "content-syntax-error", supplementary,
                    "unsupported_repertoire: " + repertoireResult.getErrorMessage(),
                    "gwout#" + gwout.getMsgid() + " rejected: " + repertoireResult.getErrorMessage());
            return;
        }

        // Kiểm tra TTL
        if (gwout.getAmhsTtl() != null && gwout.getAmhsTtl().isBefore(LocalDateTime.now())) {
            log.warn("gwout#{} TTL expired (latest-delivery-time exceeded)", gwout.getMsgid());
            rejectMessage(gwout, "ttl-expired", "maximum-time-expired", null, "ttl_expired",
                    "gwout#" + gwout.getMsgid() + " rejected: latest-delivery-time exceeded ("
                            + gwout.getAmhsTtl() + ")");
            return;
        }

        // Cảnh báo Control Position nếu bản tin có độ ưu tiên cao nhất (SS / precedence 107)
        Integer precedence = gwout.getPrecedence();
        boolean ssByPrecedence = precedence != null && precedence == AmqpProperties.PRECEDENCE_SS;
        boolean ssByPriority = precedence == null && "SS".equalsIgnoreCase(gwout.getAmhsPriority());
        if (ssByPrecedence || ssByPriority) {
            String basis = ssByPrecedence ? "precedence 107" : "ATS-message-priority SS";
            log.warn("gwout#{}: bản tin ưu tiên cao nhất ({}) - báo Control Position",
                    gwout.getMsgid(), basis);
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + ": bản tin AMHS ưu tiên cao nhất (" + basis
                            + ") gửi tới " + gwout.getAddress()
                            + " - cần Control Position xử lý",
                    "gwout", gwout.getMsgid());
        }

        // Chuyển tiếp bản tin
        try {
            gwout.setStatus(OutboundStatus.TRANSFORMED.getValue());
            if (gwout.getAmhsPriority() != null) {
                gwout.setSwimPriority(vn.asg.swim.model.AmqpProperties.mapAtsPriorityToAmqp(gwout.getAmhsPriority()));
            }
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "OK", "forwarded_unchanged");
            log.info("gwout#{} forwarded unchanged -> status=OUT_TRANSFORMED", gwout.getMsgid());

            // Ghi log nếu có yêu cầu Delivery Report
            if (Boolean.TRUE.equals(gwout.getAmhsDeliveryReport())) {
                log.info("gwout#{} có yêu cầu Delivery Report - AMHS Component phát DR",
                        gwout.getMsgid());
                conversionService.logAmhsToSwim(gwout, null, "OK", "dr_requested");
            }
        } catch (Exception e) {
            log.error("gwout#{} processing failed: {}", gwout.getMsgid(), e.getMessage());
            rejectMessage(gwout, "processing-failed", null, null,
                    "processing_failed: " + e.getMessage(),
                    "gwout#" + gwout.getMsgid() + " xử lý thất bại: " + e.getMessage());
        }
    }

    /**
     * Xử lý bản ghi phân phối tin đi từ hàng đợi.
     */
    @Transactional
    public void processDispatch(GwoutDispatch dispatch) {
        Gwout gwout = gwoutRepository.findById(dispatch.getGwoutId()).orElse(null);
        if (gwout == null) {
            handleFailure(dispatch, GwoutDispatch.STEP_ROUTING,
                    new RuntimeException("gwout#" + dispatch.getGwoutId() + " not found"));
            return;
        }

        // Đọc nội dung bản tin gốc
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

        // Gộp các dispatch cùng topic để publish một lần
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
            return;
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
     */
    private String publish(Gwout gwout, String topic, String recipient,
            String body, String contentType) throws JMSException {
        Session session = null;
        MessageProducer producer = null;
        try {
            session = connectionManager.createSession();
            producer = connectionManager.createProducer(session, topic);

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
                    : ("ftbp".equalsIgnoreCase(gwout.getBodyType()) ? "application/octet-stream" : "text/plain; charset=\"utf-8\"");
            message.setStringProperty("JMS_AMQP_CONTENT_TYPE", ct);

            if (gwout.getOrigin() != null) {
                message.setStringProperty("amhs_originator", gwout.getOrigin());
            }
            if (recipient != null) {
                message.setStringProperty("amhs_recipients", recipient);
            }
            if (gwout.getIpmId() != null) {
                message.setStringProperty("amhs_ipm_id", gwout.getIpmId());
            }
            if (gwout.getAmhsPriority() != null) {
                message.setStringProperty("amhs_ats_pri", gwout.getAmhsPriority());
            }
            if (gwout.getFilingTime() != null) {
                message.setStringProperty("amhs_ats_ft", gwout.getFilingTime());
            }
            if (gwout.getOptionalHeading() != null) {
                message.setStringProperty("amhs_ats_ohi", gwout.getOptionalHeading());
            }
            if (gwout.getSubject() != null && !gwout.getSubject().isBlank()) {
                message.setStringProperty("amhs_subject", gwout.getSubject());
            }
            String bodyPartType = gwout.getBodyPartType() != null ? gwout.getBodyPartType()
                    : ("ftbp".equalsIgnoreCase(gwout.getBodyType()) ? "file-transfer-body-part" : "ia5-text-body-part");
            message.setStringProperty("amhs_bodypart_type", bodyPartType);
            if ("file-transfer-body-part".equals(bodyPartType)) {
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
                if (gwout.getAmhsUserVisibleString() != null) {
                    message.setStringProperty("amhs_user_visible_string", gwout.getAmhsUserVisibleString());
                }
                if (gwout.getAmhsRegisteredId() != null
                        && !AmqpProperties.isDefaultRegisteredIdentifier(gwout.getAmhsRegisteredId())
                        && gwout.getAmhsUserVisibleString() == null) {
                    log.warn("gwout#{}: amhs_registered_identifier '{}' khác OID mặc định nhưng "
                            + "thiếu amhs_user_visible_string", gwout.getMsgid(), gwout.getAmhsRegisteredId());
                    alertService.create(
                            GwAlert.TYPE_VALIDATION_ERROR,
                            GwAlert.SEV_WARNING,
                            "gwout#" + gwout.getMsgid() + ": amhs_registered_identifier '"
                                    + gwout.getAmhsRegisteredId() + "' khác OID mặc định nhưng thiếu amhs_user_visible_string",
                            "gwout", gwout.getMsgid());
                }
            } else if ("ia5-text".equals(bodyPartType) || "ia5-text-body-part".equals(bodyPartType)) {
                message.setStringProperty("amhs_content_encoding", "IA5");
            } else if ("general-text-body-part".equals(bodyPartType)
                    && isTable6ContentEncoding(gwout.getBodyPartCharset())) {
                message.setStringProperty("amhs_content_encoding", gwout.getBodyPartCharset());
            }
            message.setStringProperty("amhs_message_signed", "unsigned");
            message.setStringProperty("amhs_gateway_id", configService.getGatewayId());
            int amqpPriority = gwout.getSwimPriority() != null
                    ? gwout.getSwimPriority()
                    : Message.DEFAULT_PRIORITY;
            producer.send(message, DeliveryMode.PERSISTENT, amqpPriority, Message.DEFAULT_TIME_TO_LIVE);
            return AmqpMessageIdUtil.clean(message.getJMSMessageID());

        } finally {
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

        List<String> deadRecipients = all.stream()
                .filter(d -> GwoutDispatch.STATUS_DEAD.equals(d.getStatus()))
                .map(GwoutDispatch::getRecipient)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        gwoutRepository.findById(gwoutId).ifPresent(gwout -> {
            gwout.setStatus(deadRecipients.isEmpty()
                    ? OutboundStatus.PUBLISHED.getValue()
                    : OutboundStatus.FAILED.getValue());
            gwoutRepository.save(gwout);

            // Ghi log lỗi cho các recipient không gửi được
            for (String recipient : deadRecipients) {
                conversionService.logAmhsToSwimRejected(gwout,
                        "undeliverable: " + recipient, null,
                        "unable to convert to AMQP due to delivery failure to the SWIM component");
            }
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
            rejectMessage(gwout, "no-recipients", "unrecognised-OR-name", null,
                    "no_recipients",
                    "gwout#" + gwout.getMsgid() + " has no recipients");
            return;
        }

        // Tách recipient hợp lệ khỏi recipient không hợp lệ
        List<String> recipients = new java.util.ArrayList<>();
        List<String> unconvertible = new java.util.ArrayList<>();
        for (String raw : address.split("[,\\s]+")) {
            String candidate = raw.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (MessageValidationService.isValidAftnAddress(candidate)) {
                if (!recipients.contains(candidate)) {
                    recipients.add(candidate);
                }
            } else if (!unconvertible.contains(candidate)) {
                unconvertible.add(candidate);
            }
        }

        if (recipients.isEmpty()) {
            log.warn("gwout#{} has no valid AFTN recipients after filtering", gwout.getMsgid());
            rejectMessage(gwout, "invalid-recipients", "unrecognised-OR-name",
                    "unable to convert to AMQP due to unrecognized recipient O/R address",
                    "invalid_recipients: " + String.join(",", unconvertible),
                    "gwout#" + gwout.getMsgid() + " has no valid AFTN recipients");
            return;
        }

        // Tra topic publish theo từng địa chỉ recipient
        java.util.LinkedHashMap<String, String> topicByRecipient = new java.util.LinkedHashMap<>();
        List<String> unroutable = new java.util.ArrayList<>();
        for (String recipient : recipients) {
            var ruleOpt = routingService.findTopicForRecipient(recipient);
            if (ruleOpt.isEmpty()) {
                log.warn("gwout#{}: recipient '{}' không khớp rule định tuyến OUT nào",
                        gwout.getMsgid(), recipient);
                unroutable.add(recipient);
                continue;
            }
            topicByRecipient.put(recipient, ruleOpt.get().getSendTopic());
        }

        if (topicByRecipient.isEmpty()) {
            log.warn("gwout#{} không có recipient nào tra được đích publish", gwout.getMsgid());
            rejectMessage(gwout, "no-routing-rule", "unrecognised-OR-name",
                    "unable to convert to AMQP due to unrecognized recipient O/R address",
                    "routing_failed: no rule for recipients " + String.join(",", recipients),
                    "gwout#" + gwout.getMsgid() + " rejected: không có rule định tuyến cho recipient ["
                            + String.join(",", recipients) + "]");
            return;
        }

        // Ghi log cho từng recipient không chuyển giao được
        List<String> undeliverable = new java.util.ArrayList<>(unconvertible);
        undeliverable.addAll(unroutable);
        if (!undeliverable.isEmpty()) {
            log.warn("gwout#{} có {} recipient không chuyển giao được ({}), sinh NDR riêng cho từng recipient",
                    gwout.getMsgid(), undeliverable.size(), String.join(",", undeliverable));
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + ": không chuyển giao được cho recipient ["
                            + String.join(",", undeliverable) + "], bản tin vẫn chuyển tới ["
                            + String.join(",", topicByRecipient.keySet()) + "]",
                    "gwout", gwout.getMsgid());
            for (String recipient : unconvertible) {
                logRecipientUndeliverable(gwout, recipient, "unrecognised_recipient");
            }
            for (String recipient : unroutable) {
                logRecipientUndeliverable(gwout, recipient, "no_route");
            }
        }

        for (var entry : topicByRecipient.entrySet()) {
            GwoutDispatch dispatch = new GwoutDispatch();
            dispatch.setGwoutId(gwout.getMsgid());
            dispatch.setRecipient(entry.getKey());
            dispatch.setTopic(entry.getValue());
            dispatch.setStatus(GwoutDispatch.STATUS_PENDING);
            gwoutDispatchRepository.save(dispatch);
        }

        gwoutRepository.save(gwout);
        log.debug("gwout#{} -> {} dispatch(es) created, topics={}", gwout.getMsgid(),
                topicByRecipient.size(), new java.util.LinkedHashSet<>(topicByRecipient.values()));
    }

    private boolean isTable6ContentEncoding(String charset) {
        return "IA5".equals(charset) || "ISO-646".equals(charset) || "ISO-8859-1".equals(charset);
    }

    private Integer payloadByteSize(Gwout gwout) {
        String body = gwout.getText();
        if (body == null) {
            return null;
        }
        if ("ftbp".equalsIgnoreCase(gwout.getBodyType())) {
            try {
                return java.util.Base64.getDecoder().decode(body).length;
            } catch (IllegalArgumentException e) {
                log.debug("gwout#{} body_type=ftbp nhưng text không phải base64, đo kích thước trực tiếp",
                        gwout.getMsgid());
            }
        }
        return body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /**
     * Suy ra non-delivery-diagnostic-code từ thông báo lỗi.
     */
    private String ndrDiagnosticFor(String errorMessage) {
        if (errorMessage == null) return null;
        if (errorMessage.contains("content-too-long")) return "content-too-long";
        if (errorMessage.contains("too-many-recipients")) return "too-many-recipients";
        return null;
    }

    /**
     * Suy ra supplementary-information của NDR.
     */
    private String ndrSupplementaryFor(String errorMessage) {
        if (errorMessage == null) return null;
        if (errorMessage.contains("content-too-long")) return "unable to convert to AMQP due to the content size";
        if (errorMessage.contains("too-many-recipients")) return "unable to convert to AMQP due to number of recipients";
        return null;
    }

    /**
     * Xử lý bản tin Probe nhận từ AMHS.
     */
    private void processAmhsProbe(Gwout gwout) {
        // 1. Kiểm tra originator
        String originator = gwout.getOrigin();
        if (!authorizationService.isAmhsUserAuthorized(originator)) {
            rejectProbe(gwout, "Unknown originator: " + originator, "unknown-originator",
                    "invalid-arguments",
                    "unable to convert to AMQP due to unrecognized originator O/R address");
            return;
        }

        String recipients = gwout.getAddress();
        if (recipients == null || recipients.isBlank()) {
            rejectProbe(gwout, "Recipients list is empty", "empty-recipients", "unrecognised-OR-name", null);
            return;
        }
        String[] recipientArray = recipients.trim().split("[,\\s]+");

        // 2. Kiểm tra EIT của probe
        MessageValidationService.ValidationResult eitResult =
                validationService.validateEncodedInformationTypes(gwout.getOriginEit());
        if (!eitResult.isValid()) {
            rejectProbe(gwout, "Probe EIT rejected: " + eitResult.getErrorMessage(), "unsupported-eit",
                    "encoded-information-types-unsupported", null);
            return;
        }

        // 3. Kiểm tra kích thước payload probe
        Integer contentLength = gwout.getContentLength();
        if (contentLength != null) {
            int maxSize = configService.getMaxMsgDataSize();
            if (maxSize > 0 && contentLength > maxSize) {
                rejectProbe(gwout,
                        String.format("Probe content-length %d exceeds maximum %d", contentLength, maxSize),
                        "content-too-long", "content-too-long",
                        "unable to convert to AMQP due to the content size");
                return;
            }
        }

        // 4. Kiểm tra số lượng recipient
        int maxRecipients = configService.getMaxMsgRecipients();
        if (maxRecipients > 0 && recipientArray.length > maxRecipients) {
            rejectProbe(gwout,
                    String.format("Probe addresses %d recipients, exceeds maximum %d",
                            recipientArray.length, maxRecipients),
                    "too-many-recipients", "too-many-recipients",
                    "unable to convert to AMQP due to number of recipients");
            return;
        }

        // 5. Kiểm tra tính hợp lệ của từng recipient
        List<String> deliverable = new java.util.ArrayList<>();
        List<String> undeliverable = new java.util.ArrayList<>();
        for (String recipient : recipientArray) {
            var formatResult = validationService.validateAftnAddress(recipient, "Recipient");
            if (!formatResult.isValid() || !isRecipientKnown(recipient)) {
                undeliverable.add(recipient);
            } else {
                deliverable.add(recipient);
            }
        }

        // Ghi traffic log theo từng recipient
        for (String recipient : deliverable) {
            conversionService.logAmhsToSwim(gwout, null, "OK", "probe_deliverable: " + recipient);
        }
        for (String recipient : undeliverable) {
            conversionService.logAmhsToSwimRejected(gwout,
                    "probe_unknown_recipient: " + recipient, "unrecognised-OR-name", null);
        }

        if (!undeliverable.isEmpty()) {
            log.warn("Probe gwout#{}: {} recipient(s) không chuyển đổi được sang AF-address ({}), "
                            + "{} recipient(s) chuyển đổi được",
                    gwout.getMsgid(), undeliverable.size(), String.join(",", undeliverable), deliverable.size());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "Probe gwout#" + gwout.getMsgid() + ": recipient không xác định ["
                            + String.join(",", undeliverable) + "]"
                            + (deliverable.isEmpty() ? "" : ", hợp lệ [" + String.join(",", deliverable) + "]"),
                    "gwout", gwout.getMsgid());
            gwout.setRejectionReason("unknown-recipient");
            gwout.setRejectionSource("AMHS");
            gwout.setRejectionDiagnostic("unrecognised-OR-name");
        }

        gwout.setStatus(deliverable.isEmpty()
                ? OutboundStatus.FAILED.getValue()
                : OutboundStatus.PUBLISHED.getValue());
        gwoutRepository.save(gwout);

        if (!deliverable.isEmpty()) {
            log.info("Probe gwout#{} conveyance test OK cho {} recipient", gwout.getMsgid(), deliverable.size());
        }
    }

    /**
     * Từ chối probe cho toàn bộ recipient: log traffic và báo Control Position.
     */
    private void rejectProbe(Gwout gwout, String reason, String rejectionCode, String ndrDiagnostic,
            String supplementaryInfo) {
        log.warn("Probe gwout#{} REJECTED: {}", gwout.getMsgid(), reason);
        alertService.create(
                GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                "Probe gwout#" + gwout.getMsgid() + " rejected: " + reason,
                "gwout", gwout.getMsgid());
        gwout.setStatus(OutboundStatus.FAILED.getValue());
        gwout.setRejectionReason(rejectionCode);
        gwout.setRejectionSource("AMHS");
        gwout.setRejectionDiagnostic(ndrDiagnostic);
        gwoutRepository.save(gwout);
        conversionService.logAmhsToSwimRejected(gwout, "probe_rejected_" + rejectionCode + ": " + reason,
                ndrDiagnostic, supplementaryInfo);
    }

    /**
     * Kiểm tra recipient của probe có hợp lệ hay không.
     */
    private boolean isRecipientKnown(String recipient) {
        if (!MessageValidationService.isValidAftnAddress(recipient)) {
            return false;
        }

        String whitelist;
        try {
            whitelist = configService.get("AUTHORIZED_AMHS_ADDRESSES");
        } catch (Exception e) {
            return true;
        }
        if (whitelist == null || whitelist.isBlank()) {
            return true;
        }
        return containsExact(whitelist, recipient);
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