package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.Account;
import vn.asg.cp.entity.PerformanceMetrics;
import vn.asg.cp.repository.AccountRepository;
import vn.asg.cp.repository.GwinRepository;
import vn.asg.cp.repository.GwoutRepository;
import vn.asg.cp.repository.GwinHistoryRepository;
import vn.asg.cp.repository.GwoutHistoryRepository;
import vn.asg.cp.repository.PerformanceMetricsRepository;
import vn.asg.cp.repository.ServerInfoRepository;
import vn.asg.cp.provider.AppVersionProvider;

import vn.asg.cp.entity.ServerInfo;
import vn.asg.cp.entity.ErrorType;
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
        private final GwoutHistoryRepository gwoutHistoryRepository;
        private final GwinHistoryRepository gwinHistoryRepository;
        private final ServerInfoRepository serverInfoRepository;
        private final AppVersionProvider versionProvider;

        @GetMapping("/stats")
        public ResponseEntity<?> getStats() {
                // 1. JVM Stats
                RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
                MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

                long uptimeSec = runtime.getUptime() / 1000;
                long heapUsedMb = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
                long heapMaxMb = memory.getHeapMemoryUsage().getMax() / (1024 * 1024);

                // Get current info
                String serverName = "";
                String ipAddress = "";
                String version = "";
                String description = "";

                Optional<ServerInfo> serverInfoOpt = serverInfoRepository.findFirstByVersionOrderByUuidDesc(versionProvider.getVersion());

                if (serverInfoOpt.isPresent()) {
                        ServerInfo server = serverInfoOpt.get();
                        serverName = server.getServerName();
                        ipAddress = server.getIpAddress();
                        version = server.getVersion();
                        description = server.getDescription();
                }

                // 2. Metrics từ DB Performance (tổng lũy kế)
                Optional<PerformanceMetrics> latestMetrics = metricsRepository.findFirstByOrderByTimestampDesc();
                long msgInTotal = latestMetrics.map(m -> m.getMsgInCount() != null ? m.getMsgInCount().longValue() : 0L)
                                .orElse(0L);
                long msgOutTotal = latestMetrics
                                .map(m -> m.getMsgOutCount() != null ? m.getMsgOutCount().longValue() : 0L).orElse(0L);

                // 3. Database Message stats (số lượng bản ghi hiện có theo status)
                long activeGwoutTotal = gwoutRepository.countAll();
                long historyGwoutTotal = gwoutHistoryRepository.count();
                Map<String, Object> gwoutStats = Map.of(
                                "total", activeGwoutTotal + historyGwoutTotal,
                                "pending", gwoutRepository.countByStatus(0),
                                "processing", gwoutRepository.countByStatus(3), // map status = 3 (OUT_PUBLISHING)
                                "transformed", gwoutRepository.countByStatus(2) + gwoutHistoryRepository.countByStatus(2),
                                "published", gwoutRepository.countByStatus(4) + gwoutHistoryRepository.countByStatus(4), // map status = 4 (OUT_PUBLISHED)
                                "failed", gwoutRepository.countByStatus(5) + gwoutHistoryRepository.countByStatus(5), // map status = 5 (OUT_FAILED)
                                "convertFailed", gwoutRepository.countByErrorType(ErrorType.CONVERT_FAILED.getValue()) + gwoutHistoryRepository.countByErrorType(ErrorType.CONVERT_FAILED.getValue()),
                                "undefinded", gwoutRepository.countByErrorType(ErrorType.UNDEFINED.getValue()) + gwoutHistoryRepository.countByErrorType(ErrorType.UNDEFINED.getValue())
                        );

                long activeGwinTotal = gwinRepository.countAll();
                long historyGwinTotal = gwinHistoryRepository.count();
                Map<String, Object> gwinStats = Map.of(
                                "total", activeGwinTotal + historyGwinTotal,
                                "pending", gwinRepository.countByStatus(0),
                                "processing", gwinRepository.countByStatus(1),
                                "transformed", gwinRepository.countByStatus(2) + gwinHistoryRepository.countByStatus(2),
                                "sent", gwinRepository.countByStatus(3) + gwinHistoryRepository.countByStatus(3),
                                "failed", gwinRepository.countByStatus(4) + gwinHistoryRepository.countByStatus(4),
                                "unrouted", gwinRepository.countByStatus(5) + gwinHistoryRepository.countByStatus(5),
                                "convertFailed", gwinRepository.countByErrorType(ErrorType.CONVERT_FAILED.getValue()) + gwinHistoryRepository.countByErrorType(ErrorType.CONVERT_FAILED.getValue()),
                                "undefinded", gwinRepository.countByErrorType(ErrorType.UNDEFINED.getValue()) + gwinHistoryRepository.countByErrorType(ErrorType.UNDEFINED.getValue())
                        );

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
                                                "version", version,
                                                "heapUsedMb", heapUsedMb,
                                                "heapMaxMb", heapMaxMb,
                                                "serverName", serverName,
                                                "ipAddress", ipAddress,
                                                "description", description
                                        ),
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
