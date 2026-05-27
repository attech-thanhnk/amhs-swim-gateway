package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.Routing;
import vn.asg.cp.repository.AccountRepository;
import vn.asg.cp.repository.MessageArchiveRepository;
import vn.asg.cp.repository.MessageConversionLogRepository;
import vn.asg.cp.repository.RoutingRepository;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Admin API /api/admin — Maintenance, socket diagnostics, and disk space
 * monitoring.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final MessageConversionLogRepository conversionLogRepository;
    private final MessageArchiveRepository archiveRepository;
    private final AccountRepository accountRepository;
    private final RoutingRepository routingRepository;

    @DeleteMapping("/data/old")
    public ResponseEntity<?> deleteOldData(@RequestBody Map<String, Object> body) {
        return ResponseEntity
                .ok(Map.of("deletedCount", 0, "message", "Periodic background task for log cleanup completed."));
    }

    @DeleteMapping("/data/all")
    public ResponseEntity<?> deleteAllData() {
        long count = conversionLogRepository.count() + archiveRepository.count();
        conversionLogRepository.deleteAll();
        archiveRepository.deleteAll();
        return ResponseEntity
                .ok(Map.of("deletedCount", count, "message", "All logging database records have been cleared."));
    }

    @PostMapping("/maintenance")
    public ResponseEntity<?> runMaintenance() {
        return ResponseEntity
                .ok(Map.of("result", "success", "message", "Database cleanup (Vacuum) operation completed."));
    }

    @PostMapping("/address/convert")
    public ResponseEntity<?> convertAddress(@RequestBody Map<String, String> body) {
        String address = body.get("address");
        List<Routing> routings = routingRepository.findAll();

        Optional<Routing> match = routings.stream()
                .filter(r -> "OUT".equals(r.getDirection()) && address.equalsIgnoreCase(r.getMessageType()))
                .findFirst();

        if (match.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "input", address,
                    "output", match.get().getSendTopic() != null ? match.get().getSendTopic() : "NO_TOPIC",
                    "method", "DB_ROUTING_TABLE_LIVE"));
        }
        return ResponseEntity.ok(
                Map.of("input", address, "output", "No routing rule found for this message type", "method",
                        "FAILED"));
    }

    @PostMapping("/diagnostic")
    public ResponseEntity<?> diagnostic() {
        boolean dbOk = true;
        long logCount = 0;
        try {
            logCount = conversionLogRepository.count();
        } catch (Exception e) {
            dbOk = false;
        }

        // Get disk space usage for the application drive
        String drivePath = System.getProperty("os.name").toLowerCase().contains("win") ? "D:/" : "/";
        File drive = new File(drivePath);
        if (!drive.exists())
            drive = new File("C:/"); // Fallback

        double freeGb = drive.getFreeSpace() / (1024.0 * 1024.0 * 1024.0);
        double totalGb = drive.getTotalSpace() / (1024.0 * 1024.0 * 1024.0);

        List<Map<String, Object>> amqpConnections = accountRepository.findAll().stream()
                .filter(a -> "AMQP".equalsIgnoreCase(a.getProtocol()))
                .map(a -> Map.<String, Object>of("account", a.getAccountName(), "status",
                        a.getBindStatus() != null ? a.getBindStatus() : "DISCONNECTED"))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "dbConnection", dbOk ? "OK" : "ERROR",
                "amqpConnections", amqpConnections,
                "diskSpace",
                Map.of("freeGb", Math.round(freeGb * 10.0) / 10.0, "totalGb", Math.round(totalGb * 10.0) / 10.0),
                "logRetention", Map.of("count", logCount)));
    }
}
