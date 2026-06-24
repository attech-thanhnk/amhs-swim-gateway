package vn.asg.converter.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser cho Field 18 (Other Information) của FPL.
 *
 * Field 18 chứa nhiều items phân cách bằng space, mỗi item có format: KEYWORD/VALUE
 * Ví dụ: PBN/A1B1C1D1O1S2 DOF/260507 REG/VN-A123 EET/VVTS0010 VVDN0105
 *
 * Các keywords phổ biến:
 * - PBN: Performance-Based Navigation capabilities
 * - DOF: Date of Flight (YYMMDD)
 * - REG: Registration marks
 * - EET: Estimated Elapsed Times
 * - SEL: SELCAL code
 * - OPR: Operator
 * - RMK: Remarks
 * - STS: Special handling reason
 * - DAT: Data applications
 * - COM: Communication capabilities
 * - NAV: Navigation capabilities
 * - DEP: Departure aerodrome/time (for AFIL)
 * - DEST: Destination aerodrome (for AFIL)
 * - ALTN: Alternate aerodrome(s)
 * - RALT: RNAV take-off alternate
 * - TALT: Take-off alternate
 */
public class Field18Parser {

    // Pattern để match keyword/value pairs
    // Supports: KEYWORD/VALUE hoặc KEYWORD/VALUE1 VALUE2 VALUE3 (cho EET, ALTN)
    // Fixed: Stop before next keyword pattern to avoid capturing "VN-A123 EET" as registration
    private static final Pattern ITEM_PATTERN = Pattern.compile("([A-Z]+)/(\\S+(?:\\s+(?![A-Z]{2,}/)\\S+)*)");

    /**
     * Parse Field 18 string thành map of keyword -> value.
     *
     * @param field18 Raw Field 18 string
     * @return Map chứa các keyword-value pairs
     */
    public Map<String, String> parse(String field18) {
        Map<String, String> items = new HashMap<>();

        if (field18 == null || field18.isBlank() || "0".equals(field18.trim())) {
            return items;
        }

        // Normalize: loại bỏ newlines, multiple spaces
        String normalized = field18.replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ").trim();

        Matcher matcher = ITEM_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String keyword = matcher.group(1);
            String value = matcher.group(2).trim();
            items.put(keyword, value);
        }

        return items;
    }

    /**
     * Parse DOF (Date of Flight) từ Field 18.
     *
     * @param items Map từ parse()
     * @return DOF string (YYMMDD) hoặc null
     */
    public String parseDof(Map<String, String> items) {
        return items.get("DOF");
    }

    /**
     * Parse REG (Registration) từ Field 18.
     *
     * @param items Map từ parse()
     * @return Registration string hoặc null
     */
    public String parseReg(Map<String, String> items) {
        return items.get("REG");
    }

    /**
     * Parse PBN (Performance-Based Navigation) từ Field 18.
     *
     * @param items Map từ parse()
     * @return PBN codes (e.g., "A1B1C1D1O1S2") hoặc null
     */
    public String parsePbn(Map<String, String> items) {
        return items.get("PBN");
    }

    /**
     * Parse EET (Estimated Elapsed Times) từ Field 18.
     * Format: EET/POINT1TIME1 POINT2TIME2 ...
     * Ví dụ: EET/VVTS0010 VVDN0105
     *
     * @param items Map từ parse()
     * @return EET string hoặc null
     */
    public String parseEet(Map<String, String> items) {
        return items.get("EET");
    }

    /**
     * Parse SEL (SELCAL) từ Field 18.
     *
     * @param items Map từ parse()
     * @return SELCAL code hoặc null
     */
    public String parseSel(Map<String, String> items) {
        return items.get("SEL");
    }

    /**
     * Parse OPR (Operator) từ Field 18.
     *
     * @param items Map từ parse()
     * @return Operator name hoặc null
     */
    public String parseOpr(Map<String, String> items) {
        return items.get("OPR");
    }

    /**
     * Parse STS (Special handling reason) từ Field 18.
     * Possible values: ALTRV, ATFMX, FFR, FLTCK, HAZMAT, HEAD, HOSP, HUM, MARSA, MEDEVAC, NONRVSM, SAR, STATE
     *
     * @param items Map từ parse()
     * @return STS code hoặc null
     */
    public String parseSts(Map<String, String> items) {
        return items.get("STS");
    }

    /**
     * Parse RMK (Remarks) từ Field 18.
     *
     * @param items Map từ parse()
     * @return Remarks text hoặc null
     */
    public String parseRmk(Map<String, String> items) {
        return items.get("RMK");
    }

    /**
     * Parse COM (Communication capabilities) từ Field 18.
     *
     * @param items Map từ parse()
     * @return COM capabilities hoặc null
     */
    public String parseCom(Map<String, String> items) {
        return items.get("COM");
    }

    /**
     * Parse NAV (Navigation capabilities) từ Field 18.
     *
     * @param items Map từ parse()
     * @return NAV capabilities hoặc null
     */
    public String parseNav(Map<String, String> items) {
        return items.get("NAV");
    }

    /**
     * Parse DAT (Data applications) từ Field 18.
     *
     * @param items Map từ parse()
     * @return DAT applications hoặc null
     */
    public String parseDat(Map<String, String> items) {
        return items.get("DAT");
    }

    /**
     * Parse ALTN (Alternate aerodromes) từ Field 18.
     * Note: Thường alternate nằm trong Field 16, nhưng có thể có thêm trong Field 18
     *
     * @param items Map từ parse()
     * @return ALTN aerodromes hoặc null
     */
    public String parseAltn(Map<String, String> items) {
        return items.get("ALTN");
    }
}
