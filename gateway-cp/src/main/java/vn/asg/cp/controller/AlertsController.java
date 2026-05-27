package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.GwAlert;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.repository.GwAlertRepository;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertsController {

    private final GwAlertRepository alertRepository;

    @GetMapping
    public ResponseEntity<List<GwAlert>> list() {
        return ResponseEntity.ok((List<GwAlert>) alertRepository.findAll());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<GwAlert>> byStatus(@PathVariable("status") String status) {
        return ResponseEntity.ok(alertRepository.findByStatus(status));
    }

    @PutMapping("/{id}/ack")
    public ResponseEntity<GwAlert> acknowledge(@PathVariable("id") Long id, Principal principal) {
        GwAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id));

        alert.setStatus(GwAlert.STATUS_ACKNOWLEDGED);
        alert.setAcknowledgedAt(LocalDateTime.now());
        if (principal != null) {
            alert.setAcknowledgedBy(principal.getName());
        }

        return ResponseEntity.ok(alertRepository.save(alert));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<GwAlert> resolve(@PathVariable("id") Long id) {
        GwAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id));

        alert.setStatus(GwAlert.STATUS_RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());

        return ResponseEntity.ok(alertRepository.save(alert));
    }
}
