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

        // Validation
        MessageValidationService.ValidationResult dirResult = validationService.validateAmhsToSwim(gwout.getText(),
                gwout.getAddress());
        if (!dirResult.isValid()) {
            log.warn("gwout#{} rejected by validation: {}", gwout.getMsgid(), dirResult.getErrorMessage());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + " rejected: " + dirResult.getErrorMessage(),
                    "gwout", gwout.getMsgid());
            dispatch.setStatus(GwoutDispatch.STATUS_DEAD);
            dispatch.setFailedStep(GwoutDispatch.STEP_VALIDATION);
            dispatch.setLastError(dirResult.getErrorMessage());
            gwoutDispatchRepository.save(dispatch);
            checkAndUpdateGwoutStatus(dispatch.getGwoutId());
            return;
        }

        // Authorization
        if (!authorizationService.isAmhsUserAuthorized(gwout.getOrigin())) {
            log.warn("gwout#{} REJECTED: AMHS originator '{}' not authorized",
                    gwout.getMsgid(), gwout.getOrigin());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "Unauthorized AMHS originator: " + gwout.getOrigin()
                            + " (gwout#" + gwout.getMsgid() + ")",
                    "gwout", gwout.getMsgid());
            dispatch.setStatus(GwoutDispatch.STATUS_DEAD);
            dispatch.setFailedStep(GwoutDispatch.STEP_AUTHORIZATION);
            dispatch.setLastError("AMHS originator not authorized: " + gwout.getOrigin());
            gwoutDispatchRepository.save(dispatch);
            checkAndUpdateGwoutStatus(dispatch.getGwoutId());
            return;
        }

        // TTL
        if (gwout.getAmhsTtl() != null && gwout.getAmhsTtl().isBefore(LocalDateTime.now())) {
            log.info("gwout#{} TTL expired, skipping dispatch#{}", gwout.getMsgid(), dispatch.getId());
            dispatch.setStatus(GwoutDispatch.STATUS_SENT);
            dispatch.setSentAt(LocalDateTime.now());
            gwoutDispatchRepository.save(dispatch);
            checkAndUpdateGwoutStatus(dispatch.getGwoutId());
            return;
        }

        String body = gwout.getText();

        try {
            String messageType = detectService.detect(body);
            dispatch.setMessageType(messageType);
        } catch (Exception e) {
            handleFailure(dispatch, GwoutDispatch.STEP_DETECT, e);
            return;
        }

        try {
            var ruleOpt = routingService.findBestMatchOut(dispatch.getMessageType());
            if (ruleOpt.isEmpty()) {
                throw new RuntimeException("No routing rule for type=" + dispatch.getMessageType());
            }
            var rule = ruleOpt.get();
            dispatch.setTopic(rule.getSendTopic());
        } catch (Exception e) {
            handleFailure(dispatch, GwoutDispatch.STEP_ROUTING, e);
            return;
        }

        String convertedBody;
        try {
            var rule = routingService.findBestMatchOut(dispatch.getMessageType()).get();
            
            if (Boolean.TRUE.equals(rule.getConvertToJson())) {
                boolean isAlreadyJson = gwout.getPayloadContent() != null && gwout.getPayloadContent().trim().startsWith("{");
                if (!isAlreadyJson) {
                    convertedBody = conversionService.toSwim(body, dispatch.getMessageType());
                    gwout.setPayloadContent(convertedBody);
                    gwoutRepository.save(gwout);
                } else {
                    convertedBody = gwout.getPayloadContent();
                }
            } else {
                log.debug("Routing rule for {} specifies TAC output. Forwarding original body.", dispatch.getMessageType());
                convertedBody = body;
                // If current payload is JSON but rule says TAC, update it or just use body
                if (gwout.getPayloadContent() == null || gwout.getPayloadContent().trim().startsWith("{")) {
                    gwout.setPayloadContent(body);
                    gwoutRepository.save(gwout);
                }
            }
        } catch (Exception e) {
            handleFailure(dispatch, GwoutDispatch.STEP_CONVERT, e);
            return;
        }

        try {
            publish(gwout, dispatch.getTopic(), dispatch.getRecipient(), convertedBody,
                    gwout.getContentType());

            dispatch.setStatus(GwoutDispatch.STATUS_SENT);
            dispatch.setSentAt(LocalDateTime.now());
            gwoutDispatchRepository.save(dispatch);
            log.info("dispatch#{} SENT \u2192 topic={}", dispatch.getId(), dispatch.getTopic());

        } catch (Exception e) {
            handleFailure(dispatch, GwoutDispatch.STEP_PUBLISH, e);
            return;
        }

        checkAndUpdateGwoutStatus(dispatch.getGwoutId());
    }

    private void publish(Gwout gwout, String topic, String recipient,
            String body, String contentType) throws JMSException {
        Session session = connectionManager.createSession();
        try {
            MessageProducer producer = connectionManager.createProducer(session, topic);
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
            producer.close();
        } finally {
            try {
                session.close();
            } catch (Exception ignored) {}
        }
    }

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

    private void checkAndUpdateGwoutStatus(Long gwoutId) {
        List<GwoutDispatch> all = gwoutDispatchRepository.findByGwoutId(gwoutId);
        if (all.isEmpty()) return;

        boolean allDone = all.stream()
                .allMatch(d -> GwoutDispatch.STATUS_SENT.equals(d.getStatus())
                        || GwoutDispatch.STATUS_DEAD.equals(d.getStatus()));
        if (!allDone) return;

        boolean hasDead = all.stream().anyMatch(d -> GwoutDispatch.STATUS_DEAD.equals(d.getStatus()));
        gwoutRepository.findById(gwoutId).ifPresent(gwout -> {
            gwout.setStatus(hasDead ? Gwout.STATUS_DEAD : Gwout.STATUS_SENT);
            gwoutRepository.save(gwout);
        });
    }

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
            // Exponential backoff: delay = last_delay * 2^(retryCount - delays.length)
            int lastDelay = delays[delays.length - 1];
            delay = lastDelay * (int) Math.pow(2, Math.min(retryCount - delays.length, 6)); // Cap exponent to 6 (64x)
        }
        return LocalDateTime.now().plusSeconds(delay);
    }
}
