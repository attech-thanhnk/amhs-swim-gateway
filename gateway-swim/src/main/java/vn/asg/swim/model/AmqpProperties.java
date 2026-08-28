package vn.asg.swim.model;

/**
 * EUR Doc 047 AMQP Application Properties
 *
 * Model for AMQP application properties theo §4.4.3.4 (AMHS→SWIM)
 * và §4.5.2 (SWIM→AMHS)
 */
public class AmqpProperties {

    /**
     * OID mặc định do EMA đăng ký cho abstract-value "unknown-attachment"
     * (§4.4.4.5b / §4.5.2.13b). registered-identifier mang giá trị khác OID này thì
     * user-visible-string bắt buộc phải có kèm (§4.4.4.6 / §4.5.2.14).
     */
    public static final String DEFAULT_REGISTERED_IDENTIFIER_OID = "2.16.840.1.113694.2.2.1.1";

    // §4.4.3.4.1 / §4.5.2.1
    private String amhsIpmId; // IPM Identifier

    // §4.4.3.4.2
    private String amhsFtbpFileName; // File Transfer Body Part file name
    private Long amhsFtbpObjectSize; // FTBP object size
    private String amhsFtbpLastMod; // FTBP last modification time

    // §4.4.3.4.3 / §4.5.2.1
    private String amhsAtsPri; // ATS priority (SS, FF, GG, KK)

    // §4.4.3.4.4 / §4.5.2.4
    private String amhsRecipients; // Space-separated AFTN addresses

    // §4.4.3.4.5 / §4.5.2.1
    private String amhsAtsFt; // Filing time (DDhhmm format)

    // §4.4.3.4.6 / §4.5.2.1
    private String amhsAtsOhi; // Originator's reference / OHI

    // §4.4.3.4.7 / §4.5.2.3
    private String amhsOriginator; // AFTN address originator

    // §4.4.3.4.8
    private String amhsSubject; // IPM Subject heading

    // §4.4.3.4.9
    private String amhsBodypartType; // ia5-text / ia5-text-body-part / general-text-body-part /
                                     // file-transfer-body-part
    private String amhsContentEncoding; // IA5 / ISO-646 / ISO-8859-1

    // §4.4.3.4.10
    private String amhsMessageSigned; // signed / unsigned / invalid-signature

    // §4.4.3.4.11
    private String swimCompression; // gzip (optional)

    // §4.5.2.1 - Additional SWIM→AMHS properties
    private String amhsRegisteredIdentifier; // FTBP OID
    private String amhsUserVisibleString; // FTBP user visible string

    public AmqpProperties() {
    }

    public String getAmhsIpmId() {
        return amhsIpmId;
    }

    public void setAmhsIpmId(String amhsIpmId) {
        this.amhsIpmId = amhsIpmId;
    }

    public String getAmhsFtbpFileName() {
        return amhsFtbpFileName;
    }

    public void setAmhsFtbpFileName(String amhsFtbpFileName) {
        this.amhsFtbpFileName = amhsFtbpFileName;
    }

    public Long getAmhsFtbpObjectSize() {
        return amhsFtbpObjectSize;
    }

    public void setAmhsFtbpObjectSize(Long amhsFtbpObjectSize) {
        this.amhsFtbpObjectSize = amhsFtbpObjectSize;
    }

    public String getAmhsFtbpLastMod() {
        return amhsFtbpLastMod;
    }

    public void setAmhsFtbpLastMod(String amhsFtbpLastMod) {
        this.amhsFtbpLastMod = amhsFtbpLastMod;
    }

    public String getAmhsAtsPri() {
        return amhsAtsPri;
    }

    public void setAmhsAtsPri(String amhsAtsPri) {
        this.amhsAtsPri = amhsAtsPri;
    }

    public String getAmhsRecipients() {
        return amhsRecipients;
    }

    public void setAmhsRecipients(String amhsRecipients) {
        this.amhsRecipients = amhsRecipients;
    }

    public String getAmhsAtsFt() {
        return amhsAtsFt;
    }

    public void setAmhsAtsFt(String amhsAtsFt) {
        this.amhsAtsFt = amhsAtsFt;
    }

    public String getAmhsAtsOhi() {
        return amhsAtsOhi;
    }

    public void setAmhsAtsOhi(String amhsAtsOhi) {
        this.amhsAtsOhi = amhsAtsOhi;
    }

    public String getAmhsOriginator() {
        return amhsOriginator;
    }

    public void setAmhsOriginator(String amhsOriginator) {
        this.amhsOriginator = amhsOriginator;
    }

