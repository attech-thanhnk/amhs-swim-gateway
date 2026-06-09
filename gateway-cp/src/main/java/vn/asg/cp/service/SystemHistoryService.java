package vn.asg.cp.service;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import vn.asg.cp.entity.SystemHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.asg.cp.repository.SystemHistoryRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class SystemHistoryService {

    private final SystemHistoryRepository repository;

    public Page<SystemHistory> getHistories(int page, int size) {
        return repository.findAllByOrderByEventTimeDesc(PageRequest.of(page, size));
    }

    public void info(String eventType, String title) {
        save(eventType, "INFO", title, null);
    }

    public void warn(String eventType, String title) {
        save(eventType, "WARN", title, null);
    }

    public void error(String eventType,
                      String title,
                      String description) {

        save(eventType, "ERROR", title, description);
    }

    private void save(String eventType,
                      String severity,
                      String title,
                      String description) {

        repository.save(
                SystemHistory.builder()
                        .eventTime(LocalDateTime.now())
                        .eventType(eventType)
                        .severity(severity)
                        .title(title)
                        .description(description)
                        .createdBy("system")
                        .build()
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationStarted() {
        info(
                "APPLICATION_START",
                "Application started"
        );
    }

    @PreDestroy
    public void applicationStopped() {
        info(
                "APPLICATION_STOP",
                "Application stopped"
        );
    }

    public void highMemory(double value) {
        warn(
            "HIGH_MEMORY",
            String.format(
                "Memory usage reached %.2f%%",
                value
            )
        );
    }

    public void highCpu(double value) {
        warn(
            "HIGH_CPU",
            String.format(
                "CPU usage reached %.2f%%",
                value
            )
        );
    }
}
