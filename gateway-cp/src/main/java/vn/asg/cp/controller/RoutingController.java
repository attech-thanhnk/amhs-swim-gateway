package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.ApiResponse;
import vn.asg.cp.dto.CreateRoutingRequest;
import vn.asg.cp.dto.UpdateRoutingRequest;
import vn.asg.cp.service.SystemHistoryService;
import vn.asg.cp.entity.Routing;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.exception.ValidationException;
import vn.asg.cp.repository.RoutingRepository;

import java.util.List;

/**
 * Các API xử lý CRUD cho cấu hình định tuyến (Routing).
 */
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/routing")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingRepository routingRepository;
    private final SystemHistoryService systemHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Routing>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(routingRepository.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Routing>> getOne(@PathVariable("id") Integer id) {
        Routing routing = routingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routing", id));
        return ResponseEntity.ok(ApiResponse.ok(routing));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Routing>> create(@Valid @RequestBody CreateRoutingRequest request) {
        String direction = request.getDirection() != null ? request.getDirection().trim().toUpperCase() : null;
        if (direction == null || (!direction.equals("IN") && !direction.equals("OUT"))) {
            throw new ValidationException("Direction is required (must be IN or OUT)");
        }

        if ("IN".equals(direction)) {
            if (request.getReceiveTopic() == null || request.getReceiveTopic().isBlank()) {
                throw new ValidationException("Receive topic is required for IN direction");
            }
            if (request.getRecipients() == null || request.getRecipients().isBlank()) {
                throw new ValidationException("Recipients/destination is required for IN direction");
            }
        } else {
            if (request.getSendTopic() == null || request.getSendTopic().isBlank()) {
                throw new ValidationException("Send topic is required for OUT direction");
            }
            if (request.getRecipients() == null || request.getRecipients().isBlank()) {
                throw new ValidationException("Recipients are required for OUT direction");
            }
        }

        validateAftnRecipients(request.getRecipients());

        Routing routing = new Routing();
        routing.setDirection(direction);
        routing.setReceiveTopic(request.getReceiveTopic() != null ? request.getReceiveTopic().trim() : null);
        routing.setRecipients(request.getRecipients() != null ? request.getRecipients().trim() : null);
        routing.setSendTopic(request.getSendTopic() != null ? request.getSendTopic().trim() : null);
        routing.setPriority(request.getPriority() != null ? request.getPriority() : 100);
        routing.setActive(request.getActive() != null ? request.getActive() : true);
        routing.setNote(request.getNote() != null ? request.getNote().trim() : null);

        sanitizeTopic(routing);

        Routing saved = routingRepository.save(routing);

        systemHistoryService.info(
                "ROUTING_CREATED",
                String.format("Routing '%s' created", saved.getId())
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Routing created successfully", saved));
    }

    private void sanitizeTopic(Routing routing) {
        if (routing.getSendTopic() != null) {
            routing.setSendTopic(routing.getSendTopic().replace('.', '/'));
        }
        if (routing.getReceiveTopic() != null) {
            routing.setReceiveTopic(routing.getReceiveTopic().replace('.', '/'));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Routing>> update(@PathVariable("id") Integer id, @Valid @RequestBody UpdateRoutingRequest request) {
        Routing existing = routingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routing", id));

        String direction = request.getDirection() != null ? request.getDirection().trim().toUpperCase() : existing.getDirection();
        if (direction != null && !direction.equals("IN") && !direction.equals("OUT")) {
            throw new ValidationException("Direction must be IN or OUT");
        }
        existing.setDirection(direction);

        if (request.getReceiveTopic() != null) {
            if ("IN".equals(direction) && request.getReceiveTopic().isBlank()) {
                throw new ValidationException("Receive topic cannot be blank for IN direction");
            }
            existing.setReceiveTopic(request.getReceiveTopic().trim());
        }
        if (request.getSendTopic() != null) {
            if ("OUT".equals(direction) && request.getSendTopic().isBlank()) {
                throw new ValidationException("Send topic cannot be blank for OUT direction");
            }
            existing.setSendTopic(request.getSendTopic().trim());
        }
        if (request.getRecipients() != null) {
            if (request.getRecipients().isBlank()) {
                throw new ValidationException("Recipients cannot be blank");
            }
            validateAftnRecipients(request.getRecipients());
            existing.setRecipients(request.getRecipients().trim());
        }
        if (request.getPriority() != null)
            existing.setPriority(request.getPriority());
        if (request.getActive() != null)
            existing.setActive(request.getActive());
        if (request.getNote() != null)
            existing.setNote(request.getNote().trim());

        sanitizeTopic(existing);
        Routing updated = routingRepository.save(existing);
        systemHistoryService.info(
                "ROUTING_UPDATED",
                String.format("Routing '%s' updated", updated.getId())
        );

        return ResponseEntity.ok(ApiResponse.ok("Routing updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") int id) {
        Routing routing = routingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routing", id));
        try {
            routingRepository.delete(routing);
            systemHistoryService.warn(
                    "ROUTING_DELETED",
                    String.format("Routing '%s' deleted", routing.getId())
            );

            return ResponseEntity.ok(ApiResponse.ok("Routing deleted successfully", null));
        } catch (Exception e) {
            systemHistoryService.error(
                    "ROUTING_DELETE_FAILED",
                    String.format("Failed to delete routing '%s'", routing.getId()),
                    e.getMessage()
            );
            throw new ValidationException("Failed to delete routing: " + e.getMessage());
        }
    }

    private void validateAftnRecipients(String recipients) {
        if (recipients == null || recipients.isBlank()) return;
        String[] parts = recipients.split("[,;\\s]+");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty() && !trimmed.matches("^[A-Za-z]{8}$")) {
                throw new ValidationException("Invalid AFTN address format: " + trimmed + " (must be 8 alphabetic characters, e.g. VVNBZTZX)");
            }
        }
    }
}
