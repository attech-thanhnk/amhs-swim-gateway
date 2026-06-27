package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.GwinHistory;
import vn.asg.cp.entity.GwoutHistory;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.repository.GwinHistoryRepository;
import vn.asg.cp.repository.GwoutDispatchHistoryRepository;
import vn.asg.cp.repository.GwoutHistoryRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Controller to handle queries for historical (archived) messages.
 */
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final GwinHistoryRepository gwinHistoryRepository;
    private final GwoutHistoryRepository gwoutHistoryRepository;
    private final GwoutDispatchHistoryRepository gwoutDispatchHistoryRepository;

    @GetMapping("/inbound")
    public ResponseEntity<?> getInboundHistory(
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "origin", required = false) String origin,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "fromTime", required = false) String fromTime,
            @RequestParam(name = "toTime", required = false) String toTime,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        Specification<GwinHistory> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (origin != null && !origin.trim().isEmpty()) {
            spec = spec.and((r, q, cb) -> cb.like(r.get("origin"), origin.trim() + "%"));
        }
        if (source != null && !source.trim().isEmpty()) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("source"), source.trim()));
        }
        if (fromTime != null && !fromTime.trim().isEmpty()) {
            LocalDateTime from = parseDateTime(fromTime);
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("time"), from));
        }
        if (toTime != null && !toTime.trim().isEmpty()) {
            LocalDateTime to = parseDateTime(toTime);
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("time"), to));
        }

        Page<GwinHistory> result = gwinHistoryRepository.findAll(spec, PageRequest.of(page, size, Sort.by("time").descending()));
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", page));
    }

    @GetMapping("/inbound/{msgid}")
    public ResponseEntity<?> getInboundHistoryDetails(@PathVariable("msgid") Long msgid) {
        GwinHistory msg = gwinHistoryRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound history message", msgid));
        return ResponseEntity.ok(msg);
    }

    @GetMapping("/outbound")
    public ResponseEntity<?> getOutboundHistory(
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "origin", required = false) String origin,
            @RequestParam(name = "recipient", required = false) String recipient,
            @RequestParam(name = "fromTime", required = false) String fromTime,
            @RequestParam(name = "toTime", required = false) String toTime,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        Specification<GwoutHistory> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (origin != null && !origin.trim().isEmpty()) {
            spec = spec.and((r, q, cb) -> cb.like(r.get("origin"), origin.trim() + "%"));
        }
        if (recipient != null && !recipient.trim().isEmpty()) {
            spec = spec.and((r, q, cb) -> cb.like(r.get("address"), "%" + recipient.trim() + "%"));
        }
        if (fromTime != null && !fromTime.trim().isEmpty()) {
            LocalDateTime from = parseDateTime(fromTime);
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("time"), from));
        }
        if (toTime != null && !toTime.trim().isEmpty()) {
            LocalDateTime to = parseDateTime(toTime);
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("time"), to));
        }

        Page<GwoutHistory> result = gwoutHistoryRepository.findAll(spec, PageRequest.of(page, size, Sort.by("time").descending()));
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", page));
    }

    @GetMapping("/outbound/{msgid}")
    public ResponseEntity<?> getOutboundHistoryDetails(@PathVariable("msgid") Long msgid) {
        GwoutHistory msg = gwoutHistoryRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Outbound history message", msgid));
        return ResponseEntity.ok(Map.of(
                "message", msg,
                "dispatches", gwoutDispatchHistoryRepository.findByGwoutId(msgid)));
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            if (value.contains("T")) {
                return LocalDateTime.parse(value);
            } else if (value.contains(" ")) {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                return LocalDateTime.parse(value + "T00:00:00");
            }
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
