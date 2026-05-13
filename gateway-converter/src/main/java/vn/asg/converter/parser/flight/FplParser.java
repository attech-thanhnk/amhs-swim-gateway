package vn.asg.converter.parser.flight;

import vn.asg.converter.model.flight.FplMessage;
import vn.asg.converter.parser.Field18Parser;
import vn.asg.converter.parser.MessageParser;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc dữ liệu bản tin FPL (Kế hoạch bay).
 */
public class FplParser implements MessageParser<FplMessage> {

    private final Field18Parser field18Parser = new Field18Parser();

    // Pattern nhận dạng loại bản tin trong ngoặc đơn (Mở rộng cho tất cả các loại ATS)
    private static final Pattern MSG_TYPE_PATTERN = Pattern.compile("\\((FPL|CHG|CNL|DEP|ARR|DLA|ARS|ARP|ALR|SPL|EST|CDN|ACP|RQP|RQS|CPL)-");

    // Pattern Field 13 / Field 16: ICAO code (4 chữ) + HHMM (4 số)
    private static final Pattern ICAO_TIME = Pattern.compile("^([A-Z]{4}|ZZZZ)(\\d{4})");

    // Pattern Field 15 tốc độ: N0450, K0800, M082
    private static final Pattern SPEED_LEVEL = Pattern
            .compile("^([NK]\\d{4}|M\\d{3})([FASMfasm]\\d{3,4})(?:\\s+(.*))?$");

    // Pattern ARR: (ARR-callsign-dep_icao-dest_icao+arrtime-other)
    private static final Pattern ARR_PATTERN = Pattern
            .compile("\\(ARR-([A-Z][A-Z0-9]{1,6})-([A-Z]{4})-([A-Z]{4})(\\d{4})-?(.*?)\\)");

    // Pattern DEP: (DEP-callsign-depicao+time-desticao-other)
    private static final Pattern DEP_PATTERN = Pattern
            .compile("\\(DEP-([A-Z][A-Z0-9]{1,6})-([A-Z]{4})(\\d{4})-([A-Z]{4})-?(.*?)\\)");

    // Pattern CNL: (CNL-callsign-depicao+eobt-desticao-other)
    private static final Pattern CNL_PATTERN = Pattern
            .compile("\\(CNL-([A-Z][A-Z0-9]{1,6})-([A-Z]{4})(\\d{4})-([A-Z]{4})-?(.*?)\\)");

    // Pattern DLA: (DLA-callsign-depicao+eobt-desticao-other)
    private static final Pattern DLA_PATTERN = Pattern
            .compile("\\(DLA-([A-Z][A-Z0-9]{1,6})-([A-Z]{4})(\\d{4})-([A-Z]{4})-?(.*?)\\)");

    public FplMessage parse(String body) throws Exception {
        if (body == null || body.isBlank()) {
            throw new Exception("Empty message body");
        }

        // Normalize: loại bỏ xuống dòng, thu gọn khoảng trắng
        String raw = body.replace("\r", "").replace("\n", " ").replaceAll("\\s+", " ").trim();

        // Xác định loại bản tin
        Matcher typeMatcher = MSG_TYPE_PATTERN.matcher(raw);
        if (!typeMatcher.find()) {
            throw new Exception(
                    "Cannot determine ATS message type from: " + raw.substring(0, Math.min(50, raw.length())));
        }
        String msgType = typeMatcher.group(1);

        // Delegate sang parser chuyên biệt theo loại
        FplMessage msg = switch (msgType) {
            case "FPL", "ALR", "RQP", "RQS" -> parseFpl(raw, msgType);
            case "CHG" -> parseChg(raw);
            case "CNL" -> parseCnl(raw);
            case "DEP" -> parseDep(raw);
            case "ARR" -> parseArr(raw);
            case "DLA" -> parseDla(raw);
            default -> throw new Exception("Unsupported message type: " + msgType);
        };

        msg.setOriginalTac(body);
        return msg;
    }

