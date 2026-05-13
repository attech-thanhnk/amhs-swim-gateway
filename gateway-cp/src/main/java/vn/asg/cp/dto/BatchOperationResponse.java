package vn.asg.cp.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO cho batch operations.
 */
public class BatchOperationResponse {

    private int processed;
    private int succeeded;
    private int failed;
    private List<BatchError> errors = new ArrayList<>();

    public BatchOperationResponse() {}

    public BatchOperationResponse(int processed, int succeeded, int failed, List<BatchError> errors) {
        this.processed = processed;
        this.succeeded = succeeded;
        this.failed = failed;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public int getProcessed() { return processed; }
    public void setProcessed(int processed) { this.processed = processed; }
    public int getSucceeded() { return succeeded; }
    public void setSucceeded(int succeeded) { this.succeeded = succeeded; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public List<BatchError> getErrors() { return errors; }
    public void setErrors(List<BatchError> errors) { this.errors = errors; }

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
