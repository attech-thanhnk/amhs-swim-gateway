package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EUR Doc 047 §3.3.3 - ATSMHS Service Level Selection
 *
 * Determines whether to use Extended or Basic ATSMHS service level
 * when converting SWIM messages to AMHS IPM.
 *
 * Service Level Modes (C-08):
 * - EXTENDED: Always use extended ATSMHS (supports binary content)
 * - BASIC: Always use basic ATSMHS (text only, reject binary)
 * - CONTENT_BASED: Decide based on content-type
 * - RECIPIENTS_BASED: Decide based on recipient capabilities
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtsmhsServiceLevelResolver {

    private final ConfigService configService;

    public static final String EXTENDED = "EXTENDED";
    public static final String BASIC = "BASIC";

    /**
     * EUR Doc 047 §3.3.3.1-5: Resolve ATSMHS service level
     *
     * @param contentType AMQP content-type header
     * @param recipients  Space-separated AFTN addresses
     * @return EXTENDED or BASIC
     */
    public String resolve(String contentType, String recipients) {
        String mode = configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL);

        return switch (mode.toUpperCase()) {
            case "EXTENDED" -> {
                log.debug("ATSMHS: mode=EXTENDED → EXTENDED");
                yield EXTENDED;
            }
            case "BASIC" -> {
                log.debug("ATSMHS: mode=BASIC → BASIC");
                yield BASIC;
            }
            case "CONTENT_BASED" -> resolveByContent(contentType);
            case "RECIPIENTS_BASED" -> resolveByRecipients(recipients);
            default -> {
                log.warn("ATSMHS: unknown mode '{}', defaulting to CONTENT_BASED", mode);
                yield resolveByContent(contentType);
            }
        };
    }

    /**
     * EUR Doc 047 §3.3.3.3 - Content-based mode (C-11)
     *
     * Binary content (application/octet-stream) → EXTENDED
     * Text content → BASIC
     */
    private String resolveByContent(String contentType) {
        if (contentType != null && contentType.toLowerCase().contains("octet-stream")) {
            log.debug("ATSMHS: content-based → EXTENDED (binary content)");
            return EXTENDED;
        }
        log.debug("ATSMHS: content-based → BASIC (text content)");
        return BASIC;
    }

    /**
     * EUR Doc 047 §3.3.3.4 - Recipients-based mode (C-12)
     *
     * If ALL recipients support extended ATSMHS → EXTENDED
     * Otherwise → BASIC
     *
     * Note: Current implementation assumes all recipients support extended.
     * In production, this should query X.500 Directory Service or local
     * capability database.
     */
    private String resolveByRecipients(String recipients) {
        if (recipients == null || recipients.isBlank()) {
            log.debug("ATSMHS: recipients-based → BASIC (no recipients)");
            return BASIC;
        }

        // For now, check if recipients are in known extended-capable list
        String extendedCapableAddresses = configService.get("ATSMHS_EXTENDED_CAPABLE_ADDRESSES");

        if (extendedCapableAddresses.isBlank()) {
            // Default: assume all modern AMHS units support extended
            log.debug("ATSMHS: recipients-based → EXTENDED (default assumption)");
            return EXTENDED;
        }

        String[] recipientArray = recipients.trim().split("\\s+");
        for (String recipient : recipientArray) {
            if (!extendedCapableAddresses.contains(recipient)) {
                log.debug("ATSMHS: recipients-based → BASIC (recipient {} not extended-capable)", recipient);
                return BASIC;
            }
        }

        log.debug("ATSMHS: recipients-based → EXTENDED (all recipients capable)");
        return EXTENDED;
    }

    /**
     * EUR Doc 047 §3.3.3.2 - Validate content against service level (C-10)
     *
     * BASIC mode cannot handle binary content → must reject
     *
     * @return true if content is valid for the service level
     */
    public boolean validateContent(String serviceLevel, String contentType, boolean hasBinaryContent) {
        if (BASIC.equals(serviceLevel) && hasBinaryContent) {
            log.warn("ATSMHS: BASIC mode cannot handle binary content (content-type={})", contentType);
            return false;
        }
        return true;
    }
}
