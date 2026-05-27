package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.MessageConversionLog;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.repository.MessageConversionLogRepository;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * GET /api/traffic-logs — tra cứu lịch sử điện văn
 */
@RestController
@RequestMapping("/api/traffic-logs")
@RequiredArgsConstructor
public class TrafficLogsController {

    private final MessageConversionLogRepository logRepository;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "direction", defaultValue = "ALL") String direction,
            @RequestParam(name = "status", defaultValue = "ALL") String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        Specification<MessageConversionLog> spec = Specification.where(null);

        if (from != null) {
            LocalDateTime fromDt = LocalDateTime.parse(from);
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("convertedTime"), fromDt));
        }
        if (to != null) {
            LocalDateTime toDt = LocalDateTime.parse(to);
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("convertedTime"), toDt));
        }
        if (!"ALL".equals(direction)) {
            // direction = AMHS_TO_SWIM → type=AMHS,category=OUT; SWIM_TO_AMHS →
            // type=SWIM,category=IN
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

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MessageConversionLog> getOne(@PathVariable("id") Long id) {
        MessageConversionLog log = logRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Traffic log", id));
        return ResponseEntity.ok(log);
    }
}
