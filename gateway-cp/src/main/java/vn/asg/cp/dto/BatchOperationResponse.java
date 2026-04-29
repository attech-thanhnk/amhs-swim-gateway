package vn.asg.cp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO cho batch operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResponse {

    private int processed;
    private int succeeded;
    private int failed;
    private List<BatchError> errors = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchError {
        private Long msgid;
        private String error;
    }

    public void addError(Long msgid, String error) {
        errors.add(new BatchError(msgid, error));
    }
}
