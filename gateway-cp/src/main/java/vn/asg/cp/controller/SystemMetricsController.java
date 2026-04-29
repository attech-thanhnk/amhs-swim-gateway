package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.PerformanceMetrics;
import vn.asg.cp.repository.PerformanceMetricsRepository;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GET /api/system/metrics — giám sát tài nguyên máy chủ
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemMetricsController {

    private final PerformanceMetricsRepository metricsRepository;

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics(@RequestParam(name = "last", defaultValue = "60") int last) {
        Instant since = Instant.now().minusSeconds((long) last * 60);

        List<PerformanceMetrics> history = metricsRepository.findByTimestampAfterOrderByTimestampAsc(since);

        // Current JVM stats
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        double cpuLoad = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        long heapUsed = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();

        Optional<PerformanceMetrics> latest = metricsRepository.findFirstByOrderByTimestampDesc();

        Map<String, Object> current = Map.of(
                "cpuUsage", Math.max(0, cpuLoad),
                "heapMemoryMb", heapUsed,
                "activeThreads", threads,
                "msgInCount", latest.map(m -> m.getMsgInCount() != null ? m.getMsgInCount() : 0).orElse(0),
                "msgOutCount", latest.map(m -> m.getMsgOutCount() != null ? m.getMsgOutCount() : 0).orElse(0));

        return ResponseEntity.ok(Map.of(
                "current", current,
                "history", history));
    }
}
