package vn.asg.cp.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.*;
import vn.asg.cp.entity.Gwin;
import vn.asg.cp.service.UnroutedMessageService;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST Controller cho UNROUTED Messages Management.
 */
@RestController
@RequestMapping("/api/addressing/unrouted")
@RequiredArgsConstructor
public class UnroutedMessagesController {

    private final UnroutedMessageService unroutedMessageService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "time,desc") String sort) {

        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        Sort.Direction sortDirection = sortParts.length > 1 && "desc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortField));
        Page<Gwin> result = unroutedMessageService.getUnroutedMessages(fromTime, toTime, source, pageable);
        long totalUnrouted = unroutedMessageService.getUnroutedCount();

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "currentPage", result.getNumber(),
                "size", result.getSize(),
                "statistics", Map.of("totalUnrouted", totalUnrouted)));
    }

    @GetMapping("/{msgid}")
    public ResponseEntity<Gwin> getById(@PathVariable("msgid") Long msgid) {
        return unroutedMessageService.getUnroutedMessageById(msgid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{msgid}/route")
    public ResponseEntity<Gwin> manualRoute(@PathVariable("msgid") Long msgid,
            @Valid @RequestBody ManualRouteRequest request) {
        Gwin routed = unroutedMessageService.manuallyRoute(msgid, request);
        return ResponseEntity.ok(routed);
    }

    @PostMapping("/{msgid}/reject")
    public ResponseEntity<Gwin> reject(@PathVariable("msgid") Long msgid,
            @Valid @RequestBody RejectMessageRequest request) {
        Gwin rejected = unroutedMessageService.rejectMessage(msgid, request);
        return ResponseEntity.ok(rejected);
    }

    @PostMapping("/batch-route")
    public ResponseEntity<BatchOperationResponse> batchRoute(@Valid @RequestBody BatchRouteRequest request) {
        BatchOperationResponse response = unroutedMessageService.batchRoute(request);
        return ResponseEntity.ok(response);
    }
}
