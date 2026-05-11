package vn.asg.converter.model;

import org.joda.time.DateTime;

public class NotamMessage {
    private String notamId;        // e.g. A0123/24
    private String type;           // NOTAMN, NOTAMR, NOTAMC
    private String fir;            // Q-line: VVHM
    private String notamCode;      // Q-line: QFAAH
    private String traffic;        // Q-line: IV
    private String purpose;        // Q-line: NBO
    private String scope;          // Q-line: A
    private String lowerLimit;     // Q-line: 000
    private String upperLimit;     // Q-line: 999
    private String coordinates;    // Q-line: 1059N10645E005
    private String location;       // A) VVNB
    private DateTime validFrom;    // B) 2401010000
    private DateTime validUntil;   // C) 2401312359 (or null if PERM)
    private boolean isPermanent;   // true if C) is PERM
    private String schedule;       // D)
    private String text;           // E) RWY 11R/29L CLSD...
    private String lowerHeight;    // F)
    private String upperHeight;    // G)

    // Getters and Setters

    public String getNotamId() { return notamId; }
    public void setNotamId(String notamId) { this.notamId = notamId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFir() { return fir; }
    public void setFir(String fir) { this.fir = fir; }

    public String getNotamCode() { return notamCode; }
    public void setNotamCode(String notamCode) { this.notamCode = notamCode; }

    public String getTraffic() { return traffic; }
    public void setTraffic(String traffic) { this.traffic = traffic; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getLowerLimit() { return lowerLimit; }
    public void setLowerLimit(String lowerLimit) { this.lowerLimit = lowerLimit; }

    public String getUpperLimit() { return upperLimit; }
    public void setUpperLimit(String upperLimit) { this.upperLimit = upperLimit; }

    public String getCoordinates() { return coordinates; }
    public void setCoordinates(String coordinates) { this.coordinates = coordinates; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public DateTime getValidFrom() { return validFrom; }
    public void setValidFrom(DateTime validFrom) { this.validFrom = validFrom; }

    public DateTime getValidUntil() { return validUntil; }
    public void setValidUntil(DateTime validUntil) { this.validUntil = validUntil; }

    public boolean isPermanent() { return isPermanent; }
    public void setPermanent(boolean isPermanent) { this.isPermanent = isPermanent; }

    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getLowerHeight() { return lowerHeight; }
    public void setLowerHeight(String lowerHeight) { this.lowerHeight = lowerHeight; }

    public String getUpperHeight() { return upperHeight; }
    public void setUpperHeight(String upperHeight) { this.upperHeight = upperHeight; }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(notamId != null && !notamId.isEmpty() ? notamId : "A0000/00");
        sb.append(" ").append(type != null && !type.isEmpty() ? type : "NOTAMN").append("\n");
        
        // Q) Line
        sb.append("Q) ").append(fir != null && !fir.isEmpty() ? fir : "VVVV");
        sb.append("/").append(notamCode != null && !notamCode.isEmpty() ? notamCode : "QXXXX");
        sb.append("/").append(traffic != null && !traffic.isEmpty() ? traffic : "IV");
        sb.append("/").append(purpose != null && !purpose.isEmpty() ? purpose : "NBO");
        sb.append("/").append(scope != null && !scope.isEmpty() ? scope : "A");
        sb.append("/").append(lowerLimit != null && !lowerLimit.isEmpty() ? lowerLimit : "000");
        sb.append("/").append(upperLimit != null && !upperLimit.isEmpty() ? upperLimit : "999");
        sb.append("/").append(coordinates != null && !coordinates.isEmpty() ? coordinates : "").append("\n");
        
        if (location != null && !location.isEmpty()) {
            sb.append("A) ").append(location).append("\n");
        }
        
        if (validFrom != null) {
            sb.append("B) ").append(validFrom.toString("yyMMddHHmm")).append("\n");
        }
        
        sb.append("C) ");
        if (isPermanent) {
            sb.append("PERM");
        } else if (validUntil != null) {
            sb.append(validUntil.toString("yyMMddHHmm"));
        } else {
            sb.append("EST");
        }
        sb.append("\n");
        
        if (schedule != null && !schedule.isEmpty()) {
            sb.append("D) ").append(schedule).append("\n");
        }
        
        if (text != null && !text.isEmpty()) {
            sb.append("E) ").append(text).append("\n");
        }
        
        if (lowerHeight != null && !lowerHeight.isEmpty()) {
            sb.append("F) ").append(lowerHeight).append("\n");
        }
        
        if (upperHeight != null && !upperHeight.isEmpty()) {
            sb.append("G) ").append(upperHeight).append("\n");
        }
        
        sb.append(")");
        return sb.toString();
    }
}
