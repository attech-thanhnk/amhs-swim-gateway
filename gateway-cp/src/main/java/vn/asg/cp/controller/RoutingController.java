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
    public ResponseEntity<ApiResponse<Routing>> create(@RequestBody CreateRoutingRequest request) {
        if (request.getDirection() == null || request.getDirection().isBlank()) {
            throw new ValidationException("direction is required (IN/OUT)");
        }

        Routing routing = new Routing();
        routing.setDirection(request.getDirection());
        routing.setReceiveTopic(request.getReceiveTopic());
        routing.setRecipients(request.getRecipients());
        routing.setMessageType(request.getMessageType());
        routing.setSendTopic(request.getSendTopic());
        routing.setPriority(request.getPriority() != null ? request.getPriority() : 100);
        routing.setActive(request.getActive() != null ? request.getActive() : true);
        routing.setNote(request.getNote());

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
    public ResponseEntity<ApiResponse<Routing>> update(@PathVariable("id") Integer id, @RequestBody UpdateRoutingRequest request) {
        Routing existing = routingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routing", id));

        if (request.getDirection() != null)
            existing.setDirection(request.getDirection());
        if (request.getReceiveTopic() != null)
            existing.setReceiveTopic(request.getReceiveTopic());
        if (request.getRecipients() != null)
            existing.setRecipients(request.getRecipients());
        if (request.getMessageType() != null)
            existing.setMessageType(request.getMessageType());
        if (request.getSendTopic() != null)
            existing.setSendTopic(request.getSendTopic());
        if (request.getPriority() != null)
            existing.setPriority(request.getPriority());
        if (request.getActive() != null)
            existing.setActive(request.getActive());
        if (request.getNote() != null)
            existing.setNote(request.getNote());

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
}

