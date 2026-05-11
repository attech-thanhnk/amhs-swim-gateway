package vn.asg.swim.service;

import jakarta.jms.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.GwoutDispatch;
import vn.asg.swim.repository.GwoutDispatchRepository;
import vn.asg.swim.repository.GwoutRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AMHS → SWIM direction: processes each gwout_dispatch record.
 * Pipeline: detect → routing → convert → publish.
 * Manages retry logic and synchronizes global gwout status.
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
     * Processes 1 gwout_dispatch: detect → routing → convert → publish.
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

        // EUR Doc 047 §4.4.1 - C-03/A-01: Check conversion direction
        MessageValidationService.ValidationResult dirResult = validationService.validateAmhsToSwim(gwout.getText(),
                gwout.getAddress());
        if (!dirResult.isValid()) {
            log.warn("gwout#{} rejected by validation: {}", gwout.getMsgid(), dirResult.getErrorMessage());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + " rejected: " + dirResult.getErrorMessage(),
                    "gwout", gwout.getMsgid());
            dispatch.setStatus(GwoutDispatch.STATUS_SENT); // no retry, record as rejected
            dispatch.setSentAt(LocalDateTime.now());
            dispatch.setLastError(dirResult.getErrorMessage());
            gwoutDispatchRepository.save(dispatch);
            checkAndUpdateGwoutStatus(dispatch.getGwoutId());
            return;
        }

        // EUR Doc 047 §4.4.1 - A-02: Check AMHS user authorization (C-19)
        if (!authorizationService.isAmhsUserAuthorized(gwout.getOrigin())) {
            log.warn("gwout#{} REJECTED: AMHS originator '{}' not authorized",
                    gwout.getMsgid(), gwout.getOrigin());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "Unauthorized AMHS originator: " + gwout.getOrigin()
                            + " (gwout#" + gwout.getMsgid() + ")",
                    "gwout", gwout.getMsgid());
            dispatch.setStatus(GwoutDispatch.STATUS_SENT);
            dispatch.setSentAt(LocalDateTime.now());
            dispatch.setLastError("AMHS originator not authorized: " + gwout.getOrigin());
            gwoutDispatchRepository.save(dispatch);
            checkAndUpdateGwoutStatus(dispatch.getGwoutId());
            return;
        }

        // Check TTL
        if (gwout.getAmhsTtl() != null && gwout.getAmhsTtl().isBefore(LocalDateTime.now())) {
            log.info("gwout#{} TTL expired, skipping dispatch#{}", gwout.getMsgid(), dispatch.getId());
            dispatch.setStatus(GwoutDispatch.STATUS_SENT); // đánh dấu xong, không retry
            dispatch.setSentAt(LocalDateTime.now());
            gwoutDispatchRepository.save(dispatch);
            checkAndUpdateGwoutStatus(dispatch.getGwoutId());
            return;
        }

        String body = gwout.getText();

        try {
            // Step 1: Detect message type
            String messageType = detectService.detect(body);
            dispatch.setMessageType(messageType);
            dispatch.setScope(null); // Bỏ dùng Scope

        } catch (Exception e) {
            handleFailure(dispatch, GwoutDispatch.STEP_DETECT, e);
            return;
        }

        try {
            // Step 2: Find topic from routing rule
            var ruleOpt = routingService.findBestMatchOut(dispatch.getMessageType());
            if (ruleOpt.isEmpty()) {
                throw new RuntimeException("No routing rule for type=" + dispatch.getMessageType());
            }
            var rule = ruleOpt.get();
            String topic = rule.getSendTopic();
            dispatch.setTopic(topic);
            dispatch.setAmqpAccount(null); // Simple Routing doesn't specify AMQP account per rule yet
        } catch (Exception e) {
            handleFailure(dispatch, GwoutDispatch.STEP_ROUTING, e);
            return;
        }

        String convertedBody;
        try {
            // Step 3: Convert body AMHS plain text → SWIM format (Store in gwout for audit/retry)
            if (gwout.getXmlContent() == null || gwout.getXmlContent().isBlank()) {
                convertedBody = conversionService.toSwim(body, dispatch.getMessageType());
                gwout.setXmlContent(convertedBody);
                gwoutRepository.save(gwout);
            } else {
                convertedBody = gwout.getXmlContent();
            }
        } catch (Exception e) {
            handleFailure(dispatch, GwoutDispatch.STEP_CONVERT, e);
            return;
        }

        try {
            // Step 4: Publish to AMQP broker
            publish(gwout, dispatch.getTopic(), dispatch.getRecipient(), convertedBody,
                    gwout.getContentType());

            dispatch.setStatus(GwoutDispatch.STATUS_SENT);
            dispatch.setSentAt(LocalDateTime.now());
            gwoutDispatchRepository.save(dispatch);
            log.info("dispatch#{} SENT → topic={}", dispatch.getId(), dispatch.getTopic());

        } catch (Exception e) {
            handleFailure(dispatch, GwoutDispatch.STEP_PUBLISH, e);
            return;
        }

        checkAndUpdateGwoutStatus(dispatch.getGwoutId());
    }

    /**
     * Publish AMQP message to topic.
     * EUR Doc 047 §4.4.3: Generate full AMQP Application Properties (A-18 to A-37)
     */
    private void publish(Gwout gwout, String topic, String recipient,
            String body, String contentType) throws JMSException {
        Session session = connectionManager.createSession();
        try {
            MessageProducer producer = connectionManager.createProducer(session, topic);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            TextMessage message = session.createTextMessage(body);

            // A-23: content-type
            String ct = contentType != null ? contentType : "application/xml";
            message.setStringProperty("JMS_AMQP_CONTENT_TYPE", ct);

            // A-32: amhs_originator (§4.4.3.4.7)
            if (gwout.getOrigin() != null) {
                message.setStringProperty("amhs_originator", gwout.getOrigin());
            }

            // A-29: amhs_recipients (§4.4.3.4.4)
            if (recipient != null) {
                message.setStringProperty("amhs_recipients", recipient);
            }

            // A-24: amhs_ipm_id (§4.4.3.4.1)
            if (gwout.getAmhsid() != null) {
                message.setStringProperty("amhs_ipm_id", gwout.getAmhsid());
            }

            // A-28: amhs_ats_pri (§4.4.3.4.3)
            if (gwout.getPriority() != null) {
                String atsPri = vn.asg.swim.model.AmqpProperties.mapPriorityToAts(gwout.getPriority());
                message.setStringProperty("amhs_ats_pri", atsPri);
                // Also set JMS priority for broker routing
                message.setJMSPriority(gwout.getPriority());
            }

            // A-30: amhs_ats_ft (§4.4.3.4.5)
            if (gwout.getFilingTime() != null) {
                message.setStringProperty("amhs_ats_ft", gwout.getFilingTime());
            }

            // A-31: amhs_ats_ohi (§4.4.3.4.6)
            if (gwout.getOptionalHeading() != null) {
                message.setStringProperty("amhs_ats_ohi", gwout.getOptionalHeading());
            }

            // A-34: amhs_bodypart_type (§4.4.3.4.9)
            if (gwout.getBodyType() != null) {
                String bodyPartType = "text".equals(gwout.getBodyType())
                        ? "ia5-text-body-part"
                        : "file-transfer-body-part";
                message.setStringProperty("amhs_bodypart_type", bodyPartType);
            }

            // A-35: amhs_content_encoding (§4.4.3.4.9)
            // Default IA5 for text messages
            message.setStringProperty("amhs_content_encoding", "IA5");

            // A-36: amhs_message_signed (§4.4.3.4.10)
            // Default unsigned (PKI not implemented in this phase)
            message.setStringProperty("amhs_message_signed", "unsigned");

            // A-18: message-id (§4.4.3.2.1)
            message.setJMSMessageID(UUID.randomUUID().toString());

            // A-22: creation-time (§4.4.3.3.5)
            message.setJMSTimestamp(System.currentTimeMillis());

            producer.send(message);
            producer.close();
        } finally {
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Handle failure: increment retry_count, calculate next_retry_at, escalate to
     * DEAD if limit reached.
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
     * Synchronize gwout.status when all child dispatches are finished (SENT or
     * DEAD).
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
            gwout.setStatus(hasDead ? Gwout.STATUS_DEAD : Gwout.STATUS_SENT);
            gwoutRepository.save(gwout);
        });
    }

    /**
     * Calculate next retry time from gateway_config.
     * retry 1: +30s, retry 2: +120s, retry 3: +300s (defaults).
     */
    private LocalDateTime calcNextRetry(int retryCount) {
        int[] keys = {
                configService.getInt("RETRY_DELAY_1ST_SECONDS"),
                configService.getInt("RETRY_DELAY_2ND_SECONDS"),
                configService.getInt("RETRY_DELAY_3RD_SECONDS")
        };
        int delay = keys[Math.min(retryCount - 1, keys.length - 1)];
        return LocalDateTime.now().plusSeconds(delay);
    }
}
