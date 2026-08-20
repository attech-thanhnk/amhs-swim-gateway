package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.ApiResponse;
import vn.asg.cp.dto.PageData;
import vn.asg.cp.entity.MessageConversionLog;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.repository.MessageConversionLogRepository;

import java.time.LocalDateTime;

/**
 * GET /api/traffic-logs — tra cứu lịch sử điện văn
 */
@RestController
@RequestMapping("/api/traffic-logs")
@RequiredArgsConstructor
public class TrafficLogsController {

    private final MessageConversionLogRepository logRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<PageData<MessageConversionLog>>> list(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "direction", defaultValue = "ALL") String direction,
            @RequestParam(name = "status", defaultValue = "ALL") String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        Specification<MessageConversionLog> spec = Specification.where(null);

        LocalDateTime fromDt = parseDateTime(from);
        if (fromDt != null) {
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("convertedTime"), fromDt));
        }

        LocalDateTime toDt = parseDateTime(to);
        if (toDt != null) {
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("convertedTime"), toDt));
        }

        if (!"ALL".equals(direction)) {
            if ("AMHS_TO_SWIM".equals(direction)) {
                spec = spec.and((r, q, cb) -> cb.and(
                        cb.equal(r.get("type"), "AMHS"),
                        cb.equal(r.get("category"), "OUT")));
            } else if ("SWIM_TO_AMHS".equals(direction)) {
                spec = spec.and((r, q, cb) -> cb.and(
                        cb.equal(r.get("type"), "SWIM"),
                        cb.equal(r.get("category"), "IN")));
            }
        }
        if (!"ALL".equals(status)) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }

        Page<MessageConversionLog> result = logRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("convertedTime").descending()));

        return ResponseEntity.ok(ApiResponse.ok(PageData.from(result)));
    }

    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(str.trim());
        } catch (Exception e) {
            try {
                return java.time.LocalDate.parse(str.trim()).atStartOfDay();
            } catch (Exception ex) {
                return null;
            }
        }
    }


    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MessageConversionLog>> getOne(@PathVariable("id") Long id) {
        MessageConversionLog log = logRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Traffic log", id));
        return ResponseEntity.ok(ApiResponse.ok(log));
    }
}

