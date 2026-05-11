package vn.asg.converter.core;

import java.util.regex.Pattern;

/**
 * Nhận diện loại bản tin từ body đã được bóc vỏ AFTN.
 *
 * Hỗ trợ đầy đủ các loại bản tin ATS theo ICAO Doc 4444 và Annex 15:
 *
 * ── IWXXM (Khí tượng) ──
 *   METAR / SPECI — Quan trắc sân bay (WMO No.49 / Annex 3)
 *   TAF           — Dự báo sân bay
 *   SIGMET        — Thời tiết nguy hiểm đáng kể
 *   AIRMET        — Thời tiết nguy hiểm tầng thấp
 *
 * ── FIXM (Quản lý bay) ──
 *   FPL — Kế hoạch bay (ICAO Doc 4444 Appendix 2)
 *   CHG — Sửa đổi kế hoạch bay (para 4.2)
 *   CNL — Hủy kế hoạch bay (para 4.4)
 *   DEP — Khởi hành thực tế (para 4.5)
 *   ARR — Hạ cánh thực tế (para 4.6)
 *   DLA — Trễ khởi hành (para 4.3)
 *   ARS — AIREP Special (báo cáo khí tượng đặc biệt của tàu bay)
 *   ARP — AIREP (báo cáo vị trí tàu bay)
 *
 * ── AIXM (Thông báo hàng không) ──
 *   NOTAM — Notice to Air Missions (ICAO Annex 15)
 *
 * QUY TẮC NHẬN DIỆN: Kiểm tra Bulletin TRƯỚC Single.
 */
public class MessageDetector {

    public enum Category {
        METAR, SPECI, TAF, SIGMET, AIRMET,  // Khí tượng → IWXXM
        FPL, CHG, CNL, DEP, ARR, DLA,        // Quản lý bay → FIXM
        ARS, ARP,                              // AIREP → FIXM
        NOTAM,                                 // Thông báo hàng không → AIXM
        UNKNOWN
    }

    public enum Form {
        SINGLE,    // Bản tin đơn: "METAR VVNB 221000Z..."
        BULLETIN   // Tập hợp: "SASA01 VVNB 221000\nMETAR VVNB...\nMETAR VVTS..."
    }

    public record DetectionResult(Category category, Form form) {}

    // ── Bulletin patterns (WMO AHL header: TTAAii CCCC YYGGgg) ──
    private static final Pattern METAR_BUL  = Pattern.compile("(?m)^SA[A-Z]{2}\\d{2}\\s+[A-Z]{4}\\s+\\d{6}");
    private static final Pattern SPECI_BUL  = Pattern.compile("(?m)^SP[A-Z]{2}\\d{2}\\s+[A-Z]{4}\\s+\\d{6}");
    private static final Pattern TAF_BUL    = Pattern.compile("(?m)^F[TC][A-Z]{2}\\d{2}\\s+[A-Z]{4}\\s+\\d{6}");
    private static final Pattern SIGMET_BUL = Pattern.compile("(?m)^W[SCV][A-Z]{2}\\d{2}\\s+[A-Z]{4}\\s+\\d{6}");
    private static final Pattern AIRMET_BUL = Pattern.compile("(?m)^WA[A-Z]{2}\\d{2}\\s+[A-Z]{4}\\s+\\d{6}");

    // ── Single message patterns ──
    private static final Pattern METAR_SGL  = Pattern.compile("(?m)^METAR\\s+");
    private static final Pattern SPECI_SGL  = Pattern.compile("(?m)^SPECI\\s+");
    private static final Pattern TAF_SGL    = Pattern.compile("(?m)^TAF\\s+");
    // SIGMET: "VVHM SIGMET A01 VALID..."
    private static final Pattern SIGMET_SGL = Pattern.compile("[A-Z]{4}\\s+SIGMET\\s+");
    // AIRMET: "VVHM AIRMET 1 VALID..."
    private static final Pattern AIRMET_SGL = Pattern.compile("[A-Z]{4}\\s+AIRMET\\s+");

