package vn.asg.cp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO cho addressing source distribution statistics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressingStatsResponse {

    private String period;
    private long totalMessages;
    private List<SourceDistribution> distribution = new ArrayList<>();

    public static class SourceDistribution {
        private String source;
        private long count;
        private double percentage;

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
