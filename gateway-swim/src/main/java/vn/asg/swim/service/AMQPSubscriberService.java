package vn.asg.swim.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.Gwin;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.model.ResolvedAddressing;
import vn.asg.swim.repository.GwinRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SWIM → AMHS: subscribes to topics from Solace, validates and records into
 * gwin with PENDING status.
 * Dispatching to AMHS MTA is handled by InboundDispatchService.
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

    private final List<Session> activeSessions = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

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
            return;
        }

        for (String queue : queues) {
            subscribeQueue(queue);
        }
        log.info("Subscribed to {} queues: {}", queues.size(), queues);
    }

    private void subscribeQueue(String queue) {
        try {
            Session session = connectionManager.createSession();
            activeSessions.add(session);

            // Can change to createQueue if P2P, project spec requires topic
            MessageConsumer consumer = connectionManager.createConsumer(session, queue);
            consumer.setMessageListener(msg -> {
                try {
                    handleMessage(msg, queue);
                } catch (Exception e) {
                    log.error("Error handling AMQP message from queue {}: {}", queue, e.getMessage(), e);
                }
            });

        } catch (JMSException e) {
            log.error("Failed to subscribe to queue {}: {}", queue, e.getMessage());
        }
    }

    /**
     * Validate AMQP message, resolve AMHS addressing, and write to Gwin.
     * <p>
     * Resulting Status:
     * - PENDING: Resolution successful, ready for dispatch.
     * - UNROUTED: Failed to resolve address, waiting for manual operator
     * intervention.
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
                amqpMsgId = "GW-GEN-" + java.util.UUID.randomUUID().toString();
                log.info("AMQP message has no JMSMessageID, generated synthetic ID: {}", amqpMsgId);
            }
        }
        log.info("Received AMQP message: {} from topic: {}", amqpMsgId, queue);

        // --- SELF-MESSAGE FILTER (ECHO CANCELLATION) ---
        // EUR Doc 047: A producer should ignore its own messages to avoid loops.
        String originator = amqpMsg.getStringProperty("amhs_originator");
        String localCentre = vn.asg.converter.config.App.getInstance().getString("CentreDesignator");
        if (originator != null && localCentre != null && originator.startsWith(localCentre)) {
            log.info("AMQP message {} is an echo from our own centre ({}). Ignoring.", amqpMsgId, localCentre);
            return;
        }

        // Deduplication (prevent duplicate processing per spec)
        if (gwinRepository.existsByMessageId(amqpMsgId)) {
            log.warn("AMQP message {} already exists in gwin. Ignoring duplicate.", amqpMsgId);
            return;
        }

        // EUR Doc 047 §4.5.1 - Authorization check (C-20)
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

        // 0. Extract body FIRST for validation
        String textPayload = null;
        byte[] binaryPayload = null;
        if (amqpMsg instanceof TextMessage tm) {
            textPayload = tm.getText();
        } else if (amqpMsg instanceof BytesMessage bm) {
            byte[] buf = new byte[(int) bm.getBodyLength()];
            bm.readBytes(buf);
            // Thử decode UTF-8: nếu là XML/text thì giữ nguyên chuỗi
            // (Solace Try Me! gửi BytesMessage nhưng nội dung là text)
            String asUtf8 = new String(buf, java.nio.charset.StandardCharsets.UTF_8).stripLeading();
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
                : (binaryPayload != null ? java.util.Base64.getEncoder().encodeToString(binaryPayload) : null);

        // EUR Doc 047 Validation: C-02, S-06, S-08, S-09
        MessageValidationService.ValidationResult validationResult = validationService.validateSwimToAmhs(amqpMsgId,
                amqpMsg,
                finalContent);

        if (!validationResult.isValid()) {
            log.error("AMQP message {} validation FAILED: {}", amqpMsgId, validationResult.getErrorMessage());

            // EUR Doc 047 §4.5.1: Reject + log + alert CP on validation failure
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_ERROR,
                    "Message validation failed: " + amqpMsgId + " - " + validationResult.getErrorMessage(),
                    "gwin", null);

            conversionService.logSwimToAmhs(amqpMsgId, null, "REJECTED", "validation-failed",
                    validationResult.getErrorMessage());

            // Do not save to gwin - message is rejected
            return;
        }

        // 1. Priority
        int priority;
        try {
            priority = amqpMsg.getJMSPriority();
        } catch (Exception e) {
            log.warn("Cannot read JMS priority for {}, defaulting to 2", amqpMsgId);
            priority = 2;
        }

        // 2. Content type and subject
        String contentType = amqpMsg.getStringProperty("JMS_AMQP_CONTENT_TYPE");
        String subject = amqpMsg.getStringProperty("amhs_subject");
        if (subject == null)
            subject = "SWIM_INTERWORKING";

        // EUR Doc 047 §4.5.2.1: Read AMQP application properties (S-05, S-24, S-25)
        String atsPriority = amqpMsg.getStringProperty("ats_priority");
        String amhsAtsFt = amqpMsg.getStringProperty("amhs_ats_ft");
        String amhsAtsOhi = amqpMsg.getStringProperty("amhs_ats_ohi");
        String amhsIpmId = amqpMsg.getStringProperty("amhs_ipm_id");
        String amhsBodypartType = amqpMsg.getStringProperty("amhs_bodypart_type");
        String amhsContentEncoding = amqpMsg.getStringProperty("amhs_content_encoding");
        String amhsMessageSigned = amqpMsg.getStringProperty("amhs_message_signed");

        // S-05: ats_priority overrides JMSPriority
        if (atsPriority != null && !atsPriority.isBlank()) {
            priority = vn.asg.swim.model.AmqpProperties.mapAtsPriorityToAmqp(atsPriority);
            log.debug("AMQP {}: ats_priority={} → priority={}", amqpMsgId, atsPriority, priority);
        }

        // Build JSON for amqp_properties field
        StringBuilder propsJson = new StringBuilder("{");
        if (atsPriority != null)
            propsJson.append("\"ats_priority\":\"").append(atsPriority).append("\",");
        if (amhsAtsFt != null)
            propsJson.append("\"amhs_ats_ft\":\"").append(amhsAtsFt).append("\",");
        if (amhsAtsOhi != null)
            propsJson.append("\"amhs_ats_ohi\":\"").append(amhsAtsOhi).append("\",");
        if (amhsIpmId != null)
            propsJson.append("\"amhs_ipm_id\":\"").append(amhsIpmId).append("\",");
        if (amhsBodypartType != null)
            propsJson.append("\"amhs_bodypart_type\":\"").append(amhsBodypartType).append("\",");
        if (amhsContentEncoding != null)
            propsJson.append("\"amhs_content_encoding\":\"").append(amhsContentEncoding).append("\",");
        if (amhsMessageSigned != null)
            propsJson.append("\"amhs_message_signed\":\"").append(amhsMessageSigned).append("\",");
        if (contentType != null)
            propsJson.append("\"content_type\":\"").append(contentType).append("\",");
        if (subject != null)
            propsJson.append("\"subject\":\"").append(subject).append("\",");
        // Remove trailing comma
        if (propsJson.charAt(propsJson.length() - 1) == ',') {
            propsJson.setLength(propsJson.length() - 1);
        }
        propsJson.append("}");

        final String amqpPropertiesJson = propsJson.toString();

        // 3. Resolve AMHS addressing
        ResolvedAddressing resolved = addressingResolver.resolve(amqpMsg, queue, finalContent);

        // EUR Doc 047 §3.3.3 - ATSMHS Service Level check (C-08 → C-12)
        if (resolved.isResolved()) {
            String serviceLevel = atsmhsResolver.resolve(contentType, resolved.recipients());
            boolean hasBinaryContent = binaryPayload != null;

            // C-10: Basic mode cannot handle binary content
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

        // 4. Create gwin record
        Gwin gwin = new Gwin();
        gwin.setMessageId(amqpMsgId);
        gwin.setSource(queue);
        gwin.setSubject(subject);
        gwin.setAmqpProperties(amqpPropertiesJson); // EUR Doc 047: Save all AMQP properties
        gwin.setPriority((byte) Math.min(Math.max(priority, 0), 9));
        gwin.setTime(LocalDateTime.now());
        gwin.setXmlPayload(finalContent);
        try {
            String tac = conversionService.toAmhs(finalContent, subject);
            gwin.setText(tac);
        } catch (Exception e) {
            log.warn("Auto-revert failed for AMQP {}, storing raw XML in text as fallback", amqpMsgId);
            gwin.setText(finalContent);
        }

        gwin.setBodyType("text");
        gwin.setContentType(contentType);
        gwin.setOrigin(resolved.originator());
        gwin.setAddress(resolved.recipients());
        gwin.setAddressingSource(resolved.source());

        if (resolved.isResolved()) {
            gwin.setStatus(Gwin.STATUS_PENDING);
            log.info("AMQP {} → gwin PENDING | source={} originator={} recipients={}",
                    amqpMsgId, resolved.source(), resolved.originator(), resolved.recipients());
        } else {
            // UNRESOLVED: do not reject, save for manual operator intervention
            gwin.setStatus(Gwin.STATUS_UNROUTED);
            log.warn("AMQP {} → gwin UNROUTED | Failed to resolve AMHS address (queue={})",
                    amqpMsgId, queue);
            alertService.create(
                    GwAlert.TYPE_ROUTING_ERROR,
                    GwAlert.SEV_WARNING,
                    "UNROUTED: Failed to resolve AMHS address for message " + amqpMsgId
                            + " from queue " + queue,
                    "gwin", null);
        }

        gwinRepository.save(gwin);

        String actionTag = "received-" + resolved.source().toLowerCase().replaceAll("[^a-z0-9]", "_");
        conversionService.logSwimToAmhs(amqpMsgId, resolved.originator(),
                resolved.isResolved() ? "OK" : "UNROUTED",
                actionTag,
                resolved.isResolved() ? null : "MISSING_AMHS_RECIPIENTS",
                amhsIpmId); // EUR Doc 047 §4.3.4f (G-14)

        log.info("AMQP message {} written to gwin#{}", amqpMsgId, gwin.getMsgid());
    }

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

    @PreDestroy
    public void destroy() {
        stopAll();
    }

    /**
     * Checks if string is likely text (not binary).
     * BytesMessage payload as text or Base64.
     */
    private boolean isProbablyText(String s) {
        if (s == null || s.isEmpty())
            return false;
        // Đếm ký tự điều khiển (ngoài tab, newline, carriage return)
        long controlChars = s.chars()
                .filter(c -> c < 32 && c != '\t' && c != '\n' && c != '\r')
                .count();
        // Nếu < 5% là ký tự điều khiển thì coi là text
        return controlChars < s.length() * 0.05;
    }
}
