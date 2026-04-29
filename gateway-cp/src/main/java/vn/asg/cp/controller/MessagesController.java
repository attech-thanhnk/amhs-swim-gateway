package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.Gwin;
import vn.asg.cp.entity.Gwout;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.repository.GwinDispatchRepository;
import vn.asg.cp.repository.GwinRepository;
import vn.asg.cp.repository.GwoutDispatchRepository;
import vn.asg.cp.repository.GwoutRepository;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessagesController {

    private final GwinRepository gwinRepository;
    private final GwoutRepository gwoutRepository;
    private final GwinDispatchRepository gwinDispatchRepository;
    private final GwoutDispatchRepository gwoutDispatchRepository;

    @GetMapping("/inbound")
    public ResponseEntity<?> getInboundMessages(
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "fromTime", required = false) String fromTime,
            @RequestParam(name = "toTime", required = false) String toTime,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        Specification<Gwin> spec = Specification.where(null);
        if (status != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (source != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("source"), source));
        if (fromTime != null)
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("time"), LocalDateTime.parse(fromTime)));
        if (toTime != null)
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("time"), LocalDateTime.parse(toTime)));

        Page<Gwin> result = gwinRepository.findAll(spec, PageRequest.of(page, size, Sort.by("time").descending()));
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", page));
    }

    @GetMapping("/inbound/{msgid}")
    public ResponseEntity<Map<String, Object>> getInboundMessage(@PathVariable("msgid") Long msgid) {
        Gwin msg = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound message", msgid));

        Map<String, Object> result = Map.of(
                "message", msg,
                "dispatches", gwinDispatchRepository.findByGwinId(msgid));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/outbound")
    public ResponseEntity<?> getOutboundMessages(
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "fromTime", required = false) String fromTime,
            @RequestParam(name = "toTime", required = false) String toTime,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        Specification<Gwout> spec = Specification.where(null);
        if (status != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (fromTime != null)
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("time"), LocalDateTime.parse(fromTime)));
        if (toTime != null)
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("time"), LocalDateTime.parse(toTime)));

        Page<Gwout> result = gwoutRepository.findAll(spec, PageRequest.of(page, size, Sort.by("time").descending()));
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", page));
    }

    @GetMapping("/outbound/{msgid}")
    public ResponseEntity<Map<String, Object>> getOutboundMessage(@PathVariable("msgid") Long msgid) {
        Gwout msg = gwoutRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Outbound message", msgid));

        Map<String, Object> result = Map.of(
                "message", msg,
                "dispatches", gwoutDispatchRepository.findByGwoutId(msgid));

        return ResponseEntity.ok(result);
    }

    @PostMapping("/inbound/{msgid}/retry")
    public ResponseEntity<Map<String, Object>> retryInbound(@PathVariable("msgid") Long msgid) {
        Gwin msg = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound message", msgid));

        msg.setStatus(0); // 0 = PENDING for retry
        gwinRepository.save(msg);

        return ResponseEntity.ok(Map.of("success", true, "msgid", msgid, "message", "Queued for retry"));
    }

    @PostMapping("/outbound/{msgid}/retry")
    public ResponseEntity<Map<String, Object>> retryOutbound(@PathVariable("msgid") Long msgid) {
        Gwout msg = gwoutRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Outbound message", msgid));

        msg.setStatus(0); // 0 = PENDING for retry
        gwoutRepository.save(msg);

        return ResponseEntity.ok(Map.of("success", true, "msgid", msgid, "message", "Queued for retry"));
    }

    @DeleteMapping("/inbound/{msgid}")
    public ResponseEntity<Void> deleteInbound(@PathVariable("msgid") Long msgid) {
        if (!gwinRepository.existsById(msgid)) {
            throw new ResourceNotFoundException("Inbound message", msgid);
        }
        gwinRepository.deleteById(msgid);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/outbound/{msgid}")
    public ResponseEntity<Void> deleteOutbound(@PathVariable("msgid") Long msgid) {
        if (!gwoutRepository.existsById(msgid)) {
            throw new ResourceNotFoundException("Outbound message", msgid);
        }
        gwoutRepository.deleteById(msgid);
        return ResponseEntity.noContent().build();
    }
}
