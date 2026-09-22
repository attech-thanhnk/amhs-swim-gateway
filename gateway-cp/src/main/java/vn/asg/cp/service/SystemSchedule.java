package vn.asg.cp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.asg.cp.dto.SystemLoadResponse;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSchedule {

    private final SystemMetricsService metricsService;
    private final SystemHistoryService historyService;
    private final DataRetentionService dataRetentionService;

    private boolean highCpuTriggered = false;
    private boolean highMemoryTriggered = false;

    private static final double CPU_THRESHOLD = 50.0;
    private static final double MEMORY_THRESHOLD = 30.0;

    private static final long COOLDOWN_MS = 300000; // 5 minutes
    private long lastCpuWarningTime = 0;

    @Scheduled(fixedDelay = 1)
    public void monitorSystemResources() {

        try {

            SystemLoadResponse load =
                    metricsService.getGatewayload();

            checkCpu(load.getSystemCpuLoad());

            checkMemory(load.getRamUsedPercent());
        } catch (Exception ex) {

            historyService.error(
                    "MONITOR_ERROR",
                    "System monitor failed",
                    ex.getMessage()
            );
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runDataRetention() {
        log.info("[Retention] Scheduled data retention job started");
        try {
            Map<String, Integer> deleted = dataRetentionService.runCleanup();
            int total = deleted.values().stream().mapToInt(Integer::intValue).sum();
            log.info("[Retention] Job completed — {} total rows deleted: {}", total, deleted);
        } catch (Exception e) {
            log.error("[Retention] Scheduled job failed: {}", e.getMessage(), e);
        }
    }

    private void checkCpu(double cpuPercent) {
        if (cpuPercent >= CPU_THRESHOLD) {
            long now = System.currentTimeMillis();

            if (now - lastCpuWarningTime >= COOLDOWN_MS) {
                historyService.warn(
                        "HIGH_CPU",
                        String.format(
                            "CPU usage reached %.2f%%",
                            cpuPercent
                        )
                );
                lastCpuWarningTime = now;
            }
        }
    }

    private void checkMemory(double memoryPercent) {

        if (memoryPercent >= MEMORY_THRESHOLD) {

            if (!highMemoryTriggered) {

                historyService.warn(
                        "HIGH_MEMORY",
                        String.format(
                                "Memory usage reached %.2f%%",
                                memoryPercent
                        )
                );

                highMemoryTriggered = true;
            }

        } else if (memoryPercent < 85) {

            highMemoryTriggered = false;
        }
    }
}
