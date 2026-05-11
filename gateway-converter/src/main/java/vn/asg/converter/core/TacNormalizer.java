package vn.asg.converter.core;

/**
 * Làm sạch ký tự rác từ bản tin AFTN/AMHS trước khi xử lý.
 * Học từ TacRaw.normalize() của project IWXXM cũ.
 */
public class TacNormalizer {

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "";

        String s = raw;
        s = s.replace("\u0007", "");   // BEL character (chuông AFTN)
        s = s.replace("\u0001", "");   // SOH
        s = s.replace("\u0003", "");   // ETX
        s = s.replace("\r\n", "\n");
        s = s.replace("\r", "\n");
        s = s.strip();

        // Thu gọn khoảng trắng trong từng dòng nhưng giữ nguyên xuống dòng
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.strip().replaceAll("[ \\t]{2,}", " "));
            sb.append("\n");
        }

        s = sb.toString().strip();
        s = s.replaceAll("\n{3,}", "\n\n");
        return s;
    }
}
