package vn.asg.converter.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bóc vỏ AFTN/AMHS để lấy body bản tin thực sự.
 *
 * Cấu trúc bản tin AFTN đầy đủ:
 * ZCZC SAP01 ← AFTN header
 * FF VVNBYOYX VVVVZZZZ ← Priority + Địa chỉ nhận
 * 221000 VVNBYOYX ← Filing time + Originator
 * METAR VVNB 221000Z... ← BODY (phần cần lấy)
 * NNNN ← AFTN footer
 */
public class TacPreprocessor {

    // ZCZC [header]: bắt đầu bản tin AFTN
    private static final Pattern HEAD_RX = Pattern.compile("ZCZC\\s*.*");

    // Priority + Địa chỉ: "FF VVNBYOYX VVVVZZZZ"
    // Priority codes: SS=Safety, DD=Distress, FF=Flood, GG=General, KK=Known
    private static final Pattern ADDRESS_RX = Pattern
            .compile("(?<priority>SS|DD|FF|GG|KK)\\s+(?<address>(?:[A-Z]{8}\\s*)+)");

    // Filing time + Originator: "221000 VVNBYOYX"
    private static final Pattern ORIGIN_RX = Pattern.compile("(?m)^(?<filingTime>\\d{6})\\s+(?<origin>[A-Z]{8}).*");

    // NNNN: kết thúc bản tin AFTN
    private static final Pattern ENDING_RX = Pattern.compile("NNNN.*", Pattern.DOTALL);

    public static class AftnEnvelope {
        public boolean hasAftnWrapper;
        public String priority; // "FF", "GG"... hoặc null
        public String addressLine; // "VVNBYOYX VVVVZZZZ"
        public String filingTime; // "221000"
        public String originator; // "VVNBYOYX"
        public String body; // "METAR VVNB 221000Z..."
    }

    public AftnEnvelope unwrap(String rawMessage) {
        AftnEnvelope env = new AftnEnvelope();
        StringBuffer buf = new StringBuffer(TacNormalizer.normalize(rawMessage));

        // Lớp 1: ZCZC header
        Matcher m = HEAD_RX.matcher(buf);
        if (m.find()) {
            env.hasAftnWrapper = true;
            buf.delete(m.start(), m.end());
            refresh(buf);
        }

        // Lớp 2: Priority + Address
        m = ADDRESS_RX.matcher(buf);
        if (m.find()) {
            env.priority = m.group("priority");
            env.addressLine = m.group("address").strip();
            buf.delete(m.start(), m.end());
            refresh(buf);
        }

        // Lớp 3: Filing time + Originator
        m = ORIGIN_RX.matcher(buf);
        if (m.find()) {
            env.filingTime = m.group("filingTime");
            env.originator = m.group("origin");
            buf.delete(m.start(), m.end());
            refresh(buf);
        }

        // Lớp 4: NNNN footer
        m = ENDING_RX.matcher(buf);
        if (m.find()) {
            buf.delete(m.start(), buf.length());
        }

        env.body = TacNormalizer.normalize(buf.toString());
        return env;
    }

    private void refresh(StringBuffer buf) {
        String normalized = TacNormalizer.normalize(buf.toString());
        buf.setLength(0);
        buf.append(normalized);
    }
}
