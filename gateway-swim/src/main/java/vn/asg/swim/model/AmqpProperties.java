package vn.asg.swim.model;

/**
 * Model for AMQP application properties (AMHS↔SWIM).
 */
public class AmqpProperties {

    /**
     * OID mặc định cho abstract-value "unknown-attachment".
     */
    public static final String DEFAULT_REGISTERED_IDENTIFIER_OID = "2.16.840.1.113694.2.2.1.1";

    private String amhsIpmId; // IPM Identifier

    private String amhsFtbpFileName; // File Transfer Body Part file name
    private Long amhsFtbpObjectSize; // FTBP object size
    private String amhsFtbpLastMod; // FTBP last modification time

    private String amhsAtsPri; // ATS priority (SS, FF, GG, KK)

    private String amhsRecipients; // Space-separated AFTN addresses

    private String amhsAtsFt; // Filing time (DDhhmm format)

    private String amhsAtsOhi; // Originator's reference / OHI

    private String amhsOriginator; // AFTN address originator

    private String amhsSubject; // IPM Subject heading

    private String amhsBodypartType; // ia5-text / ia5-text-body-part / general-text-body-part / file-transfer-body-part
    private String amhsContentEncoding; // IA5 / ISO-646 / ISO-8859-1

    private String amhsMessageSigned; // signed / unsigned / invalid-signature

    private String swimCompression; // gzip (optional)

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
     * Ánh xạ AMQP priority sang ATS priority (SS/DD/FF/GG/KK).
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

    /**
     * Ánh xạ ATS priority sang AMQP priority (SS=6, DD=5, FF=4, GG=3, KK=2; mặc định 4).
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
     * Ánh xạ precedence của Extended IPM sang ATS priority:
     * 107 = SS, 71 = DD, 57 = FF, 28 = GG, 14 = KK.
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
     * Mức precedence tương đương ưu tiên SS (107).
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
