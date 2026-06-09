package vn.asg.cp.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.SystemHistory;
import vn.asg.cp.dto.CreateHistoryRequest;
import vn.asg.cp.service.SystemHistoryService;

@RestController
@RequestMapping("/api/system-history")
@RequiredArgsConstructor
public class SystemHistoryController {

    private final SystemHistoryService service;

    @PostMapping
    public ResponseEntity<SystemHistory> create(
            @RequestBody CreateHistoryRequest request) {

        // return ResponseEntity.ok(service.create(request));
        return null;
    }

    @GetMapping
    public ResponseEntity<Page<SystemHistory>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<SystemHistory> histories = service.getHistories(page, size);
        return ResponseEntity.ok(histories);
    }
}
