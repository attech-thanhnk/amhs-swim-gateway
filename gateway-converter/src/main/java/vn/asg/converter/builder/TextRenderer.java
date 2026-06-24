package vn.asg.converter.builder;

import vn.asg.converter.model.BaseMessage;

/**
 * Bộ hiển thị dạng văn bản thô (TAC).
 */
public class TextRenderer {

    /**
     * Trả về nội dung điện văn gốc từ model.
     */
    public String render(BaseMessage message) {
        if (message == null) return "";
        
        // If it already has original TAC (e.g. from AFTN to JSON flow), use it
        if (message.getOriginalTac() != null && !message.getOriginalTac().isEmpty()) {
            return message.getOriginalTac();
        }

        // Otherwise, render from fields (JSON to TAC flow) using model's toString implementation
        try {
            message.validate();
            return message.toString();
        } catch (Exception e) {
            throw new RuntimeException("Validation failed: " + e.getMessage(), e);
        }
    }
}
