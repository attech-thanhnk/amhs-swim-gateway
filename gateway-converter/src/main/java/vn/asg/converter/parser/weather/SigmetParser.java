package vn.asg.converter.parser.weather;

import vn.asg.converter.model.weather.SigmetMessage;
import vn.asg.converter.parser.MessageParser;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser cho bản tin SIGMET.
 */
public class SigmetParser implements MessageParser<SigmetMessage> {

    // VVHM SIGMET A01 VALID 220600/221000 VVGL-
    private static final Pattern SIGMET_HEADER = 
        Pattern.compile("([A-Z]{4})\\s+SIGMET\\s+([A-Z0-9]{2,3})\\s+VALID\\s+(\\d{6}/\\d{6})\\s+([A-Z]{4})");

    @Override
    public SigmetMessage parse(String body) throws Exception {
        if (body == null || body.isBlank()) {
            throw new Exception("Empty SIGMET body");
        }

        String raw = body.trim().replaceAll("\\s+", " ");
        SigmetMessage msg = new SigmetMessage();
        msg.setMessageType("SIGMET");
        msg.setOriginalTac(body);

        Matcher m = SIGMET_HEADER.matcher(raw);
        if (m.find()) {
            msg.setFir(m.group(1));
            msg.setSequenceNumber(m.group(2));
            msg.setValidityPeriod(m.group(3));
            
            // Phần nội dung sau header
            String content = raw.substring(m.end()).trim();
            msg.setPhenomenon(content); // Tạm thời để cả cụm cho đến khi có logic chi tiết hơn
        }

        return msg;
    }
}
