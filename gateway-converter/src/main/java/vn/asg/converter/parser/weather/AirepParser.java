package vn.asg.converter.parser.weather;

import vn.asg.converter.model.weather.AirepMessage;
import vn.asg.converter.parser.MessageParser;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser cho bản tin AIREP (ARS/ARP).
 */
public class AirepParser implements MessageParser<AirepMessage> {

    // (ARS-VNA123-POS1234-...)
    private static final Pattern AIREP_PATTERN = 
        Pattern.compile("\\((ARS|ARP)-([A-Z0-9]{1,7})-([^-]+)-([^-]+)-([^-]+)");

    @Override
    public AirepMessage parse(String body) throws Exception {
        if (body == null || body.isBlank()) {
            throw new Exception("Empty AIREP body");
        }

        String raw = body.trim().replaceAll("\\s+", " ");
        AirepMessage msg = new AirepMessage();
        msg.setOriginalTac(body);

        Matcher m = AIREP_PATTERN.matcher(raw);
        if (m.find()) {
            msg.setMessageType(m.group(1));
            msg.setAircraftId(m.group(2));
            msg.setPosition(m.group(3));
            msg.setTime(m.group(4));
            msg.setLevel(m.group(5));
        }

        return msg;
    }
}
