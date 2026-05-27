package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.service.SystemMetricsService;
import vn.asg.cp.dto.SystemOverviewResponse;

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

    private final SystemMetricsService metricsService;

    @GetMapping("/health")
    public SystemOverviewResponse getSystemLoad() {
        return metricsService.getSystemLoad();
    }
}
