package vn.asg.cp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String error;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    private Map<String, Object> details;

    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(String error, String message, String path) {
        this.error = error;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.path = path;
    }
}
