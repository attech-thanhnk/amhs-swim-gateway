package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.AddressingStatsResponse;
import vn.asg.cp.repository.GwinRepository;
import vn.asg.cp.service.UnroutedMessageService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Addressing Statistics & Monitoring.
 */
@RestController
@RequestMapping("/api/addressing/stats")
@RequiredArgsConstructor
public class AddressingStatsController {

    private final GwinRepository gwinRepository;
    private final UnroutedMessageService unroutedMessageService;

    @GetMapping("/distribution")
    public ResponseEntity<AddressingStatsResponse> getDistribution(
            @RequestParam(defaultValue = "last_24h") String period) {

        LocalDateTime toTime = LocalDateTime.now();
        LocalDateTime fromTime = switch (period) {
            case "last_hour" -> toTime.minusHours(1);
            case "last_7d" -> toTime.minusDays(7);
            case "last_30d" -> toTime.minusDays(30);
            default -> toTime.minusDays(1);
        };

        List<Object[]> rawData = gwinRepository.countByAddressingSourceBetween(fromTime, toTime);
        // Process raw database rows into DTOs
        long totalMessages = 0;
        List<AddressingStatsResponse.SourceDistribution> distribution = new ArrayList<>();

        for (Object[] row : rawData) {
            String source = (String) row[0];
            long count = ((Number) row[1]).longValue();
            totalMessages += count;
            distribution.add(new AddressingStatsResponse.SourceDistribution(source, count, 0.0));
        }

        for (AddressingStatsResponse.SourceDistribution dist : distribution) {
            double percentage = totalMessages > 0 ? (dist.getCount() * 100.0 / totalMessages) : 0.0;
            dist.setPercentage(Math.round(percentage * 100.0) / 100.0);
        }

        AddressingStatsResponse response = new AddressingStatsResponse();
        response.setPeriod(period);
        response.setTotalMessages(totalMessages);
        response.setDistribution(distribution);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/success-rate")
    public ResponseEntity<Map<String, Object>> getSuccessRate(
            @RequestParam(defaultValue = "last_7d") String period) {

        LocalDateTime toTime = LocalDateTime.now();
        LocalDateTime fromTime = period.equals("last_30d") ? toTime.minusDays(30) : toTime.minusDays(7);

        long totalMessages = gwinRepository.countByTimeBetween(fromTime, toTime);
        long unresolvedCount = unroutedMessageService.getUnroutedCountInRange(fromTime, toTime);
        long resolvedCount = totalMessages - unresolvedCount;
        double successRate = totalMessages > 0 ? (resolvedCount * 100.0 / totalMessages) : 0.0;

        return ResponseEntity.ok(Map.of(
                "period", period,
                "fromTime", fromTime,
                "toTime", toTime,
                "totalMessages", totalMessages,
                "resolved", resolvedCount,
                "unrouted", unresolvedCount,
                "successRate", Math.round(successRate * 100.0) / 100.0,
                "unresolvedRate", Math.round((100.0 - successRate) * 100.0) / 100.0));
    }
}
