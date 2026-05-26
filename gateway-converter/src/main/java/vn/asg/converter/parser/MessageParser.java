package vn.asg.converter.parser;

import vn.asg.converter.model.BaseMessage;

/**
 * Interface chung cho các bộ phân tích điện văn.
 * @param <T> Kiểu bản tin trả về (kế thừa từ BaseMessage)
 */
public interface MessageParser<T extends BaseMessage> {
    
    /**
     * Phân tích nội dung điện văn thô sang đối tượng Model.
     * @param body Thân bản tin (đã bóc vỏ AFTN)
     * @return Đối tượng model chứa dữ liệu đã bóc tách
     */
    T parse(String body) throws Exception;
}