    public String getAmhsSubject() {
        return amhsSubject;
    }

    public void setAmhsSubject(String amhsSubject) {
        this.amhsSubject = amhsSubject;
    }

    public String getAmhsBodypartType() {
        return amhsBodypartType;
    }

    public void setAmhsBodypartType(String amhsBodypartType) {
        this.amhsBodypartType = amhsBodypartType;
    }

    public String getAmhsContentEncoding() {
        return amhsContentEncoding;
    }

    public void setAmhsContentEncoding(String amhsContentEncoding) {
        this.amhsContentEncoding = amhsContentEncoding;
    }

    public String getAmhsMessageSigned() {
        return amhsMessageSigned;
    }

    public void setAmhsMessageSigned(String amhsMessageSigned) {
        this.amhsMessageSigned = amhsMessageSigned;
    }

    public String getSwimCompression() {
        return swimCompression;
    }

    public void setSwimCompression(String swimCompression) {
        this.swimCompression = swimCompression;
    }

    public String getAmhsRegisteredIdentifier() {
        return amhsRegisteredIdentifier;
    }

    public void setAmhsRegisteredIdentifier(String amhsRegisteredIdentifier) {
        this.amhsRegisteredIdentifier = amhsRegisteredIdentifier;
    }

    public String getAmhsUserVisibleString() {
        return amhsUserVisibleString;
    }

    public void setAmhsUserVisibleString(String amhsUserVisibleString) {
        this.amhsUserVisibleString = amhsUserVisibleString;
    }

    /**
     * EUR Doc 047 v3.0 §4.5.2.2 Table 9 (Mapping of AMQP priority): AMQP priority
     * (>=6/5/4/3/<=2) -> ATS-message-priority (SS/DD/FF/GG/KK).
     */
    public static String mapPriorityToAmhs(int priority) {
        if (priority < 0 || priority > 9) {
            throw new IllegalArgumentException("Invalid AMQP priority: " + priority);
        }
        if (priority >= 6) return "SS";
        return switch (priority) {
            case 5 -> "DD";
            case 4 -> "FF";
            case 3 -> "GG";
            default -> "KK"; // <= 2
        };
    }

    public static String mapPriorityToAts(int priority) {
        return mapPriorityToAmhs(priority);
    }

    /**
     * EUR Doc 047 v3.0 §4.4.3.2.2 Table 3 (ATS Priority to AMQP Priority conversion):
     * SS=6, DD=5, FF=4, GG=3, KK=2. Default AMQP priority per the same section's note is 4.
     */
    public static int mapAtsPriorityToAmqp(String atsPri) {
        if (atsPri == null)
            return 4;
        return switch (atsPri.toUpperCase().trim()) {
            case "SS" -> 6;
            case "DD" -> 5;
            case "FF" -> 4;
            case "GG" -> 3;
            case "KK" -> 2;
            default -> 4;
        };
    }

    /**
     * EUR Doc 047 v3.0 Table 5 (Amhs_ats_pri ATS-message-priority and IPM precedence
     * equivalency): precedence của Extended IPM ↔ ATS-message-priority.
     * <p>
     * 107 = SS, 71 = DD, 57 = FF, 28 = GG, 14 = KK. Giá trị precedence nằm ngoài danh sách
     * trả về null để caller quyết định (thường là giữ nguyên ATS-message-priority của
     * Basic IPM thay vì suy diễn sai).
     */
    public static String mapPrecedenceToAtsPriority(Integer precedence) {
        if (precedence == null) {
            return null;
        }
        return switch (precedence) {
            case 107 -> "SS";
            case 71 -> "DD";
            case 57 -> "FF";
            case 28 -> "GG";
            case 14 -> "KK";
            default -> null;
        };
    }

    /**
     * EUR Doc 047 §4.4.4.4 / Appendix A CTSW020: precedence 107 tương đương ưu tiên SS và
     * phải được log + báo Control Position khi recipient có responsibility "responsible".
     */
    public static final int PRECEDENCE_SS = 107;

    /**
     * Kiểm tra registered-identifier có phải OID mặc định "unknown-attachment" hay không.
     * Chấp nhận cả dạng có ngoặc nhọn {2.16.840...} lẫn không.
     */
    public static boolean isDefaultRegisteredIdentifier(String oid) {
        if (oid == null) {
            return false;
        }
        return DEFAULT_REGISTERED_IDENTIFIER_OID.equals(oid.replaceAll("[{}\\s]", ""));
    }
}
