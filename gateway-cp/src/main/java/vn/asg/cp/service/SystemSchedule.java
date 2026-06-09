package vn.asg.cp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.asg.cp.dto.SystemLoadResponse;

@Service
@RequiredArgsConstructor
public class SystemSchedule {

    private final SystemMetricsService metricsService;
    private final SystemHistoryService historyService;

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
