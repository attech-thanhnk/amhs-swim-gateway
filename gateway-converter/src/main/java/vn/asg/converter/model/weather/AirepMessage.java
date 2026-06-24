package vn.asg.converter.model.weather;
 
import vn.asg.converter.model.BaseMessage;

/**
 * Model cho bản tin AIREP (Báo cáo vị trí/khí tượng từ tàu bay).
 */
public class AirepMessage extends BaseMessage {

    private String aircraftId;
    private String position;
    private String time;
    private String level;
    private String meteorologicalInfo;

    public String getAircraftId() {
        return aircraftId;
    }

    public void setAircraftId(String aircraftId) {
        this.aircraftId = aircraftId;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMeteorologicalInfo() {
        return meteorologicalInfo;
    }

    public void setMeteorologicalInfo(String meteorologicalInfo) {
        this.meteorologicalInfo = meteorologicalInfo;
    }

    @Override
    public String toString() {
        if (getOriginalTac() != null && !getOriginalTac().isEmpty()) {
            return getOriginalTac();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("ARP ");
        sb.append(aircraftId != null ? aircraftId : "UNKNOWN").append(" ");
        sb.append(position != null ? position : "UNKNOWN").append(" ");
        sb.append(time != null ? time : "0000").append(" ");
        sb.append(level != null ? level : "F330").append("\n");
        if (meteorologicalInfo != null) sb.append(meteorologicalInfo);
        return sb.toString().trim() + "=";
    }

    @Override
    public void validate() throws Exception {
        if (aircraftId == null || aircraftId.isEmpty()) throw new Exception("Missing Aircraft Identification");
    }
}
