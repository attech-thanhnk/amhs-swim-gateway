package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    public ResponseEntity<List<GatewayConfig>> list() {
        return ResponseEntity.ok(configRepository.findAll());
    }

    @GetMapping("/{key}")
    public ResponseEntity<GatewayConfig> getOne(@PathVariable("key") String key) {
        GatewayConfig config = configRepository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Config", key));
        return ResponseEntity.ok(config);
    }

    @PutMapping("/{key}")
    public ResponseEntity<GatewayConfig> update(@PathVariable("key") String key, @RequestBody UpdateConfigRequest request) {
        if (request.getValue() == null) {
            throw new ValidationException("value is required");
        }

        GatewayConfig existing = configRepository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Config", key));

        existing.setConfigValue(request.getValue());
        existing.setUpdatedAt(LocalDateTime.now());

        return ResponseEntity.ok(configRepository.save(existing));
    }
}
