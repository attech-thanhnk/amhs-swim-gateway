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

    @PutMapping("/bulk-ack")
    public ResponseEntity<Void> bulkAcknowledge(Principal principal) {
        List<GwAlert> activeAlerts = alertRepository.findByStatus(GwAlert.STATUS_NEW);
        LocalDateTime now = LocalDateTime.now();
        String username = principal != null ? principal.getName() : "operator";
        for (GwAlert alert : activeAlerts) {
            alert.setStatus(GwAlert.STATUS_ACKNOWLEDGED);
            alert.setAcknowledgedAt(now);
            alert.setAcknowledgedBy(username);
        }
        alertRepository.saveAll(activeAlerts);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/bulk-resolve")
    public ResponseEntity<Void> bulkResolve() {
        List<GwAlert> ackAlerts = alertRepository.findByStatus(GwAlert.STATUS_ACKNOWLEDGED);
        List<GwAlert> newAlerts = alertRepository.findByStatus(GwAlert.STATUS_NEW);
        LocalDateTime now = LocalDateTime.now();
        for (GwAlert alert : ackAlerts) {
            alert.setStatus(GwAlert.STATUS_RESOLVED);
            alert.setResolvedAt(now);
        }
        for (GwAlert alert : newAlerts) {
            alert.setStatus(GwAlert.STATUS_RESOLVED);
            alert.setResolvedAt(now);
        }
        alertRepository.saveAll(ackAlerts);
        alertRepository.saveAll(newAlerts);
        return ResponseEntity.ok().build();
    }
}
