package vn.asg.converter.parser.notam;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import vn.asg.converter.model.notam.NotamMessage;
import vn.asg.converter.parser.MessageParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc dữ liệu bản tin NOTAM.
 */
public class NotamParser implements MessageParser<NotamMessage> {

    // Header: (A0123/26 NOTAMN
    private static final Pattern HEADER_PATTERN =
        Pattern.compile("\\((?<id>[A-Z]\\d{4}/\\d{2})\\s+(?<type>NOTAM[NRC])");

    // Q) VVHM/QFAAH/IV/NBO/A/000/999/1059N10645E005
    // Tọa độ có thể vắng mặt trong một số NOTAM nội địa
    private static final Pattern Q_LINE_PATTERN =
        Pattern.compile("Q\\)\\s*(?<fir>[A-Z]{4})/(?<notamCode>[A-Z]{5})/(?<traffic>[A-Z]{1,2})/(?<purpose>[A-Z]{1,3})/(?<scope>[A-Z]{1,3})/(?<lower>\\d{3})/(?<upper>\\d{3})(?:/(?<coord>\\d{4}[NS]\\d{5}[EW]\\d{3}))?");

    // A) VVNB hoặc A) VVNB VVTS (nhiều sân bay)
    private static final Pattern A_LINE_PATTERN =
        Pattern.compile("A\\)\\s*(?<location>[A-Z]{4}(?:\\s+[A-Z]{4})*)");

    // B) 2601010000
    private static final Pattern B_LINE_PATTERN =
        Pattern.compile("B\\)\\s*(?<from>\\d{10})");

    // C) 2601312359 hoặc PERM hoặc UFN hoặc EST
    private static final Pattern C_LINE_PATTERN =
        Pattern.compile("C\\)\\s*(?<until>\\d{10}|PERM|UFN|EST)");

    // D) optional schedule
    private static final Pattern D_LINE_PATTERN =
        Pattern.compile("D\\)\\s*(?<schedule>.*?)(?=\\s*E\\))", Pattern.DOTALL);

    // E) text — FIX CHÍNH: lookahead mở rộng, không bắt buộc F)/G)
    // Thử 1: có F) hoặc G) sau E) text
    private static final Pattern E_LINE_WITH_FG =
        Pattern.compile("E\\)\\s*(?<text>.*?)(?=\\s*[FG]\\))", Pattern.DOTALL);

    // Thử 2: E) text đến cuối bản tin (trước ký tự đóng ngoặc cuối cùng)
    private static final Pattern E_LINE_TO_END =
        Pattern.compile("E\\)\\s*(?<text>.*?)\\s*\\)\\s*$", Pattern.DOTALL);

    // F)/G) limits (tùy chọn)
    private static final Pattern F_LINE_PATTERN =
        Pattern.compile("F\\)\\s*(?<lower>.*?)(?=\\s*G\\)|\\)\\s*$)", Pattern.DOTALL);
    private static final Pattern G_LINE_PATTERN =
        Pattern.compile("G\\)\\s*(?<upper>.*?)\\s*\\)\\s*$", Pattern.DOTALL);

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormat.forPattern("yyMMddHHmm").withZoneUTC();