    // ── NOTAM — ICAO Annex 15 ──
    // "(A0123/24 NOTAMN" hoặc "(B0001/24 NOTAMR" (series A-Z)
    private static final Pattern NOTAM_PAT  = Pattern.compile("\\([A-Z]\\d{4}/\\d{2}\\s+NOTAM[NRC]");

    // ── FPL và các bản tin quản lý bay — ICAO Doc 4444 ──
    private static final Pattern FPL_PAT    = Pattern.compile("\\(FPL-");
    private static final Pattern CHG_PAT    = Pattern.compile("\\(CHG-");
    private static final Pattern CNL_PAT    = Pattern.compile("\\(CNL-");
    private static final Pattern DEP_PAT    = Pattern.compile("\\(DEP-");
    private static final Pattern ARR_PAT    = Pattern.compile("\\(ARR-");
    private static final Pattern DLA_PAT    = Pattern.compile("\\(DLA-");
    // AIREP Special và AIREP thường
    private static final Pattern ARS_PAT    = Pattern.compile("\\(ARS-");
    private static final Pattern ARP_PAT    = Pattern.compile("\\(ARP-");

    public static DetectionResult detect(String body) {
        if (body == null || body.isBlank())
            return new DetectionResult(Category.UNKNOWN, Form.SINGLE);

        // ── Kiểm tra Bulletin TRƯỚC Single ──
        if (METAR_BUL.matcher(body).find())   return new DetectionResult(Category.METAR,  Form.BULLETIN);
        if (SPECI_BUL.matcher(body).find())   return new DetectionResult(Category.SPECI,  Form.BULLETIN);
        if (TAF_BUL.matcher(body).find())     return new DetectionResult(Category.TAF,    Form.BULLETIN);
        if (SIGMET_BUL.matcher(body).find())  return new DetectionResult(Category.SIGMET, Form.BULLETIN);
        if (AIRMET_BUL.matcher(body).find())  return new DetectionResult(Category.AIRMET, Form.BULLETIN);

        // ── Single message ──
        if (METAR_SGL.matcher(body).find())   return new DetectionResult(Category.METAR,  Form.SINGLE);
        if (SPECI_SGL.matcher(body).find())   return new DetectionResult(Category.SPECI,  Form.SINGLE);
        if (TAF_SGL.matcher(body).find())     return new DetectionResult(Category.TAF,    Form.SINGLE);
        if (SIGMET_SGL.matcher(body).find())  return new DetectionResult(Category.SIGMET, Form.SINGLE);
        if (AIRMET_SGL.matcher(body).find())  return new DetectionResult(Category.AIRMET, Form.SINGLE);

        // ── NOTAM ──
        if (NOTAM_PAT.matcher(body).find())   return new DetectionResult(Category.NOTAM,  Form.SINGLE);

        // ── Quản lý bay — FPL trước vì chi tiết nhất ──
        if (FPL_PAT.matcher(body).find())     return new DetectionResult(Category.FPL,    Form.SINGLE);
        if (CHG_PAT.matcher(body).find())     return new DetectionResult(Category.CHG,    Form.SINGLE);
        if (CNL_PAT.matcher(body).find())     return new DetectionResult(Category.CNL,    Form.SINGLE);
        if (DEP_PAT.matcher(body).find())     return new DetectionResult(Category.DEP,    Form.SINGLE);
        if (ARR_PAT.matcher(body).find())     return new DetectionResult(Category.ARR,    Form.SINGLE);
        if (DLA_PAT.matcher(body).find())     return new DetectionResult(Category.DLA,    Form.SINGLE);
        if (ARS_PAT.matcher(body).find())     return new DetectionResult(Category.ARS,    Form.SINGLE);
        if (ARP_PAT.matcher(body).find())     return new DetectionResult(Category.ARP,    Form.SINGLE);

        return new DetectionResult(Category.UNKNOWN, Form.SINGLE);
    }
}
