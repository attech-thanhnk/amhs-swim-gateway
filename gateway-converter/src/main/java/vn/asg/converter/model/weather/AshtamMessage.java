package vn.asg.converter.model.weather;

import vn.asg.converter.model.BaseMessage;

/**
 * Model cho bản tin ASHTAM (Báo cáo tro bụi núi lửa).
 */
public class AshtamMessage extends BaseMessage {
    private String ashtamId;
    private String aerodrome; // A)
    private String observationTime; // B)
    private String volcanoName; // C)
    private String volcanoLocation; // D)
    private String colorCode; // E)
    private String cloudExtent; // F)
    private String cloudDirection; // G)
    private String airRoutes; // H)
    private String airspaceClose; // I)
    private String infSource; // J)
    private String remarks; // K)

    public String getAshtamId() { return ashtamId; }
    public void setAshtamId(String ashtamId) { this.ashtamId = ashtamId; }

    public String getAerodrome() { return aerodrome; }
    public void setAerodrome(String aerodrome) { this.aerodrome = aerodrome; }

    public String getObservationTime() { return observationTime; }
    public void setObservationTime(String observationTime) { this.observationTime = observationTime; }

    public String getVolcanoName() { return volcanoName; }
    public void setVolcanoName(String volcanoName) { this.volcanoName = volcanoName; }

    public String getVolcanoLocation() { return volcanoLocation; }
    public void setVolcanoLocation(String volcanoLocation) { this.volcanoLocation = volcanoLocation; }

    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }

    public String getCloudExtent() { return cloudExtent; }
    public void setCloudExtent(String cloudExtent) { this.cloudExtent = cloudExtent; }

    public String getCloudDirection() { return cloudDirection; }
    public void setCloudDirection(String cloudDirection) { this.cloudDirection = cloudDirection; }

    public String getAirRoutes() { return airRoutes; }
    public void setAirRoutes(String airRoutes) { this.airRoutes = airRoutes; }

    public String getAirspaceClose() { return airspaceClose; }
    public void setAirspaceClose(String airspaceClose) { this.airspaceClose = airspaceClose; }

    public String getInfSource() { return infSource; }
    public void setInfSource(String infSource) { this.infSource = infSource; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    @Override
    public String toString() {
        if (getOriginalTac() != null && !getOriginalTac().isEmpty()) return getOriginalTac();
        StringBuilder sb = new StringBuilder();
        sb.append("(ASHTAM").append(ashtamId != null ? ashtamId : "1234").append(" ");
        sb.append("A)").append(aerodrome != null ? aerodrome : "VVTS").append("\n");
        sb.append("B)").append(observationTime != null ? observationTime : "00000000").append("\n");
        if (volcanoName != null) sb.append("C)").append(volcanoName).append("\n");
        if (volcanoLocation != null) sb.append("D)").append(volcanoLocation).append("\n");
        if (colorCode != null) sb.append("E)").append(colorCode).append("\n");
        if (cloudExtent != null) sb.append("F)").append(cloudExtent).append("\n");
        if (cloudDirection != null) sb.append("G)").append(cloudDirection).append("\n");
        if (airRoutes != null) sb.append("H)").append(airRoutes).append("\n");
        if (airspaceClose != null) sb.append("I)").append(airspaceClose).append("\n");
        if (infSource != null) sb.append("J)").append(infSource).append("\n");
        if (remarks != null) sb.append("K)").append(remarks).append("\n");
        sb.append(")");
        return sb.toString().trim();
    }

    @Override
    public void validate() throws Exception {
        if (ashtamId == null) throw new Exception("Missing ASHTAM ID");
        if (aerodrome == null) throw new Exception("Missing Aerodrome (Field A)");
    }
}
