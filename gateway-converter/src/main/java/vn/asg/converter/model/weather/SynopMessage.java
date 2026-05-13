package vn.asg.converter.model.weather;

import vn.asg.converter.model.BaseMessage;

/**
 * Model cho bản tin SYNOP (Báo cáo khí tượng bề mặt).
 */
public class SynopMessage extends BaseMessage {
    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    @Override
    public String toString() {
        if (getOriginalTac() != null && !getOriginalTac().isEmpty()) return getOriginalTac();
        if (content == null) return "";
        return content.trim() + " =";
    }

    @Override
    public void validate() throws Exception {
        if (content == null || content.isEmpty()) throw new Exception("Empty SYNOP content");
    }
}
