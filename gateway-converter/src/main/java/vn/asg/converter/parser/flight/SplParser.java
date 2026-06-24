package vn.asg.converter.parser.flight;

import vn.asg.converter.model.flight.FplMessage;
import vn.asg.converter.parser.MessageParser;

/**
 * Parser chuyên biệt cho bản tin Bổ sung kế hoạch bay (SPL).
 * Tuân thủ ICAO Doc 4444: (SPL-F7-F13-F19)
 */
public class SplParser implements MessageParser<FplMessage> {

    @Override
    public FplMessage parse(String body) throws Exception {
        if (body == null || body.isBlank()) throw new Exception("Empty body");

        String raw = body.replace("\r", "").replace("\n", " ").replaceAll("\\s+", " ").trim();
        int start = raw.indexOf('(');
        int end = raw.lastIndexOf(')');
        if (start == -1 || end == -1) throw new Exception("Invalid SPL format");

        String content = raw.substring(start + 1, end).trim();
        String[] fields = content.split("-");

        if (fields.length < 4) {
            throw new Exception("SPL message too short (expected F7, F13, F19)");
        }

        FplMessage msg = new FplMessage();
        msg.setMessageType("SPL");
        msg.setAircraftId(fields[1].trim());
        
        // F13
        String f13 = fields[2].trim();
        if (f13.length() >= 4) msg.setDepartureIcao(f13.substring(0, 4));
        if (f13.length() >= 8) msg.setEobt(f13.substring(4, 8));

        // F19 (Supplementary Info)
        StringBuilder f19 = new StringBuilder();
        for (int i = 3; i < fields.length; i++) {
            f19.append("-").append(fields[i]);
        }
        msg.setOtherInfo(f19.length() > 0 ? f19.substring(1) : "");
        msg.setOriginalTac(body);
        
        return msg;
    }
}
