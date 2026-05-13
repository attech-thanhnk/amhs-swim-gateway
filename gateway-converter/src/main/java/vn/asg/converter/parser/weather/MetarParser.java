package vn.asg.converter.parser.weather;

import vn.asg.converter.model.weather.MetarMessage;
import vn.asg.converter.parser.MessageParser;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc dữ liệu bản tin METAR/SPECI.
 */
public class MetarParser implements MessageParser<MetarMessage> {

    private static final Pattern METAR_PATTERN = 
        Pattern.compile("^(METAR|SPECI)?\\s*([A-Z]{4})\\s+(\\d{6}Z)");

    @Override
    public MetarMessage parse(String body) throws Exception {
        if (body == null || body.isBlank()) {
            throw new Exception("Empty METAR body");
        }

        String raw = body.trim().replaceAll("\\s+", " ");
        MetarMessage msg = new MetarMessage();
        msg.setOriginalTac(body);

        Matcher m = METAR_PATTERN.matcher(raw);
        if (m.find()) {
            msg.setMessageType(m.group(1) != null ? m.group(1) : "METAR");
            msg.setStationIcao(m.group(2));
            msg.setObservationTime(m.group(3));
        } else {
            // Fallback nếu không có prefix METAR
            String[] parts = raw.split(" ");
            if (parts.length >= 2 && parts[0].length() == 4) {
                msg.setStationIcao(parts[0]);
                msg.setObservationTime(parts[1]);
                msg.setMessageType("METAR");
            }
        }

        // Parse NIL
        if (raw.contains(" NIL")) {
            msg.setNil(true);
        }

        // Logic parse chi tiết các trường khác (Wind, Vis, Cloud...) sẽ được bổ sung sau
        // Hiện tại tập trung vào cấu trúc Unified
        
        return msg;
    }
}
