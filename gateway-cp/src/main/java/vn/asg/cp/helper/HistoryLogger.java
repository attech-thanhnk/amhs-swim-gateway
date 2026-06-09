package vn.asg.cp.helper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.asg.cp.entity.SystemHistory;
import vn.asg.cp.repository.SystemHistoryRepository;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HistoryLogger {

    private final SystemHistoryRepository repository;

    public void info(String eventType, String title) {

        repository.save(
                SystemHistory.builder()
                        .eventTime(LocalDateTime.now())
                        .eventType(eventType)
                        .severity("INFO")
                        .title(title)
                        .createdBy("system")
                        .build()
        );
    }

    public void error(String eventType,
                      String title,
                      String description) {

        repository.save(
                SystemHistory.builder()
                        .eventTime(LocalDateTime.now())
                        .eventType(eventType)
                        .severity("ERROR")
                        .title(title)
                        .description(description)
                        .createdBy("system")
                        .build()
        );
    }
}
