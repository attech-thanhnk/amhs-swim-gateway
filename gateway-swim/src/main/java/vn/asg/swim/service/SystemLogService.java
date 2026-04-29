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

    /**
     * Records a single entry into the system log.
     *
     * @param level   Log level (INFO, DEBUG, etc.)
     * @param module  Module name originating the log
     * @param content Detailed content
     */
    public void log(String level, String module, String content) {
        try {
            SystemLog entry = SystemLog.builder()
                    .uuid(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now())
                    .level(level)
                    .module(module)
                    .content(content)
                    .status("UNREAD")
                    .build();
            systemLogRepository.save(entry);
            log.info("System log recorded: [{}] {}", module, content);
        } catch (Exception e) {
            log.error("Failed to write system_log: {}", e.getMessage());
        }
    }
}