    // ─────────────────────────────────────────────
    // FPL / CHG — ICAO Doc 4444 Appendix 2
    // ─────────────────────────────────────────────
    private FplMessage parseFpl(String raw, String msgType) throws Exception {
        // Trích nội dung trong dấu ngoặc
        int start = raw.indexOf('(');
        int end = raw.lastIndexOf(')');
        if (start == -1 || end == -1 || end <= start) {
            throw new Exception("FPL message must be enclosed in parentheses");
        }
        String content = raw.substring(start + 1, end).trim();

        // Xử lý Field 18 có chứa ký tự '-' (như REG/VN-A123)
        String[] rawFields = content.split("-");
        
        // --- Xử lý đặc biệt cho ALR (Alerting Message) ---
        int offset = 0;
        String alertInfo = null;
        if ("ALR".equals(msgType) && rawFields.length > 2) {
            alertInfo = rawFields[1].trim();
            offset = 1; // Dịch chuyển 1 chỉ số cho các trường phía sau
        }

        String[] fields = mergeField18(rawFields, offset);

        FplMessage msg = new FplMessage();
        msg.setMessageType(msgType);
        if (alertInfo != null) {
            msg.setRemarks("Alert: " + alertInfo);
        }

        // Field 7: Aircraft identification (callsign)
        msg.setAircraftId(fields[1 + offset].trim());

        // Field 8: Flight rules + type
        String f8 = fields[2 + offset].trim();
        if (f8.length() >= 1) msg.setFlightRules(f8.substring(0, 1));
        if (f8.length() >= 2) msg.setFlightType(f8.substring(1, 2));

        // Field 9: Aircraft type + wake turbulence
        parseAircraftField(fields[3 + offset].trim(), msg);

        // Field 10: Equipment / SSR
        msg.setEquipment(fields[4 + offset].trim());

        // Field 13: Departure ICAO + EOBT
        parseDepartureField(fields[5 + offset].trim(), msg);

        // Field 15: Cruising speed + level + route
        parseRouteField(fields[6 + offset].trim(), msg);

        // Field 16: Destination + EET + Alternates
        if (fields.length > 7 + offset) {
            parseDestinationField(fields[7 + offset].trim(), msg);
        }

        // Field 18: Other information
        if (fields.length > 8 + offset && !fields[8 + offset].trim().isEmpty() && !fields[8 + offset].trim().equals("0")) {
            String f18Raw = fields[8 + offset].trim();
            msg.setOtherInfo(f18Raw);
            Map<String, String> f18Items = field18Parser.parse(f18Raw);
            msg.setDof(field18Parser.parseDof(f18Items));
            msg.setRegistration(field18Parser.parseReg(f18Items));
            msg.setPbn(field18Parser.parsePbn(f18Items));
            msg.setEet(field18Parser.parseEet(f18Items));
            msg.setSelcal(field18Parser.parseSel(f18Items));
            msg.setOperator(field18Parser.parseOpr(f18Items));
            msg.setSts(field18Parser.parseSts(f18Items));
            String rmk = field18Parser.parseRmk(f18Items);
            if (rmk != null && !rmk.equalsIgnoreCase("null")) {
                msg.setRemarks((msg.getRemarks() != null ? msg.getRemarks() + " " : "") + rmk);
            }
            msg.setNavCapabilities(field18Parser.parseNav(f18Items));
        }

        return msg;
    }

    /**
     * Parse bản tin CHG (Change flight plan).
     * Cấu trúc: (CHG-Callsign-DepICAO+EOBT-DestICAO-AmendedData)
     */
    private FplMessage parseChg(String raw) throws Exception {
        int start = raw.indexOf('(');
        int end = raw.lastIndexOf(')');
        if (start == -1 || end == -1) throw new Exception("Invalid CHG format");

        String content = raw.substring(start + 1, end).trim();
        String[] fields = content.split("-");

        if (fields.length < 5) {
            throw new Exception("CHG message too short (minimum 5 fields required)");
        }

        FplMessage msg = new FplMessage();
        msg.setMessageType("CHG");
        msg.setAircraftId(fields[1].trim());
        parseDepartureField(fields[2].trim(), msg);
        msg.setDestinationIcao(fields[3].trim());

        // Dữ liệu thay đổi nằm ở các field còn lại (từ index 4 trở đi)
        StringBuilder amended = new StringBuilder();
        for (int i = 4; i < fields.length; i++) {
            amended.append("-").append(fields[i]);
        }
        msg.setOtherInfo(amended.length() > 0 ? amended.substring(1) : "");

        return msg;
    }

