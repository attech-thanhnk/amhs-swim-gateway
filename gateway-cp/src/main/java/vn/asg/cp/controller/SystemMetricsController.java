package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.ApiResponse;
import vn.asg.cp.service.SystemMetricsService;
import vn.asg.cp.dto.SystemOverviewResponse;

/**
 * GET /api/system/metrics — giám sát tài nguyên máy chủ
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemMetricsController {

    private final SystemMetricsService metricsService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<SystemOverviewResponse>> getSystemLoad() {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.getSystemLoad()));
    }
}