    /**
     * Parse bản tin NOTAM đơn.
     */
    public NotamMessage parse(String body) throws Exception {
        if (body == null || body.isBlank()) {
            throw new Exception("Empty NOTAM body");
        }

        // Chuẩn hóa: giữ nguyên newlines cho parsing nhưng thu gọn khoảng trắng thừa
        String normalized = body.replace("\r", "")
                                .replaceAll("[ \\t]{2,}", " ")
                                .trim();

        // Cũng chuẩn bị phiên bản 1 dòng để parse các pattern cần liên tục
        String oneLiner = normalized.replace("\n", " ").replaceAll("\\s+", " ").trim();

        NotamMessage msg = new NotamMessage();

        // Parse Header
        Matcher m = HEADER_PATTERN.matcher(oneLiner);
        if (m.find()) {
            msg.setNotamId(m.group("id"));
            msg.setMessageType(m.group("type"));
        } else {
            throw new Exception("Missing or invalid NOTAM header in: " + oneLiner.substring(0, Math.min(60, oneLiner.length())));
        }

        // Parse Q-Line
        m = Q_LINE_PATTERN.matcher(oneLiner);
        if (m.find()) {
            msg.setFir(m.group("fir"));
            msg.setNotamCode(m.group("notamCode"));
            msg.setTraffic(m.group("traffic"));
            msg.setPurpose(m.group("purpose"));
            msg.setScope(m.group("scope"));
            msg.setLowerLimit(m.group("lower"));
            msg.setUpperLimit(m.group("upper"));
            // Tọa độ là tùy chọn
            try { msg.setCoordinates(m.group("coord")); } catch (IllegalArgumentException ignored) {}
        }
        // Q-line có thể vắng mặt trong một số NOTAM đặc biệt — không throw exception

        // Parse A-Line
        m = A_LINE_PATTERN.matcher(oneLiner);
        if (m.find()) {
            msg.setLocation(m.group("location").trim());
        }

        // Parse B-Line
        m = B_LINE_PATTERN.matcher(oneLiner);
        if (m.find()) {
            try {
                msg.setValidFrom(DateTime.parse(m.group("from"), DATE_FORMATTER));
            } catch (Exception e) {
                throw new Exception("Invalid B) date: " + m.group("from"));
            }
        }

        // Parse C-Line
        m = C_LINE_PATTERN.matcher(oneLiner);
        if (m.find()) {
            String until = m.group("until");
            if ("PERM".equals(until) || "UFN".equals(until) || "EST".equals(until)) {
                msg.setPermanent(true);
            } else {
                try {
                    msg.setValidUntil(DateTime.parse(until, DATE_FORMATTER));
                } catch (Exception e) {
                    throw new Exception("Invalid C) date: " + until);
                }
            }
        }

        // Parse D-Line (tùy chọn)
        m = D_LINE_PATTERN.matcher(oneLiner);
        if (m.find()) {
            msg.setSchedule(m.group("schedule").trim());
        }

        // Parse E-Line — FIX: thử với F)/G) trước, fallback về end-of-notam
        m = E_LINE_WITH_FG.matcher(oneLiner);
        if (m.find()) {
            msg.setText(m.group("text").trim());
        } else {
            // Fallback: lấy từ E) đến ký tự đóng ngoặc cuối cùng
            m = E_LINE_TO_END.matcher(oneLiner);
            if (m.find()) {
                msg.setText(m.group("text").trim());
            } else {
                throw new Exception("Missing mandatory E) text in NOTAM: "
                    + oneLiner.substring(0, Math.min(100, oneLiner.length())));
            }
        }

        // Parse F-Line (tùy chọn — giới hạn độ cao thấp)
        m = F_LINE_PATTERN.matcher(oneLiner);
        if (m.find()) {
            msg.setLowerHeight(m.group("lower").trim());
        }

        // Parse G-Line (tùy chọn — giới hạn độ cao cao)
        m = G_LINE_PATTERN.matcher(oneLiner);
        if (m.find()) {
            msg.setUpperHeight(m.group("upper").trim());
        }

        msg.setOriginalTac(body);
        return msg;
    }

    /**
     * Parse nhiều NOTAM trong một bản tin bulletin.
     * AFTN đôi khi gửi nhiều NOTAM liên tiếp trong cùng một bản tin.
     */
    public List<NotamMessage> parseAll(String body) throws Exception {
        List<NotamMessage> results = new ArrayList<>();
        if (body == null || body.isBlank()) return results;

        // Tách từng NOTAM riêng biệt bằng header pattern
        // Mỗi NOTAM bắt đầu bằng "(Xnnnn/nn NOTAM..."
        String normalized = body.replace("\r", "").trim();
        Pattern notamSplit = Pattern.compile("(?=\\([A-Z]\\d{4}/\\d{2}\\s+NOTAM[NRC])");
        String[] parts = notamSplit.split(normalized);

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            try {
                results.add(parse(part));
            } catch (Exception e) {
                // Log warning nhưng tiếp tục parse các NOTAM còn lại
                // trong môi trường production nên dùng Logger thay vì stderr
                System.err.println("[WARN] Failed to parse NOTAM segment: " + e.getMessage());
            }
        }

        if (results.isEmpty()) {
            throw new Exception("No valid NOTAM found in body");
        }
        return results;
    }
}
