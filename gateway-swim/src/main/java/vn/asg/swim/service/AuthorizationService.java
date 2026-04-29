package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EUR Doc 047 §3.5 - Authorization Service
 *
 * Checks authorization for AMHS and SWIM users/enterprises.
 *
 * Configuration Modes (C-19, C-20):
 * - ALL: Accept all users (no filtering)
 * - BY_LIST: Accept only users in whitelist
 * - BY_PRMD: Accept only users from specific PRMD (AMHS only)
 * - BY_ENTERPRISE: Accept only specific SWIM enterprises (SWIM only)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final ConfigService configService;

    /**
     * EUR Doc 047 §4.4.1 - Check AMHS user authorization (C-19)
     *
     * @param originator AFTN address or X.400 O/R address
     * @return true if authorized
     */
    public boolean isAmhsUserAuthorized(String originator) {
        if (originator == null || originator.isBlank()) {
            log.debug("Authorization: AMHS user check skipped (no originator)");
            return true; // Allow messages without originator
        }

        String mode = configService.get(ConfigService.KEY_AUTHORIZED_AMHS_USERS);

        return switch (mode.toUpperCase()) {
            case "ALL" -> {
                log.debug("Authorization: AMHS mode=ALL → ALLOW {}", originator);
                yield true;
            }
            case "BY_LIST" -> checkAmhsWhitelist(originator);
            case "BY_PRMD" -> checkAmhsPrmd(originator);
            default -> {
                log.warn("Authorization: Unknown AMHS mode '{}', defaulting to ALL", mode);
                yield true;
            }
        };
    }

    /**
     * EUR Doc 047 §4.5.1 - Check SWIM user authorization (C-20)
     *
     * @param amqpMsg AMQP message (check user-id, enterprise properties)
     * @return true if authorized
     */
    public boolean isSwimUserAuthorized(Message amqpMsg) {
        String mode = configService.get(ConfigService.KEY_AUTHORIZED_SWIM_USERS);

        if ("ALL".equalsIgnoreCase(mode)) {
            log.debug("Authorization: SWIM mode=ALL → ALLOW");
            return true;
        }

        try {
            // Try to get user-id from AMQP message
            String userId = amqpMsg.getStringProperty("user_id");
            String enterprise = amqpMsg.getStringProperty("swim_enterprise");

            return switch (mode.toUpperCase()) {
                case "BY_LIST" -> checkSwimWhitelist(userId);
                case "BY_ENTERPRISE" -> checkSwimEnterprise(enterprise);
                default -> {
                    log.warn("Authorization: Unknown SWIM mode '{}', defaulting to ALL", mode);
                    yield true;
                }
            };
        } catch (JMSException e) {
            log.warn("Authorization: Failed to read SWIM user properties: {}", e.getMessage());
            return true; // Allow on error (fail-open)
        }
    }

    /**
     * Check if AMHS originator is in whitelist
     */
    private boolean checkAmhsWhitelist(String originator) {
        String whitelist = configService.get("AUTHORIZED_AMHS_ADDRESSES");
        if (whitelist.isBlank()) {
            log.warn("Authorization: AMHS BY_LIST mode but whitelist is empty → ALLOW ALL");
            return true;
        }

        boolean authorized = whitelist.contains(originator);
        log.debug("Authorization: AMHS BY_LIST → {} for {}", authorized ? "ALLOW" : "DENY", originator);
        return authorized;
    }

    /**
     * Check if AMHS originator is from authorized PRMD
     */
    private boolean checkAmhsPrmd(String originator) {
        String authorizedPrmds = configService.get("AUTHORIZED_AMHS_PRMDS");
        if (authorizedPrmds.isBlank()) {
            log.warn("Authorization: AMHS BY_PRMD mode but PRMD list is empty → ALLOW ALL");
            return true;
        }

        // AFTN address format: XXXXYYYYZ (first 4 chars = location, next 3 = unit, last
        // 1 = letter)
        // PRMD typically matches location code (first 2-4 chars)
        if (originator.length() < 4) {
            log.debug("Authorization: AMHS BY_PRMD → DENY {} (invalid format)", originator);
            return false;
        }

        String prmdPrefix = originator.substring(0, 4);
        boolean authorized = authorizedPrmds.contains(prmdPrefix);
        log.debug("Authorization: AMHS BY_PRMD → {} for {} (PRMD={})",
                authorized ? "ALLOW" : "DENY", originator, prmdPrefix);
        return authorized;
    }

    /**
     * Check if SWIM user is in whitelist
     */
    private boolean checkSwimWhitelist(String userId) {
        if (userId == null || userId.isBlank()) {
            log.debug("Authorization: SWIM BY_LIST → DENY (no user_id)");
            return false;
        }

        String whitelist = configService.get("AUTHORIZED_SWIM_USERS");
        if (whitelist.isBlank()) {
            log.warn("Authorization: SWIM BY_LIST mode but whitelist is empty → ALLOW ALL");
            return true;
        }

        boolean authorized = whitelist.contains(userId);
        log.debug("Authorization: SWIM BY_LIST → {} for {}", authorized ? "ALLOW" : "DENY", userId);
        return authorized;
    }

    /**
     * Check if SWIM message is from authorized enterprise
     */
    private boolean checkSwimEnterprise(String enterprise) {
        if (enterprise == null || enterprise.isBlank()) {
            log.debug("Authorization: SWIM BY_ENTERPRISE → DENY (no swim_enterprise)");
            return false;
        }

        String authorizedEnterprises = configService.get("AUTHORIZED_SWIM_ENTERPRISES");
        if (authorizedEnterprises.isBlank()) {
            log.warn("Authorization: SWIM BY_ENTERPRISE mode but enterprise list is empty → ALLOW ALL");
            return true;
        }

        boolean authorized = authorizedEnterprises.contains(enterprise);
        log.debug("Authorization: SWIM BY_ENTERPRISE → {} for {}", authorized ? "ALLOW" : "DENY", enterprise);
        return authorized;
    }
}
