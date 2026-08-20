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
import vn.asg.cp.entity.Gwin;
import vn.asg.cp.entity.Gwout;
import vn.asg.cp.entity.MessageStatus;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.repository.GwinDispatchRepository;
import vn.asg.cp.repository.GwinRepository;
import vn.asg.cp.repository.GwoutDispatchRepository;
import vn.asg.cp.repository.GwoutRepository;

import java.time.LocalDateTime;
import java.util.List;
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
    public ResponseEntity<ApiResponse<PageData<Gwin>>> getInboundMessages(
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
        return ResponseEntity.ok(ApiResponse.ok(PageData.from(result)));
    }

    @GetMapping("/inbound/{msgid}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInboundMessage(@PathVariable("msgid") Long msgid) {
        Gwin msg = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound message", msgid));

        Map<String, Object> result = Map.of(
                "message", msg,
                "dispatches", gwinDispatchRepository.findByGwinId(msgid));

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/outbound")
    public ResponseEntity<ApiResponse<PageData<Map<String, Object>>>> getOutboundMessages(
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
        
        List<Map<String, Object>> contentList = new java.util.ArrayList<>();
        for (Gwout g : result.getContent()) {
            try {
                Map<String, Object> m = new java.util.HashMap<>();
                m.put("msgid", g.getMsgid());
                m.put("amhsid", g.getAmhsid());
                m.put("amhsPriority", g.getAmhsPriority());
                m.put("time", g.getTime() != null ? g.getTime().toString() : null);
                m.put("filingTime", g.getFilingTime());
                m.put("text", g.getText());
                m.put("bodyType", g.getBodyType());
                m.put("origin", g.getOrigin());
                m.put("address", g.getAddress());
                m.put("optionalHeading", g.getOptionalHeading());
                m.put("subject", g.getSubject());
                m.put("amhsTtl", g.getAmhsTtl() != null ? g.getAmhsTtl().toString() : null);
                m.put("amhsRegisteredId", g.getAmhsRegisteredId());
                m.put("ipmId", g.getIpmId());
                m.put("swimPriority", g.getSwimPriority());
                m.put("amqpMessageId", g.getAmqpMessageId());
                m.put("bodyPartType", g.getBodyPartType());
                m.put("bodyPartCharset", g.getBodyPartCharset());
                m.put("ftbpFileName", g.getFtbpFileName());
                m.put("ftbpObjectSize", g.getFtbpObjectSize());
                m.put("ftbpLastMod", g.getFtbpLastMod());
                m.put("messageSigned", g.getMessageSigned());
                m.put("rejectionReason", g.getRejectionReason());
                m.put("rejectionDiagnostic", g.getRejectionDiagnostic());
                m.put("amhsDeliveryReport", g.getAmhsDeliveryReport());
                m.put("contentType", g.getContentType());
                m.put("status", g.getStatus());
                contentList.add(m);
            } catch (Exception e) {
                org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MessagesController.class);
                logger.error("CRITICAL: Error mapping Gwout msgid: {}, error: {}", g.getMsgid(), e.getMessage(), e);
                throw e;
            }
        }

        PageData<Map<String, Object>> pageData = PageData.of(contentList, page, size, result.getTotalElements(), result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.ok(pageData));
    }

    @GetMapping("/outbound/{msgid}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOutboundMessage(@PathVariable("msgid") Long msgid) {
        Gwout msg = gwoutRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Outbound message", msgid));

        Map<String, Object> result = Map.of(
                "message", msg,
                "dispatches", gwoutDispatchRepository.findByGwoutId(msgid));

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/inbound/{msgid}/retry")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryInbound(@PathVariable("msgid") Long msgid) {
        Gwin msg = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound message", msgid));

        msg.setStatus(MessageStatus.IN_PENDING.getValue());
        gwinRepository.save(msg);

        return ResponseEntity.ok(ApiResponse.ok("Queued for retry", Map.of("success", true, "msgid", msgid, "message", "Queued for retry")));
    }

    @PostMapping("/inbound/{msgid}/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolveInbound(@PathVariable("msgid") Long msgid) {
        Gwin msg = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound message", msgid));

        msg.setStatus(MessageStatus.IN_RESOLVED.getValue());
        gwinRepository.save(msg);

        gwinDispatchRepository.findByGwinId(msgid).forEach(d -> {
            d.setStatus("DEAD");
            gwinDispatchRepository.save(d);
        });

        return ResponseEntity.ok(ApiResponse.ok("Marked as resolved", Map.of("success", true, "msgid", msgid, "message", "Marked as resolved")));
    }

    @PostMapping("/inbound/{msgid}/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelInbound(@PathVariable("msgid") Long msgid) {
        Gwin msg = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound message", msgid));

        msg.setStatus(MessageStatus.IN_CANCELLED.getValue());
        gwinRepository.save(msg);

        gwinDispatchRepository.findByGwinId(msgid).forEach(d -> {
            d.setStatus("DEAD");
            gwinDispatchRepository.save(d);
        });

        return ResponseEntity.ok(ApiResponse.ok("Marked as cancelled", Map.of("success", true, "msgid", msgid, "message", "Marked as cancelled")));
    }

    @PostMapping("/outbound/{msgid}/retry")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryOutbound(@PathVariable("msgid") Long msgid) {
        Gwout msg = gwoutRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Outbound message", msgid));

        msg.setStatus(MessageStatus.OUT_PENDING.getValue());
        gwoutRepository.save(msg);

        return ResponseEntity.ok(ApiResponse.ok("Queued for retry", Map.of("success", true, "msgid", msgid, "message", "Queued for retry")));
    }

    @PostMapping("/outbound/{msgid}/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolveOutbound(@PathVariable("msgid") Long msgid) {
        Gwout msg = gwoutRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Outbound message", msgid));

        msg.setStatus(MessageStatus.OUT_RESOLVED.getValue());
        gwoutRepository.save(msg);

        gwoutDispatchRepository.findByGwoutId(msgid).forEach(d -> {
            d.setStatus("DEAD");
            gwoutDispatchRepository.save(d);
        });

        return ResponseEntity.ok(ApiResponse.ok("Marked as resolved", Map.of("success", true, "msgid", msgid, "message", "Marked as resolved")));
    }

    @PostMapping("/outbound/{msgid}/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelOutbound(@PathVariable("msgid") Long msgid) {
        Gwout msg = gwoutRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Outbound message", msgid));

        msg.setStatus(MessageStatus.OUT_CANCELLED.getValue());
        gwoutRepository.save(msg);

        gwoutDispatchRepository.findByGwoutId(msgid).forEach(d -> {
            d.setStatus("DEAD");
            gwoutDispatchRepository.save(d);
        });

        return ResponseEntity.ok(ApiResponse.ok("Marked as cancelled", Map.of("success", true, "msgid", msgid, "message", "Marked as cancelled")));
    }

    @DeleteMapping("/inbound/{msgid}")
    public ResponseEntity<ApiResponse<Void>> deleteInbound(@PathVariable("msgid") Long msgid) {
        if (!gwinRepository.existsById(msgid)) {
            throw new ResourceNotFoundException("Inbound message", msgid);
        }
        gwinRepository.deleteById(msgid);
        return ResponseEntity.ok(ApiResponse.ok("Inbound message deleted", null));
    }

    @DeleteMapping("/outbound/{msgid}")
    public ResponseEntity<ApiResponse<Void>> deleteOutbound(@PathVariable("msgid") Long msgid) {
        if (!gwoutRepository.existsById(msgid)) {
            throw new ResourceNotFoundException("Outbound message", msgid);
        }
        gwoutRepository.deleteById(msgid);
        return ResponseEntity.ok(ApiResponse.ok("Outbound message deleted", null));
    }
}

