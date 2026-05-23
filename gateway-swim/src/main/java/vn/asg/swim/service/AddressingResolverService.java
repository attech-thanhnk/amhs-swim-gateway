package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.model.ResolvedAddressing;

/**
 * Tự động phân giải (resolve) AMHS originator + recipients cho các bản tin đến từ SWIM.
 * <p>
 * Áp dụng thứ tự ưu tiên Simple Routing:
 * <ol>
 * <li>AMQP Properties</li>
 * <li>Routing Rules — cấu hình trong bảng `routing` dựa theo queue & filter</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddressingResolverService {

    private final RoutingService routingService;
    private final ConfigService configService;
    private final MessageValidationService validationService;
    private final MessageDetectService detectService;

    /**
     * Phân giải địa chỉ AMHS gửi và nhận từ bản tin AMQP.
     */
    public ResolvedAddressing resolve(Message amqpMsg, String queue, String body) {
        // Chiến lược 1: Sử dụng AMQP Properties
        ResolvedAddressing result = resolveFromAmqpProperties(amqpMsg);
        if (resolved(result)) {
            return result;
        }

        // Chiến lược 2: Sử dụng Simple Routing Rules
        result = resolveFromRoutingRules(queue, body);
        if (resolved(result)) {
            return result;
        }

        // Không phân giải được địa chỉ
        log.warn("AddressingResolver: UNRESOLVED for queue={}", queue);
        return new ResolvedAddressing(null, null, ResolvedAddressing.SOURCE_UNRESOLVED);
    }

    /**
     * Phân giải địa chỉ từ các thuộc tính AMQP (amhs_originator, amhs_recipients).
     */
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

    /**
     * Phân giải địa chỉ dựa theo cấu hình định tuyến trong database.
     */
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

    /**
     * Kiểm tra đối tượng địa chỉ đã được phân giải thành công hay chưa.
     */
    private boolean resolved(ResolvedAddressing r) {
        return r != null && r.isResolved();
    }

    /**
     * Nhận dạng loại bản tin từ nội dung body.
     */
    private String extractMessageType(String body) {
        String detected = detectService.detect(body);
        return "UNKNOWN".equals(detected) ? null : detected;
    }

    /**
     * Chuẩn hóa danh sách người nhận (loại bỏ ngoặc vuông, dấu phẩy, dấu nháy kép).
     */
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
