package vn.asg.converter.parser.flight;

import vn.asg.converter.model.flight.FplMessage;
import vn.asg.converter.parser.MessageParser;

/**
 * Parser chuyên biệt cho các bản tin Phối hợp (EST, CDN, ACP, CPL).
 */
public class CoordinationParser implements MessageParser<FplMessage> {

    @Override
    public FplMessage parse(String body) throws Exception {
        if (body == null || body.isBlank()) throw new Exception("Empty body");

        String raw = body.replace("\r", "").replace("\n", " ").replaceAll("\\s+", " ").trim();
        int start = raw.indexOf('(');
        int end = raw.lastIndexOf(')');
        if (start == -1 || end == -1) throw new Exception("Invalid format");

        String content = raw.substring(start + 1, end).trim();
        String[] fields = content.split("-");
        String type = fields[0].toUpperCase();

        FplMessage msg = new FplMessage();
        msg.setMessageType(type);
        
        if (fields.length > 1) msg.setAircraftId(fields[1].trim());

        if ("EST".equals(type)) {
            // (EST-F7-F13-F14-F16)
            if (fields.length > 2) parseIcaoTime(fields[2].trim(), msg, true);
            if (fields.length > 3) msg.setRemarks("Boundary: " + fields[3].trim());
            if (fields.length > 4) parseIcaoTime(fields[4].trim(), msg, false);
        } else {
            // CDN, ACP, CPL: (TYPE-F7-F13-F16-...)
            if (fields.length > 2) parseIcaoTime(fields[2].trim(), msg, true);
            if (fields.length > 3) parseIcaoTime(fields[3].trim(), msg, false);
        }

        msg.setOriginalTac(body);
        return msg;
    }

    private void parseIcaoTime(String field, FplMessage msg, boolean isDep) {
        if (field.length() >= 4) {
            String icao = field.substring(0, 4);
            if (isDep) msg.setDepartureIcao(icao);
            else msg.setDestinationIcao(icao);
        }
    }
}
