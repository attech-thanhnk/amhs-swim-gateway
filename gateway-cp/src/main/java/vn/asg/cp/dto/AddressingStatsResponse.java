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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceDistribution {
        private String source;
        private long count;
        private double percentage;
    }
}
