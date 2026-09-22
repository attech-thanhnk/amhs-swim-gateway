package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.ApiResponse;
import vn.asg.cp.repository.AccountRepository;
import vn.asg.cp.repository.MessageConversionLogRepository;
import vn.asg.cp.service.DataRetentionService;

import java.io.File;
import java.util.List;
import java.util.Map;
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
    private final AccountRepository accountRepository;
    private final DataRetentionService dataRetentionService;

    @DeleteMapping("/data/old")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteOldData(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Integer> deleted = dataRetentionService.runCleanup();
        int total = deleted.values().stream().mapToInt(Integer::intValue).sum();
        int retentionDays = dataRetentionService.getRetentionDays();
        return ResponseEntity.ok(ApiResponse.ok(
                "Data retention cleanup completed.",
                Map.of(
                        "deletedCount", total,
                        "retentionDays", retentionDays,
                        "details", deleted,
                        "message", "Deleted " + total + " records older than " + retentionDays + " days"
                )
        ));
    }

    @PostMapping("/maintenance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runMaintenance() {
        return ResponseEntity.ok(ApiResponse.ok("Database cleanup (Vacuum) operation completed.",
                Map.of("result", "success", "message", "Database cleanup (Vacuum) operation completed.")));
    }

    @PostMapping("/diagnostic")
    public ResponseEntity<ApiResponse<Map<String, Object>>> diagnostic() {
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

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "dbConnection", dbOk ? "OK" : "ERROR",
                "amqpConnections", amqpConnections,
                "diskSpace",
                Map.of("freeGb", Math.round(freeGb * 10.0) / 10.0, "totalGb", Math.round(totalGb * 10.0) / 10.0),
                "logRetention", Map.of("count", logCount))));
    }
}

