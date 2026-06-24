package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.SystemLog;
import vn.asg.swim.repository.SystemLogRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dịch vụ ghi log hệ thống (system_log).
 * Được sử dụng cho các sự kiện quản trị và theo dõi tiến trình thay vì các lỗi logic nghiệp vụ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemLogService {

    private final SystemLogRepository systemLogRepository;

    /**
     * Ghi log mức độ INFO.
     */
    public void info(String module, String content) {
        log("INFO", module, content);
    }

    /**
     * Ghi log mức độ ERROR.
     */
    public void error(String module, String content) {
        log("ERROR", module, content);
    }

    /**
     * Ghi log mức độ DEBUG.
     */
    public void debug(String module, String content) {
        log("DEBUG", module, content);
    }

    /**
     * Ghi một bản ghi đơn lẻ vào nhật ký hệ thống.
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
