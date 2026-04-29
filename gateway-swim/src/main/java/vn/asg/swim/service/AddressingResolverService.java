package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.model.ResolvedAddressing;

/**
 * Automatically resolves AMHS originator + recipients for messages from SWIM.
 * <p>
 * Applies Simple Routing priorities:
 * <ol>
 * <li>AMQP Properties</li>
 * <li>Routing Rules — from the `routing` table, based on queue & filter</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddressingResolverService {

    private final RoutingService routingService;
    private final ConfigService configService;
    private final MessageValidationService validationService;

    public ResolvedAddressing resolve(Message amqpMsg, String queue, String body) {
        // Strategy 1: AMQP Properties
        ResolvedAddressing result = resolveFromAmqpProperties(amqpMsg);
        if (resolved(result)) {
            return result;
        }

        // Strategy 2: Simple Routing Rules
        result = resolveFromRoutingRules(queue, body);
        if (resolved(result)) {
            return result;
        }

        // UNRESOLVED
        log.warn("AddressingResolver: UNRESOLVED for queue={}", queue);
        return new ResolvedAddressing(null, null, ResolvedAddressing.SOURCE_UNRESOLVED);
    }

    private ResolvedAddressing resolveFromAmqpProperties(Message amqpMsg) {
        try {
            String recipients = amqpMsg.getStringProperty("amhs_recipients");
            String originator = amqpMsg.getStringProperty("amhs_originator");

            if (recipients != null && !recipients.isBlank() &&
                    originator != null && !originator.isBlank()) {

                var originatorResult = validationService.validateAftnAddress(originator, "amhs_originator");
                if (!originatorResult.isValid()) {
                    log.warn("AddressingResolver: AMQP Property skip — Invalid originator");
                    return null;
                }

                var recipientsResult = validationService.validateAftnRecipients(recipients);
                if (!recipientsResult.isValid()) {
                    log.warn("AddressingResolver: AMQP Property skip — Invalid recipients");
                    return null;
                }

                log.debug("AddressingResolver: Resolved via AMQP Properties");
                return new ResolvedAddressing(originator, normalizeRecipients(recipients),
                        ResolvedAddressing.SOURCE_AMQP_PROPERTY);
            }
        } catch (JMSException e) {
            log.debug("AddressingResolver: AMQP error skip — {}", e.getMessage());
        }
        return null;
    }

    private ResolvedAddressing resolveFromRoutingRules(String queue, String body) {
        String messageFilter = extractMessageType(body);
        var ruleOpt = routingService.findBestMatchIn(queue, messageFilter);

        if (ruleOpt.isPresent()) {
            var rule = ruleOpt.get();
            String recs = normalizeRecipients(rule.getRecipients());
            if (recs != null && !recs.isBlank()) {
                String orig = rule.getOriginator() != null && !rule.getOriginator().isBlank()
                        ? rule.getOriginator()
                        : configService.getDefaultOriginator();
                log.debug("AddressingResolver: Resolved via Routing Rules queue={}, filter={}", queue, messageFilter);
                return new ResolvedAddressing(orig, recs, ResolvedAddressing.SOURCE_ROUTING_RULE);
            }
        }
        return null;
    }

    private boolean resolved(ResolvedAddressing r) {
        return r != null && r.isResolved();
    }

    private String extractMessageType(String body) {
        if (body == null || body.isBlank())
            return null;
        String trimmed = body.stripLeading();

        // Simple heuristic filtering for Simple Routing
        if (trimmed.startsWith("METAR") || trimmed.contains("<iwxxm:METAR"))
            return "METAR";
        if (trimmed.startsWith("TAF") || trimmed.contains("<iwxxm:TAF"))
            return "TAF";
        if (trimmed.startsWith("(FPL") || trimmed.contains("<fx:FlightPlan"))
            return "FPL";
        if (trimmed.startsWith("NOTAM") || trimmed.contains("<aixm:Event"))
            return "NOTAM";
        if (trimmed.startsWith("SIGMET") || trimmed.contains("<iwxxm:SIGMET"))
            return "SIGMET";
        if (trimmed.startsWith("SPECI") || trimmed.contains("<iwxxm:SPECI"))
            return "SPECI";

        return null;
    }

    static String normalizeRecipients(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            return trimmed.replaceAll("[\\[\\]\"]", "").replaceAll(",\\s*", " ").trim();
        }
        return trimmed;
    }
}
