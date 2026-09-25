package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.ApiResponse;
import vn.asg.cp.dto.UpdateConfigRequest;
import vn.asg.cp.entity.GatewayConfig;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.exception.ValidationException;
import vn.asg.cp.repository.GatewayConfigRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Quản lý cấu hình hệ thống (gateway_config).
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class GatewayConfigController {

    private final GatewayConfigRepository configRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GatewayConfig>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(configRepository.findAll()));
    }

    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<GatewayConfig>> getOne(@PathVariable("key") String key) {
        GatewayConfig config = configRepository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Config", key));
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<GatewayConfig>> update(@PathVariable("key") String key, @RequestBody UpdateConfigRequest request) {
        if (request.getValue() == null) {
            throw new ValidationException("value is required");
        }

        if (isIntegerConfigKey(key)) {
            String val = request.getValue().trim();
            if (!val.isEmpty()) {
                try {
                    Long.parseLong(val);
                } catch (NumberFormatException e) {
                    throw new ValidationException("Value for " + key + " must be an integer");
                }
            }
        }

        GatewayConfig existing = configRepository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Config", key));

        existing.setConfigValue(request.getValue());
        existing.setUpdatedAt(LocalDateTime.now());

        return ResponseEntity.ok(ApiResponse.ok("Configuration updated successfully", configRepository.save(existing)));
    }

    private boolean isIntegerConfigKey(String key) {
        if (key == null) return false;
        String upper = key.toUpperCase();
        return upper.contains("DAYS") || upper.contains("PORT") || upper.contains("TIMEOUT") ||
               upper.contains("INTERVAL") || upper.contains("COUNT") || upper.contains("RETRIES") ||
               upper.contains("SIZE") || upper.contains("MAX_") || upper.contains("PERIOD") ||
               upper.contains("LIMIT");
    }
}

