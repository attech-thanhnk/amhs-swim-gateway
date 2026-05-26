// ================================
// MessageLogController.java
// ================================

package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.MessageLog;
import vn.asg.cp.service.MessageLogService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/message-logs")
@RequiredArgsConstructor
@CrossOrigin
public class MessageLogController {

    private final MessageLogService service;

    // =====================================
    // GET ALL WITH PAGINATION
    // =====================================

    @GetMapping
    public ResponseEntity<Page<MessageLog>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(service.getAll(pageable));
    }

    // =====================================
    // GET LATEST LOGS
    // =====================================

    @GetMapping("/latest")
    public ResponseEntity<List<MessageLog>> getLatestLogs() {
        return ResponseEntity.ok(service.getLatestLogs());
    }

    // =====================================
    // GET BY ID
    // =====================================

    @GetMapping("/{id}")
    public ResponseEntity<MessageLog> getById(
            @PathVariable Long id) {

        MessageLog log = service.getById(id);

        if (log == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(log);
    }

    // =====================================
    // FILTER BY STATUS
    // =====================================

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<MessageLog>> getByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                service.getByStatus(status, pageable));
    }

    // =====================================
    // FILTER BY DIRECTION
    // =====================================

    @GetMapping("/direction/{direction}")
    public ResponseEntity<Page<MessageLog>> getByDirection(
            @PathVariable String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                service.getByDirection(direction, pageable));
    }

    // =====================================
    // FILTER BY MESSAGE TYPE
    // =====================================

    @GetMapping("/type/{type}")
    public ResponseEntity<Page<MessageLog>> getByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                service.getByMessageType(type, pageable));
    }

    // =====================================
    // FILTER BY DATE RANGE
    // =====================================

    @GetMapping("/date-range")
    public ResponseEntity<Page<MessageLog>> getByDateRange(

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size) {

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                service.getByDateRange(from, to, pageable));
    }
}
