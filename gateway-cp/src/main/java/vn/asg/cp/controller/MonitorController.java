package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.Account;
import vn.asg.cp.entity.PerformanceMetrics;
import vn.asg.cp.repository.AccountRepository;
import vn.asg.cp.repository.GwinRepository;
import vn.asg.cp.repository.GwoutRepository;
import vn.asg.cp.repository.PerformanceMetricsRepository;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MonitorController — Tổng quan trạng thái hệ thống:
 * JVM, Disk, Kết nối account và Thống kê luồng điện văn.
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

        private final AccountRepository accountRepository;
        private final PerformanceMetricsRepository metricsRepository;
        private final GwoutRepository gwoutRepository;
        private final GwinRepository gwinRepository;

        @GetMapping("/stats")
        public ResponseEntity<?> getStats() {
                // 1. JVM Stats
                RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
                MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

                long uptimeSec = runtime.getUptime() / 1000;
                long heapUsedMb = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
                long heapMaxMb = memory.getHeapMemoryUsage().getMax() / (1024 * 1024);

                // 2. Metrics từ DB Performance (tổng lũy kế)
                Optional<PerformanceMetrics> latestMetrics = metricsRepository.findFirstByOrderByTimestampDesc();
                long msgInTotal = latestMetrics.map(m -> m.getMsgInCount() != null ? m.getMsgInCount().longValue() : 0L)
                                .orElse(0L);
                long msgOutTotal = latestMetrics
                                .map(m -> m.getMsgOutCount() != null ? m.getMsgOutCount().longValue() : 0L).orElse(0L);

                // 3. Database Message stats (số lượng bản ghi hiện có theo status)
                Map<String, Object> gwoutStats = Map.of(
                                "new", gwoutRepository.countByStatus(0),
                                "processing", gwoutRepository.countByStatus(1),
                                "error", gwoutRepository.countByStatus(3));

                Map<String, Object> gwinStats = Map.of(
                                "new", gwinRepository.countByStatus(0),
                                "error", gwinRepository.countByStatus(3));

                // 4. Accounts Connection Status
                List<Account> accounts = accountRepository.findAll();
                List<Map<String, Object>> amqpAccounts = accounts.stream()
                                .filter(a -> "AMQP".equalsIgnoreCase(a.getProtocol()))
                                .map(a -> Map.<String, Object>of(
                                                "id", a.getId(),
                                                "name", a.getAccountName(),
                                                "status",
                                                a.getBindStatus() != null ? a.getBindStatus() : "DISCONNECTED"))
                                .collect(Collectors.toList());

                List<Map<String, Object>> amhsAccounts = accounts.stream()
                                .filter(a -> "AMHS".equalsIgnoreCase(a.getProtocol())
                                                || "X400".equalsIgnoreCase(a.getProtocol()))
                                .map(a -> Map.<String, Object>of(
                                                "id", a.getId(),
                                                "name", a.getAccountName(),
                                                "status",
                                                a.getBindStatus() != null ? a.getBindStatus() : "DISCONNECTED"))
                                .collect(Collectors.toList());

                // 5. Build Response
                Map<String, Object> response = Map.of(
                                "server", Map.of(
                                                "uptime", uptimeSec,
                                                "version", "2.0.0",
                                                "heapUsedMb", heapUsedMb,
                                                "heapMaxMb", heapMaxMb),
                                "trafficCumulative", Map.of(
                                                "inbound", msgInTotal,
                                                "outbound", msgOutTotal),
                                "database", Map.of(
                                                "gw_out", gwoutStats,
                                                "gw_in", gwinStats),
                                "connections", Map.of(
                                                "amqp", amqpAccounts,
                                                "amhs", amhsAccounts,
                                                "activeAmqp",
                                                (Object) amqpAccounts.stream()
                                                                .filter(a -> "CONNECTED".equals(a.get("status")))
                                                                .count()));

                return ResponseEntity.ok(response);
        }
}
