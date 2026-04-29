package vn.asg.cp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.cp.entity.GatewayConfig;
import vn.asg.cp.repository.GatewayConfigRepository;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    public static final String KEY_SERVER_PORT = "SERVER_PORT_CP";
    private final GatewayConfigRepository repository;

    /**
     * Lấy cấu hình bắt buộc từ DB.
     * Mặc định IGNORE giá trị hardcoded trong code để đảm bảo Động 100%.
     */
    public String get(String key) {
        return repository.findByConfigKey(key)
                .map(GatewayConfig::getConfigValue)
                .orElseThrow(() -> {
                    log.error("CRITICAL CONFIG MISSING: '{}' is not defined in gateway_config table!", key);
                    return new IllegalStateException(
                            "Mandatory configuration '" + key + "' missing in Database.");
                });
    }

    public List<String> getCommaSeparatedConfig(String key) {
        String val = get(key);
        return Arrays.asList(val.split("\\s*,\\s*"));
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }
}
