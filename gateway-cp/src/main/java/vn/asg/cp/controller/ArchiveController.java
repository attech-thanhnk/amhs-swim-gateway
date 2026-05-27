package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.MessageArchive;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.repository.MessageArchiveRepository;

import java.util.Map;

/**
 * GET /api/archive — xem lại điện văn raw lưu trữ
 */
@RestController
@RequestMapping("/api/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final MessageArchiveRepository archiveRepository;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(name = "direction", defaultValue = "ALL") String direction,
            @RequestParam(name = "searchType", defaultValue = "AMQP_ID") String searchType,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        Specification<MessageArchive> spec = Specification.where(null);

        if (!"ALL".equals(direction)) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("direction"), direction));
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search + "%";
            spec = spec.and((r, q, cb) -> switch (searchType) {
                case "MTS_ID" -> cb.like(r.get("mtsId"), pattern);
                case "IPM_ID" -> cb.like(r.get("ipmId"), pattern);
                case "MSG_ID" -> cb.like(r.get("msgId"), pattern);
                default -> cb.like(r.get("amqpMessageId"), pattern); // AMQP_ID
            });
        }

        Page<MessageArchive> result = archiveRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("timestamp").descending()));

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()));
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<MessageArchive> getOne(@PathVariable("uuid") String uuid) {
        MessageArchive archive = archiveRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Archive", uuid));
        return ResponseEntity.ok(archive);
    }
}
