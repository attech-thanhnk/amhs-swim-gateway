package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EUR Doc 047 §3.5 - Dịch vụ Xác thực Quyền hạn (Authorization Service)
 *
 * Kiểm tra quyền hạn (authorization) đối với người dùng AMHS và SWIM.
 *
 * Các chế độ Cấu hình (C-19, C-20):
 * - ALL: Chấp nhận tất cả người dùng (không lọc)
 * - BY_LIST: Chỉ chấp nhận người dùng trong whitelist
 * - BY_PRMD: Chỉ chấp nhận người dùng từ PRMD cụ thể (chỉ dành cho AMHS)
 * - BY_ENTERPRISE: Chỉ chấp nhận các doanh nghiệp SWIM cụ thể (chỉ dành cho
 * SWIM)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final ConfigService configService;

    /**
     * EUR Doc 047 §4.4.1 - Kiểm tra quyền hạn người dùng AMHS (C-19)
     *
     * @param originator Địa chỉ AFTN hoặc địa chỉ X.400 O/R
     * @return true nếu được phép truy cập
     */
    public boolean isAmhsUserAuthorized(String originator) {
        if (originator == null || originator.isBlank()) {
            log.debug("Authorization: AMHS user check skipped (no originator)");
            return true; // Cho phép bản tin không có originator
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
     * EUR Doc 047 §4.5.1 - Kiểm tra quyền hạn người dùng SWIM (C-20)
     *
     * @param amqpMsg Bản tin AMQP (kiểm tra các thuộc tính user-id, enterprise)
     * @return true nếu được phép truy cập
     */
    public boolean isSwimUserAuthorized(Message amqpMsg) {
        String mode = configService.get(ConfigService.KEY_AUTHORIZED_SWIM_USERS);

        if ("ALL".equalsIgnoreCase(mode)) {
            log.debug("Authorization: SWIM mode=ALL → ALLOW");
            return true;
        }

        try {
            // Thử lấy thông tin user-id từ bản tin AMQP
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
            return true; // Cho phép đi qua nếu gặp lỗi (fail-open)
        }
    }

    /**
     * Kiểm tra xem originator AMHS có nằm trong whitelist hay không
     */
    private boolean checkAmhsWhitelist(String originator) {
        String whitelist = configService.get("AUTHORIZED_AMHS_ADDRESSES");
        if (whitelist.isBlank()) {
            log.warn("Authorization: AMHS BY_LIST mode but whitelist is empty → ALLOW ALL");
            return true;
        }

        boolean authorized = containsExact(whitelist, originator);
        log.debug("Authorization: AMHS BY_LIST → {} for {}", authorized ? "ALLOW" : "DENY", originator);
        return authorized;
    }

    /**
     * Kiểm tra xem originator AMHS có thuộc PRMD được cấp quyền hay không
     */
    private boolean checkAmhsPrmd(String originator) {
        String authorizedPrmds = configService.get("AUTHORIZED_AMHS_PRMDS");
        if (authorizedPrmds.isBlank()) {
            log.warn("Authorization: AMHS BY_PRMD mode but PRMD list is empty → ALLOW ALL");
            return true;
        }

        // Định dạng địa chỉ AFTN: XXXXYYYYZ (4 ký tự đầu = location, 3 ký tự tiếp =
        // unit, ký tự cuối cùng = letter)
        // PRMD thường khớp với mã vị trí (2-4 ký tự đầu tiên)
        if (originator.length() < 4) {
            log.debug("Authorization: AMHS BY_PRMD → DENY {} (invalid format)", originator);
            return false;
        }

        String prmdPrefix = originator.substring(0, 4);
        boolean authorized = containsExact(authorizedPrmds, prmdPrefix);
        log.debug("Authorization: AMHS BY_PRMD → {} for {} (PRMD={})",
                authorized ? "ALLOW" : "DENY", originator, prmdPrefix);
        return authorized;
    }

    /**
     * Kiểm tra xem người dùng SWIM có nằm trong whitelist hay không
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

        boolean authorized = containsExact(whitelist, userId);
        log.debug("Authorization: SWIM BY_LIST → {} for {}", authorized ? "ALLOW" : "DENY", userId);
        return authorized;
    }

    /**
     * Kiểm tra xem bản tin SWIM có thuộc doanh nghiệp được cấp quyền hay không
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

        boolean authorized = containsExact(authorizedEnterprises, enterprise);
        log.debug("Authorization: SWIM BY_ENTERPRISE → {} for {}", authorized ? "ALLOW" : "DENY", enterprise);
        return authorized;
    }

    /**
     * So khớp chính xác phần tử trong danh sách ngăn cách bởi dấu cách hoặc dấu phẩy.
     */
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
