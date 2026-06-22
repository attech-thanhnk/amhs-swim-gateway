package vn.asg.cp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHistoryWithReadStatusDTO {
    private Long id;
    private String title;
    private String description;
    private String eventType;
    private String severity;
    private String eventTime;
    private String createdBy;
    private Boolean isRead;
}
