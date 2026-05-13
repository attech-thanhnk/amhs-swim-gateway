package vn.asg.swim.model;

/**
 * EUR Doc 047 AMQP Application Properties
 *
 * Model for AMQP application properties theo §4.4.3.4 (AMHS→SWIM)
 * và §4.5.2 (SWIM→AMHS)
 */
public class AmqpProperties {

    // §4.4.3.4.1 / §4.5.2.1
    private String amhsIpmId;           // IPM Identifier

    // §4.4.3.4.2
    private String amhsFtbpFileName;    // File Transfer Body Part file name
    private Long amhsFtbpObjectSize;    // FTBP object size
    private String amhsFtbpLastMod;     // FTBP last modification time

    // §4.4.3.4.3 / §4.5.2.1
    private String amhsAtsPri;          // ATS priority (SS, FF, GG, KK)

    // §4.4.3.4.4 / §4.5.2.4
    private String amhsRecipients;      // Space-separated AFTN addresses

    // §4.4.3.4.5 / §4.5.2.1
    private String amhsAtsFt;           // Filing time (DDhhmm format)

    // §4.4.3.4.6 / §4.5.2.1
    private String amhsAtsOhi;          // Originator's reference / OHI

    // §4.4.3.4.7 / §4.5.2.3
    private String amhsOriginator;      // AFTN address originator

    // §4.4.3.4.8
    private String amhsSubject;         // IPM Subject heading

    // §4.4.3.4.9
    private String amhsBodypartType;    // ia5-text / ia5-text-body-part / general-text-body-part / file-transfer-body-part
    private String amhsContentEncoding; // IA5 / ISO-646 / ISO-8859-1

    // §4.4.3.4.10
    private String amhsMessageSigned;   // signed / unsigned / invalid-signature

    // §4.4.3.4.11
    private String swimCompression;     // gzip (optional)

    // §4.5.2.1 - Additional SWIM→AMHS properties
    private String amhsRegisteredIdentifier;  // FTBP OID
    private String amhsUserVisibleString;     // FTBP user visible string

    public AmqpProperties() {}

    public String getAmhsIpmId() { return amhsIpmId; }
    public void setAmhsIpmId(String amhsIpmId) { this.amhsIpmId = amhsIpmId; }
    public String getAmhsFtbpFileName() { return amhsFtbpFileName; }
    public void setAmhsFtbpFileName(String amhsFtbpFileName) { this.amhsFtbpFileName = amhsFtbpFileName; }
    public Long getAmhsFtbpObjectSize() { return amhsFtbpObjectSize; }
    public void setAmhsFtbpObjectSize(Long amhsFtbpObjectSize) { this.amhsFtbpObjectSize = amhsFtbpObjectSize; }
    public String getAmhsFtbpLastMod() { return amhsFtbpLastMod; }
    public void setAmhsFtbpLastMod(String amhsFtbpLastMod) { this.amhsFtbpLastMod = amhsFtbpLastMod; }
    public String getAmhsAtsPri() { return amhsAtsPri; }
    public void setAmhsAtsPri(String amhsAtsPri) { this.amhsAtsPri = amhsAtsPri; }
    public String getAmhsRecipients() { return amhsRecipients; }
    public void setAmhsRecipients(String amhsRecipients) { this.amhsRecipients = amhsRecipients; }
    public String getAmhsAtsFt() { return amhsAtsFt; }
    public void setAmhsAtsFt(String amhsAtsFt) { this.amhsAtsFt = amhsAtsFt; }
    public String getAmhsAtsOhi() { return amhsAtsOhi; }
    public void setAmhsAtsOhi(String amhsAtsOhi) { this.amhsAtsOhi = amhsAtsOhi; }
    public String getAmhsOriginator() { return amhsOriginator; }
    public void setAmhsOriginator(String amhsOriginator) { this.amhsOriginator = amhsOriginator; }
    public String getAmhsSubject() { return amhsSubject; }
    public void setAmhsSubject(String amhsSubject) { this.amhsSubject = amhsSubject; }
    public String getAmhsBodypartType() { return amhsBodypartType; }
    public void setAmhsBodypartType(String amhsBodypartType) { this.amhsBodypartType = amhsBodypartType; }
    public String getAmhsContentEncoding() { return amhsContentEncoding; }
    public void setAmhsContentEncoding(String amhsContentEncoding) { this.amhsContentEncoding = amhsContentEncoding; }
    public String getAmhsMessageSigned() { return amhsMessageSigned; }
    public void setAmhsMessageSigned(String amhsMessageSigned) { this.amhsMessageSigned = amhsMessageSigned; }
    public String getSwimCompression() { return swimCompression; }
    public void setSwimCompression(String swimCompression) { this.swimCompression = swimCompression; }
    public String getAmhsRegisteredIdentifier() { return amhsRegisteredIdentifier; }
    public void setAmhsRegisteredIdentifier(String amhsRegisteredIdentifier) { this.amhsRegisteredIdentifier = amhsRegisteredIdentifier; }
    public String getAmhsUserVisibleString() { return amhsUserVisibleString; }
    public void setAmhsUserVisibleString(String amhsUserVisibleString) { this.amhsUserVisibleString = amhsUserVisibleString; }

    /**
     * Build from priority code
     */
    public static String mapPriorityToAts(int priority) {
        return switch (priority) {
            case 0, 1 -> "SS"; // Flash/Urgent
            case 2 -> "FF";    // Normal-high
            case 3 -> "GG";    // Normal
            default -> "KK";   // Low
        };
    }

    /**
     * Map ATS priority to AMQP priority (0-9)
     */
    public static int mapAtsPriorityToAmqp(String atsPri) {
        if (atsPri == null) return 2;
        return switch (atsPri.toUpperCase()) {
            case "SS" -> 0; // Flash
            case "FF" -> 2; // Normal-high
            case "GG" -> 3; // Normal
            case "KK" -> 4; // Low
            default -> 2;   // Default normal
        };
    }
}
