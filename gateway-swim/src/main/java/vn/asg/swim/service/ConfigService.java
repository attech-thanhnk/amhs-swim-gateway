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
     * Lấy giá trị cấu hình kiểu Boolean.
     */
    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    /**
     * Lấy AFTN originator mặc định.
     */
    public String getDefaultOriginator() {
        return get(KEY_DEFAULT_ORIGINATOR);
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
