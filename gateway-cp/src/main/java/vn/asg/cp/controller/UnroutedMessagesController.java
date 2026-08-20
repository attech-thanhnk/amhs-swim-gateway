package vn.asg.cp.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.*;
import vn.asg.cp.entity.Gwin;
import vn.asg.cp.service.UnroutedMessageService;

import java.time.LocalDateTime;

/**
 * REST Controller cho UNROUTED Messages Management.
 */
@RestController
@RequestMapping("/api/addressing/unrouted")
@RequiredArgsConstructor
public class UnroutedMessagesController {

    private final UnroutedMessageService unroutedMessageService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageData<Gwin>>> getAll(
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

        return ResponseEntity.ok(ApiResponse.ok(PageData.from(result)));
    }

    @GetMapping("/{msgid}")
    public ResponseEntity<ApiResponse<Gwin>> getById(@PathVariable("msgid") Long msgid) {
        return unroutedMessageService.getUnroutedMessageById(msgid)
                .map(msg -> ResponseEntity.ok(ApiResponse.ok(msg)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Unrouted message not found")));
    }

    @PostMapping("/{msgid}/route")
    public ResponseEntity<ApiResponse<Gwin>> manualRoute(@PathVariable("msgid") Long msgid,
            @Valid @RequestBody ManualRouteRequest request) {
        Gwin routed = unroutedMessageService.manuallyRoute(msgid, request);
        return ResponseEntity.ok(ApiResponse.ok("Message manually routed successfully", routed));
    }

    @PostMapping("/{msgid}/reject")
    public ResponseEntity<ApiResponse<Gwin>> reject(@PathVariable("msgid") Long msgid,
            @Valid @RequestBody RejectMessageRequest request) {
        Gwin rejected = unroutedMessageService.rejectMessage(msgid, request);
        return ResponseEntity.ok(ApiResponse.ok("Message rejected successfully", rejected));
    }

    @PostMapping("/batch-route")
    public ResponseEntity<ApiResponse<BatchOperationResponse>> batchRoute(@Valid @RequestBody BatchRouteRequest request) {
        BatchOperationResponse response = unroutedMessageService.batchRoute(request);
        return ResponseEntity.ok(ApiResponse.ok("Batch routing operation executed", response));
    }
}

