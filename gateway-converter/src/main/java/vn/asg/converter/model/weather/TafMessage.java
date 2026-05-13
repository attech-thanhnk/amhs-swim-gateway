package vn.asg.converter.model.weather;

import vn.asg.converter.model.BaseMessage;
import java.util.List;

/**
 * Model cho bản tin TAF (Dự báo thời tiết sân bay).
 */
public class TafMessage extends BaseMessage {

    private String stationIcao;
    private String issueTime;
    private String validity; // 2206/2312
    private String baseConditions; // Wind, Vis, Clouds...
    private List<TafTrend> trends;

    public static class TafTrend {
        private String type; // BECMG, TEMPO, PROB30...
        private String period; // 2208/2210
        private String conditions;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPeriod() {
            return period;
        }

        public void setPeriod(String period) {
            this.period = period;
        }

        public String getConditions() {
            return conditions;
        }

        public void setConditions(String conditions) {
            this.conditions = conditions;
        }
    }

    public String getStationIcao() {
        return stationIcao;
    }

    public void setStationIcao(String stationIcao) {
        this.stationIcao = stationIcao;
    }

    public String getIssueTime() {
        return issueTime;
    }

    public void setIssueTime(String issueTime) {
        this.issueTime = issueTime;
    }

    public String getValidity() {
        return validity;
    }

    public void setValidity(String validity) {
        this.validity = validity;
    }

    public String getBaseConditions() {
        return baseConditions;
    }

    public void setBaseConditions(String baseConditions) {
        this.baseConditions = baseConditions;
    }

    public List<TafTrend> getTrends() {
        return trends;
    }

    public void setTrends(List<TafTrend> trends) {
        this.trends = trends;
    }

    @Override
    public String toString() {
        if (getOriginalTac() != null && !getOriginalTac().isEmpty()) {
            return getOriginalTac();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("TAF ");
        if (stationIcao != null) sb.append(stationIcao).append(" ");
        if (issueTime != null) sb.append(issueTime).append(" ");
        if (validity != null) sb.append(validity).append(" ");
        if (baseConditions != null) sb.append(baseConditions).append(" ");
        if (trends != null) {
            for (TafTrend trend : trends) {
                sb.append("\n  ").append(trend.getType()).append(" ").append(trend.getPeriod()).append(" ").append(trend.getConditions());
            }
        }
        return sb.toString().trim() + "=";
    }

    @Override
    public void validate() throws Exception {
        if (stationIcao == null || stationIcao.isEmpty()) throw new Exception("Missing Station ICAO");
    }
}
