package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.GatewayConfig;
import vn.asg.swim.repository.GatewayConfigRepository;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    public static final String KEY_MAX_MESSAGE_RECIPIENTS = "MAX_MESSAGE_RECIPIENTS";
    public static final String KEY_POLL_INTERVAL_MS = "POLL_INTERVAL_MS";
    public static final String KEY_DEFAULT_ORIGINATOR = "DEFAULT_ORIGINATOR_AFTN";
    public static final String KEY_CONVERSION_DIRECTION = "CONVERSION_DIRECTION";
    public static final String KEY_ATSMHS_SERVICE_LEVEL = "ATSMHS_SERVICE_LEVEL";
    public static final String KEY_AUTHORIZED_AMHS_USERS = "AUTHORIZED_AMHS_USERS";
    public static final String KEY_AUTHORIZED_SWIM_USERS = "AUTHORIZED_SWIM_USERS";
    public static final String KEY_STRICT_COMPLIANCE_MODE = "STRICT_COMPLIANCE_MODE";
    public static final String KEY_MAX_MSG_DATA_SIZE = "MAX_MSG_DATA_SIZE";
    public static final String KEY_SERVER_PORT = "SERVER_PORT_SWIM";
    public static final String KEY_GATEWAY_ID = "GATEWAY_ID";
    public static final String KEY_ALLOW_NON_ISO646_REPERTOIRE = "ALLOW_NON_ISO646_REPERTOIRE";
    public static final String KEY_GATEWAY_AMHS_ADDRESS = "GATEWAY_AMHS_ADDRESS";

    /** Địa chỉ AFTN mặc định của gateway khi cấu hình chưa được khai báo. */
    public static final String DEFAULT_GATEWAY_AMHS_ADDRESS = "VVTSSWIM";

    private final GatewayConfigRepository repository;

    /**
     * Lấy giá trị cấu hình bắt buộc từ cơ sở dữ liệu.
     * Ném ra lỗi nghiêm trọng nếu cấu hình không tồn tại.
     */
    public String get(String key) {
        return repository.findByConfigKey(key)
                .map(GatewayConfig::getConfigValue)
                .orElseThrow(() -> {
                    log.error("CRITICAL CONFIG MISSING: '{}' is not defined in gateway_config table!", key);
                    return new IllegalStateException("Mandatory configuration '" + key + "' missing in Database");
                });
    }

    /**
     * Lấy danh sách cấu hình dạng phân tách bởi dấu phẩy.
     */
    public List<String> getCommaSeparatedConfig(String key) {
        String val = get(key);
        return Arrays.asList(val.split("\\s*,\\s*"));
    }

    /**
     * Lấy giá trị cấu hình kiểu Integer.
     */
    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    /**
     * Lấy giá trị cấu hình kiểu Integer có fallback về giá trị mặc định.
     */
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Lấy giá trị cấu hình kiểu Boolean.
     */
    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    /**
     * Lấy AFTN originator mặc định.
     * <p>
     * Đây là địa chỉ ITCU dùng làm <b>originator</b> khi dựng bản tin AMHS ở chiều SWIM → AMHS.
     * KHÔNG dùng key này làm địa chỉ gateway ở chiều AMHS → SWIM — xem
     * {@link #getGatewayAmhsAddress()}.
     */
    public String getDefaultOriginator() {
        return get(KEY_DEFAULT_ORIGINATOR);
    }

    /**
     * Địa chỉ AFTN mà gateway dùng để NHẬN bản tin từ AMHS (mặc định "VVTSSWIM").
     * <p>
     * EUR Doc 047 §4.4.3.4.4: {@code amhs_recipients} phải là danh sách recipient mà ITCU chịu
     * trách nhiệm chuyển giao, nên chính địa chỉ gateway phải bị loại khỏi danh sách đó. Trước
     * đây chiều AMHS → SWIM mượn tạm {@link #KEY_DEFAULT_ORIGINATOR}, nhưng hai khái niệm khác
     * nhau: đặt originator của chiều SWIM → AMHS sang giá trị khác sẽ làm địa chỉ gateway không
     * còn bị loại. Tách thành key riêng để hai chiều độc lập nhau.
     */
    public String getGatewayAmhsAddress() {
        try {
            String value = get(KEY_GATEWAY_AMHS_ADDRESS);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        } catch (Exception e) {
            log.warn("{} chưa khai báo trong gateway_config, dùng mặc định '{}'",
                    KEY_GATEWAY_AMHS_ADDRESS, DEFAULT_GATEWAY_AMHS_ADDRESS);
        }
        return DEFAULT_GATEWAY_AMHS_ADDRESS;
    }

    /**
     * Kiểm tra chế độ tuân thủ nghiêm ngặt (Strict Compliance Mode).
     */
    public boolean isStrictComplianceMode() {
        return getBoolean(KEY_STRICT_COMPLIANCE_MODE);
    }

    /**
     * Lấy chiều chuyển đổi định dạng (Conversion Direction).
     */
    public String getConversionDir() {
        return get(KEY_CONVERSION_DIRECTION);
    }

    /**
     * Lấy số lượng người nhận tối đa được cấu hình.
     */
    public int getMaxMsgRecipients() {
        return getInt(KEY_MAX_MESSAGE_RECIPIENTS);
    }

    /**
     * Lấy dung lượng bản tin tối đa được cấu hình.
     */
    public int getMaxMsgDataSize() {
        return getInt(KEY_MAX_MSG_DATA_SIZE);
    }

    /**
     * EUR Doc 047 §4.4.2.3 / Appendix A CTSW019: chính sách nội bộ của AMHS Management Domain
     * đối với general-text-body-part có repertoire khác ISO 646 (ISO 8859-x, Cyrillic, Arabic,
     * Greek, Hebrew, CJK...). true = vẫn chuyển đổi sang AMQP, false = từ chối và sinh NDR.
     * Mặc định true (chuyển đổi) khi cấu hình chưa được khai báo.
     */
    public boolean isNonIso646RepertoireAllowed() {
        try {
            String value = get(KEY_ALLOW_NON_ISO646_REPERTOIRE);
            return value == null || value.isBlank() || Boolean.parseBoolean(value.trim());
        } catch (Exception e) {
            log.warn("{} not found in DB, defaulting to 'allowed' (convert)", KEY_ALLOW_NON_ISO646_REPERTOIRE);
            return true;
        }
    }

    /**
     * Lấy chu kỳ quét định cấu hình (Poll Interval) tính bằng mili giây.
     */
    public long getPollIntervalMs() {
        return (long) getInt(KEY_POLL_INTERVAL_MS);
    }

    /**
     * Lấy định danh Gateway (Gateway ID).
     */
    public String getGatewayId() {
        try {
            return get(KEY_GATEWAY_ID);
        } catch (Exception e) {
            log.warn("GATEWAY_ID not found in DB, using default 'GW-01'");
            return "GW-01";
        }
    }
}
