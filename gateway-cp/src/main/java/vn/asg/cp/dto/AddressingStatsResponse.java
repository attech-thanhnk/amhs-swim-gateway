package vn.asg.cp.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO cho addressing source distribution statistics.
 */
public class AddressingStatsResponse {

    private String period;
    private long totalMessages;
    private List<SourceDistribution> distribution = new ArrayList<>();

    public AddressingStatsResponse() {}

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public long getTotalMessages() { return totalMessages; }
    public void setTotalMessages(long totalMessages) { this.totalMessages = totalMessages; }
    public List<SourceDistribution> getDistribution() { return distribution; }
    public void setDistribution(List<SourceDistribution> distribution) { this.distribution = distribution; }

    public static class SourceDistribution {
        private String source;
        private long count;
        private double percentage;

        public SourceDistribution() {}

        public SourceDistribution(String source, long count, double percentage) {
            this.source = source;
            this.count = count;
            this.percentage = percentage;
        }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
        public double getPercentage() { return percentage; }
        public void setPercentage(double percentage) { this.percentage = percentage; }
    }
}
