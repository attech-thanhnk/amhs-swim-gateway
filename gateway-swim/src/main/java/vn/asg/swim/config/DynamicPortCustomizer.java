package vn.asg.swim.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;
import vn.asg.swim.service.ConfigService;

/**
 * Tự động thiết lập Server Port từ Database thay vì đọc application.properties.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicPortCustomizer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    private final ConfigService configService;

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        log.info("DynamicPort: Checking database for SERVER_PORT configuration...");
        try {
            int port = configService.getInt(ConfigService.KEY_SERVER_PORT);
            factory.setPort(port);
            log.info("DynamicPort: Web server port strictly set to {} from database", port);
        } catch (Exception e) {
            log.error("DynamicPort: FAILED to retrieve SERVER_PORT from database. " +
                    "Applying Fail-Fast policy. Error: {}", e.getMessage());
            // Re-throw to prevent application from running with hardcoded/property port
            throw new IllegalStateException(
                    "CRITICAL: SERVER_PORT must be defined in database for 100% dynamic policy.", e);
        }
    }
}
