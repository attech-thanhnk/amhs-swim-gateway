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

    public static class BatchError {
        private Long msgid;
        private String error;

        public BatchError() {}

        public BatchError(Long msgid, String error) {
            this.msgid = msgid;
            this.error = error;
        }

        public Long getMsgid() { return msgid; }
        public void setMsgid(Long msgid) { this.msgid = msgid; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public void addError(Long msgid, String error) {
        errors.add(new BatchError(msgid, error));
    }
}
