package vn.asg.converter.model.weather;

import vn.asg.converter.model.BaseMessage;
import java.util.List;

/**
 * Model cho bản tin METAR/SPECI (Khí tượng sân bay).
 */
public class MetarMessage extends BaseMessage {

    private String stationIcao; // VVNB
    private String observationTime; // 221000Z
    private boolean isNil; // true nếu bản tin là NIL
    private String wind; // 27005KT
    private String visibility; // 9999
    private List<String> weather; // -RA, TS, FG...
    private String cloud; // SCT020 FEW030CB
    private String temperature; // 28/24
    private String qnh; // Q1012
    private String recentWeather; // RE-RA
    private String trend; // NOSIG, BECMG...

    public String getStationIcao() {
        return stationIcao;
    }

    public void setStationIcao(String stationIcao) {
        this.stationIcao = stationIcao;
    }

    public String getObservationTime() {
        return observationTime;
    }

    public void setObservationTime(String observationTime) {
        this.observationTime = observationTime;
    }

    public boolean isNil() {
        return isNil;
    }

    public void setNil(boolean nil) {
        isNil = nil;
    }

    public String getWind() {
        return wind;
    }

    public void setWind(String wind) {
        this.wind = wind;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public List<String> getWeather() {
        return weather;
    }

    public void setWeather(List<String> weather) {
        this.weather = weather;
    }

    public String getCloud() {
        return cloud;
    }

    public void setCloud(String cloud) {
        this.cloud = cloud;
    }

    public String getTemperature() {
        return temperature;
    }

    public void setTemperature(String temperature) {
        this.temperature = temperature;
    }

    public String getQnh() {
        return qnh;
    }

    public void setQnh(String qnh) {
        this.qnh = qnh;
    }

    public String getRecentWeather() {
        return recentWeather;
    }

    public void setRecentWeather(String recentWeather) {
        this.recentWeather = recentWeather;
    }

    public String getTrend() {
        return trend;
    }

    public void setTrend(String trend) {
        this.trend = trend;
    }

    @Override
    public String toString() {
        if (getOriginalTac() != null && !getOriginalTac().isEmpty()) {
            return getOriginalTac();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(getMessageType() != null ? getMessageType() : "METAR").append(" ");
        sb.append(stationIcao != null ? stationIcao : "VVVV").append(" ");
        sb.append(observationTime != null ? observationTime : "000000Z").append(" ");
        if (isNil) {
            sb.append("NIL");
        } else {
            if (wind != null) sb.append(wind).append(" ");
            if (visibility != null) sb.append(visibility).append(" ");
            if (weather != null && !weather.isEmpty()) {
                sb.append(String.join(" ", weather)).append(" ");
            }
            if (cloud != null) sb.append(cloud).append(" ");
            if (temperature != null) sb.append(temperature).append(" ");
            if (qnh != null) sb.append(qnh).append(" ");
            if (recentWeather != null) sb.append(recentWeather).append(" ");
            if (trend != null) sb.append(trend);
        }
        return sb.toString().trim() + "=";
    }

    @Override
    public void validate() throws Exception {
        if (stationIcao == null || stationIcao.isEmpty()) throw new Exception("Missing Station ICAO");
        if (observationTime == null || observationTime.isEmpty()) throw new Exception("Missing Observation Time");
    }
}
