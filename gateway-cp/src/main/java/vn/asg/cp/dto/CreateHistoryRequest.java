package vn.asg.cp.dto;
import lombok.Data;

@Data
public class CreateHistoryRequest {

    private String eventType;
    private String severity;
    private String title;
    private String description;
    private String createdBy;
}
