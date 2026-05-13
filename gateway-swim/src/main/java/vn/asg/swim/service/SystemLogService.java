package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.SystemLog;
import vn.asg.swim.repository.SystemLogRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * System Log Service (system_log).
 * Used for administrative events and process tracking rather than business
 * logic errors.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemLogService {

    private final SystemLogRepository systemLogRepository;

    public void info(String module, String content) {
        log("INFO", module, content);
    }

    public void error(String module, String content) {
        log("ERROR", module, content);
    }

    public void debug(String module, String content) {
        log("DEBUG", module, content);
    }

    /**
     * Records a single entry into the system log.
     */
    public void log(String level, String module, String content) {
        try {
            SystemLog entry = new SystemLog();
            entry.setUuid(UUID.randomUUID().toString());
            entry.setTimestamp(LocalDateTime.now());
            entry.setLevel(level);
            entry.setModule(module);
            entry.setContent(content);
            entry.setStatus("UNREAD");
            systemLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write system_log: {}", e.getMessage());
        }
    }
}