    protected void parseAircraftField(String f9, FplMessage msg) {
        if (f9 == null || f9.isEmpty())
            return;

        // Tách số lượng (nếu có) và loại máy bay
        String typePart = f9;
        if (f9.contains("/")) {
            String[] parts = f9.split("/", 2);
            typePart = parts[0];
            if (parts.length > 1) {
                msg.setWakeTurbulence(parts[1].trim());
            }
        }

        // Kiểm tra có chỉ số đầu là số (số lượng máy bay)
        if (!typePart.isEmpty() && Character.isDigit(typePart.charAt(0))) {
            int i = 0;
            while (i < typePart.length() && Character.isDigit(typePart.charAt(i)))
                i++;
            msg.setNumberOfAircraft(typePart.substring(0, i));
            msg.setAircraftType(typePart.substring(i));
        } else {
            msg.setAircraftType(typePart);
        }
    }

    protected void parseDepartureField(String f13, FplMessage msg) {
        if (f13 == null || f13.isEmpty())
            return;
        Matcher m = ICAO_TIME.matcher(f13);
        if (m.find()) {
            msg.setDepartureIcao(m.group(1));
            msg.setEobt(m.group(2));
        } else if (f13.length() >= 4) {
            msg.setDepartureIcao(f13.substring(0, 4));
            if (f13.length() >= 8)
                msg.setEobt(f13.substring(4, 8));
        }
    }

    protected void parseRouteField(String f15, FplMessage msg) {
        if (f15 == null || f15.isEmpty())
            return;
        Matcher m = SPEED_LEVEL.matcher(f15);
        if (m.matches()) {
            msg.setCruisingSpeed(m.group(1));
            msg.setCruisingLevel(m.group(2));
            if (m.group(3) != null)
                msg.setRoute(m.group(3).trim());
        } else {
            // Fallback: cố tách tốc độ/mực theo khoảng trắng đầu tiên
            int spaceIdx = f15.indexOf(' ');
            if (spaceIdx > 0) {
                String sl = f15.substring(0, spaceIdx);
                // Speed thường ≥ 5 ký tự (N0450), Level ≥ 4 ký tự (F330)
                if (sl.length() >= 5) {
                    msg.setCruisingSpeed(sl.substring(0, 5));
                    msg.setCruisingLevel(sl.substring(5));
                }
                msg.setRoute(f15.substring(spaceIdx).trim());
            } else {
                msg.setRoute(f15);
            }
        }
    }

    protected void parseDestinationField(String f16, FplMessage msg) {
        if (f16 == null || f16.isEmpty())
            return;
        String[] parts = f16.trim().split("\\s+");
        if (parts.length > 0) {
            Matcher m = ICAO_TIME.matcher(parts[0]);
            if (m.find()) {
                msg.setDestinationIcao(m.group(1));
                msg.setTotalEet(m.group(2));
            } else if (parts[0].length() >= 4) {
                msg.setDestinationIcao(parts[0].substring(0, 4));
            }
        }
        if (parts.length > 1)
            msg.setAltDestination1(parts[1]);
        if (parts.length > 2)
            msg.setAltDestination2(parts[2]);
    }

    // ─────────────────────────────────────────────
    // DEP — ICAO Doc 4444 para 4.5.1
    // (DEP-callsign-depICAO+EOBT-destICAO-other)
    // ─────────────────────────────────────────────
    private FplMessage parseDep(String raw) throws Exception {
        Matcher m = DEP_PATTERN.matcher(raw);
        if (!m.find()) {
            throw new Exception("Cannot parse DEP message: " + raw.substring(0, Math.min(80, raw.length())));
        }
        FplMessage msg = new FplMessage();
        msg.setMessageType("DEP");
        msg.setAircraftId(m.group(1));
        msg.setDepartureIcao(m.group(2));
        msg.setActualDepartureTime(m.group(3));
        msg.setDestinationIcao(m.group(4));
        String other = m.group(5).trim();
        if (!other.isEmpty() && !other.equals("0")) {
            msg.setOtherInfo(other);
            Map<String, String> f18 = field18Parser.parse(other);
            msg.setDof(field18Parser.parseDof(f18));
        }
        return msg;
    }

