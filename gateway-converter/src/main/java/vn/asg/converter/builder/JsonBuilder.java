package vn.asg.converter.builder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import vn.asg.converter.model.BaseMessage;

/**
 * Chuyển đổi đối tượng Model sang định dạng JSON sử dụng thư viện Jackson.
 */
public class JsonBuilder {

    private final ObjectMapper mapper;

    public JsonBuilder() {
        this.mapper = new ObjectMapper();
        // Cấu hình Jackson
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // Bỏ qua trường null
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);               // Pretty print
        this.mapper.registerModule(new JodaModule());                        // Hỗ trợ Joda Time
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // ISO-8601
    }

    /**
     * Chuyển đổi một đối tượng BaseMessage bất kỳ sang JSON String.
     */
    public String build(BaseMessage message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException("Error building JSON from message model", e);
        }
    }
}
