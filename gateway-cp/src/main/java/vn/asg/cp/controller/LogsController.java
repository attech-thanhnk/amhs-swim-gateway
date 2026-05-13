package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.SystemLog;
import vn.asg.cp.repository.SystemLogRepository;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Controller quản lý System Logs với khả năng lọc linh hoạt (Specification).
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogsController {

    private final SystemLogRepository logRepository;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(name = "level", defaultValue = "ALL") String level,
            @RequestParam(name = "module", defaultValue = "ALL") String module,
            @RequestParam(name = "after", required = false) String after,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size) {

        // Xây dựng Specification động
        Specification<SystemLog> spec = Specification.where(null);

        if (StringUtils.hasText(after)) {
            LocalDateTime afterDt = LocalDateTime.parse(after);
            spec = spec.and((r, q, cb) -> cb.greaterThan(r.get("timestamp"), afterDt));
        }

        if (!"ALL".equalsIgnoreCase(level)) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("level"), level));
        }

        if (!"ALL".equalsIgnoreCase(module)) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("module"), module));
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<SystemLog> result = logRepository.findAll(spec, pageRequest);

        LocalDateTime latestTs = result.getContent().stream()
                .map(SystemLog::getTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "latestTimestamp", latestTs != null ? latestTs.toString() : "",
                "totalElements", result.getTotalElements()));
    }
}
