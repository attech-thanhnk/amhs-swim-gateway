package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.SystemLog;
import vn.asg.cp.repository.SystemLogRepository;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * GET /api/logs — xem log hệ thống real-time (polling)
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

        Page<SystemLog> result;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());

        if (after != null) {
            LocalDateTime afterDt = LocalDateTime.parse(after);
            if (!"ALL".equals(level) && !"ALL".equals(module)) {
                result = logRepository.findByLevelAndModuleAndTimestampAfter(level, module, afterDt, pageRequest);
            } else {
                result = logRepository.findByTimestampAfter(afterDt, pageRequest);
            }
        } else {
            if (!"ALL".equals(level) || !"ALL".equals(module)) {
                result = logRepository.findByLevelContainingAndModuleContaining(
                        "ALL".equals(level) ? "" : level,
                        "ALL".equals(module) ? "" : module,
                        pageRequest);
            } else {
                result = logRepository.findAll(pageRequest);
            }
        }

        LocalDateTime latestTs = result.getContent().stream()
                .map(SystemLog::getTimestamp)
                .filter(ts -> ts != null)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "latestTimestamp", latestTs.toString(),
                "totalElements", result.getTotalElements()));
    }
}
