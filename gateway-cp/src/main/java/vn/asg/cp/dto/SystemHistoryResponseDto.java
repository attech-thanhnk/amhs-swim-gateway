package vn.asg.cp.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class SystemHistoryResponseDto {
    private Long id;
    private LocalDateTime eventTime;
    private String eventType;
    private String severity;
    private String title;
    private String description;
    private Boolean isRead;

    public SystemHistoryResponseDto(Long id, LocalDateTime eventTime,
                                    String eventType, String severity,
                                    String title, String description,
                                    Boolean isRead) {
        this.id = id;
        this.eventTime = eventTime;
        this.eventType = eventType;
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.isRead = isRead;
    }

}
