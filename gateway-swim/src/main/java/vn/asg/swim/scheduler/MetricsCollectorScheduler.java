package vn.asg.swim.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.asg.swim.entity.PerformanceMetrics;
import vn.asg.swim.repository.GwinRepository;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.repository.PerformanceMetricsRepository;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;

/**
 * Collects CPU/heap/thread metrics every 60 seconds and records them to
 * high-performance_metrics.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsCollectorScheduler {

    private final PerformanceMetricsRepository metricsRepository;
    private final GwinRepository gwinRepository;
    private final GwoutRepository gwoutRepository;

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void collect() {
        try {
            OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
            MemoryMXBean memMxBean = ManagementFactory.getMemoryMXBean();

            float cpuLoad = (float) osMxBean.getSystemLoadAverage();
            if (cpuLoad < 0)
                cpuLoad = 0;

            long heapUsed = memMxBean.getHeapMemoryUsage().getUsed();
            float heapMb = heapUsed / (1024f * 1024f);

            int threads = ManagementFactory.getThreadMXBean().getThreadCount();

            long msgIn = gwinRepository.count();
            long msgOut = gwoutRepository.countByStatus(2); // STATUS_SENT

            PerformanceMetrics metrics = PerformanceMetrics.builder()
                    .timestamp(Instant.now())
                    .cpuUsage(cpuLoad)
                    .heapMemory(heapMb)
                    .activeThreads(threads)
                    .msgInCount((int) msgIn)
                    .msgOutCount((int) msgOut)
                    .build();

            metricsRepository.save(metrics);
            log.debug("Metrics collected: cpu={}% heap={}MB threads={}", cpuLoad, heapMb, threads);

        } catch (Exception e) {
            log.error("Error collecting metrics: {}", e.getMessage());
        }
    }
}
