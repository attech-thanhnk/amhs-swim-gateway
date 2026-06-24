package vn.asg.converter.model.weather;
 
import vn.asg.converter.model.BaseMessage;

/**
 * Model cho bản tin SIGMET/AIRMET (Thời tiết nguy hiểm).
 */
public class SigmetMessage extends BaseMessage {

    private String fir; // VVHM
    private String sequenceNumber; // A01
    private String validityPeriod; // 220600/221000
    private String phenomenon; // TS, SEV TURB...
    private String location; // WI N10 E105 - N15 E110...
    private String movement; // MOV NW 15KT
    private String intensity; // NC, INTSF, WKN

    public String getFir() {
        return fir;
    }

    public void setFir(String fir) {
        this.fir = fir;
    }

    public String getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(String sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getValidityPeriod() {
        return validityPeriod;
    }

    public void setValidityPeriod(String validityPeriod) {
        this.validityPeriod = validityPeriod;
    }

    public String getPhenomenon() {
        return phenomenon;
    }

    public void setPhenomenon(String phenomenon) {
        this.phenomenon = phenomenon;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getMovement() {
        return movement;
    }

    public void setMovement(String movement) {
        this.movement = movement;
    }

    public String getIntensity() {
        return intensity;
    }

    public void setIntensity(String intensity) {
        this.intensity = intensity;
    }

    @Override
    public String toString() {
        if (getOriginalTac() != null && !getOriginalTac().isEmpty()) {
            return getOriginalTac();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(fir != null ? fir : "VVVV").append(" ");
        sb.append(getMessageType() != null ? getMessageType() : "SIGMET").append(" ");
        sb.append(sequenceNumber != null ? sequenceNumber : "01").append(" VALID ");
        sb.append(validityPeriod != null ? validityPeriod : "000000/000000").append("\n");
        sb.append(phenomenon != null ? phenomenon : "UNKNOWN").append(" ");
        if (location != null) sb.append(location).append(" ");
        if (movement != null) sb.append(movement).append(" ");
        if (intensity != null) sb.append(intensity);
        return sb.toString().trim() + "=";
    }

    @Override
    public void validate() throws Exception {
        if (fir == null || fir.isEmpty()) throw new Exception("Missing FIR identifier");
    }
}