    // ─────────────────────────────────────────────
    // ARR — ICAO Doc 4444 para 4.6.1
    // (ARR-callsign-depICAO-destICAO+arrTime-other)
    // ─────────────────────────────────────────────
    private FplMessage parseArr(String raw) throws Exception {
        Matcher m = ARR_PATTERN.matcher(raw);
        if (!m.find()) {
            throw new Exception("Cannot parse ARR message: " + raw.substring(0, Math.min(80, raw.length())));
        }
        FplMessage msg = new FplMessage();
        msg.setMessageType("ARR");
        msg.setAircraftId(m.group(1));
        msg.setDepartureIcao(m.group(2));
        msg.setDestinationIcao(m.group(3));
        msg.setActualArrivalTime(m.group(4));
        String other = m.group(5).trim();
        if (!other.isEmpty() && !other.equals("0")) {
            msg.setOtherInfo(other);
        }
        return msg;
    }

    // ─────────────────────────────────────────────
    // CNL — ICAO Doc 4444 para 4.4.1
    // (CNL-callsign-depICAO+EOBT-destICAO-other)
    // ─────────────────────────────────────────────
    private FplMessage parseCnl(String raw) throws Exception {
        Matcher m = CNL_PATTERN.matcher(raw);
        if (!m.find()) {
            throw new Exception("Cannot parse CNL message: " + raw.substring(0, Math.min(80, raw.length())));
        }
        FplMessage msg = new FplMessage();
        msg.setMessageType("CNL");
        msg.setAircraftId(m.group(1));
        msg.setDepartureIcao(m.group(2));
        msg.setEobt(m.group(3));
        msg.setDestinationIcao(m.group(4));
        String other = m.group(5).trim();
        if (!other.isEmpty() && !other.equals("0")) {
            msg.setOtherInfo(other);
        }
        return msg;
    }

    // ─────────────────────────────────────────────
    // DLA — ICAO Doc 4444 para 4.3.1
    // (DLA-callsign-depICAO+EOBT-destICAO-other)
    // ─────────────────────────────────────────────
    private FplMessage parseDla(String raw) throws Exception {
        Matcher m = DLA_PATTERN.matcher(raw);
        if (!m.find()) {
            throw new Exception("Cannot parse DLA message: " + raw.substring(0, Math.min(80, raw.length())));
        }
        FplMessage msg = new FplMessage();
        msg.setMessageType("DLA");
        msg.setAircraftId(m.group(1));
        msg.setDepartureIcao(m.group(2));
        msg.setEobt(m.group(3));
        msg.setDestinationIcao(m.group(4));
        String other = m.group(5).trim();
        if (!other.isEmpty() && !other.equals("0")) {
            msg.setOtherInfo(other);
        }
        return msg;
    }

    protected String[] mergeField18(String[] rawFields, int offset) {
        final int F18_INDEX = 8 + offset;

        if (rawFields.length <= F18_INDEX + 1) {
            // Đủ hoặc thiếu field — không cần merge
            return rawFields;
        }

        // Có thể có Field 19 (bắt đầu bằng "E/")
        boolean hasF19 = rawFields[rawFields.length - 1].trim().startsWith("E/");
        int f18EndIdx = hasF19 ? rawFields.length - 2 : rawFields.length - 1;

        // Merge từ F18_INDEX đến f18EndIdx thành một phần
        StringBuilder f18 = new StringBuilder(rawFields[F18_INDEX]);
        for (int i = F18_INDEX + 1; i <= f18EndIdx; i++) {
            f18.append("-").append(rawFields[i]);
        }

        String[] merged = new String[hasF19 ? F18_INDEX + 2 : F18_INDEX + 1];
        System.arraycopy(rawFields, 0, merged, 0, F18_INDEX);
        merged[F18_INDEX] = f18.toString();
        if (hasF19) {
            merged[F18_INDEX + 1] = rawFields[rawFields.length - 1];
        }
        return merged;
    }
}
