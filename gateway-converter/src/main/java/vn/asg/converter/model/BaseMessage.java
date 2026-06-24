package vn.asg.converter.model;

import java.util.List;

/**
 * Chứa các thông tin Metadata chuẩn theo EUR Doc 047 và ICAO.
 */
public abstract class BaseMessage {

    private String messageId; // Định danh duy nhất cho bản tin
    private String messageType; // FPL, METAR, NOTAM, CHG...
    private String priority; // SS, DD, FF, GG, KK
    private String originator; // Địa chỉ người gửi (8 ký tự)
    private List<String> recipients; // Danh sách địa chỉ người nhận
    private String filingTime; // YYGGgg
    private String originalTac; // Nội dung điện văn thô gốc
    private long timestamp; // Thời điểm xử lý (epoch ms)

    public BaseMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getOriginator() {
        return originator;
    }

    public void setOriginator(String originator) {
        this.originator = originator;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public String getFilingTime() {
        return filingTime;
    }

    public void setFilingTime(String filingTime) {
        this.filingTime = filingTime;
    }

    public String getOriginalTac() {
        return originalTac;
    }

    public void setOriginalTac(String originalTac) {
        this.originalTac = originalTac;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Kiểm tra tính hợp lệ của các trường bắt buộc trước khi render sang TEXT.
     * @throws Exception nếu thiếu thông tin quan trọng.
     */
    public abstract void validate() throws Exception;
}
