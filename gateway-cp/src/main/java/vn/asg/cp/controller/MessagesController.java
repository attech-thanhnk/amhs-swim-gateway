package vn.asg.cp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import vn.asg.cp.entity.InboundStatus;
import vn.asg.cp.entity.OutboundStatus;
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
    private final ObjectMapper objectMapper;

    @GetMapping("/inbound")
    public ResponseEntity<ApiResponse<PageData<Map<String, Object>>>> getInboundMessages(
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "fromTime", required = false) String fromTime,
            @RequestParam(name = "toTime", required = false) String toTime,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        Specification<Gwin> spec = Specification.where(null);
        if (status != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (source != null && !source.trim().isEmpty())
            spec = spec.and((r, q, cb) -> cb.equal(r.get("source"), source.trim()));
        if (fromTime != null && !fromTime.trim().isEmpty()) {
            LocalDateTime from = parseDateTime(fromTime);
            if (from != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("time"), from));
        }
        if (toTime != null && !toTime.trim().isEmpty()) {
            LocalDateTime to = parseDateTime(toTime);
            if (to != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("time"), to));
        }
        if (query != null && !query.trim().isEmpty()) {
            String kw = "%" + query.trim().toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> cb.or(
                    cb.like(cb.lower(r.get("origin")), kw),
                    cb.like(cb.lower(r.get("address")), kw),
                    cb.like(cb.lower(r.get("amhsRecipients")), kw),
                    cb.like(cb.lower(r.get("messageId")), kw),
                    cb.like(cb.lower(r.get("subject")), kw),
                    cb.like(cb.lower(r.get("payloadContent")), kw),
                    cb.like(cb.lower(r.get("amqpProperties")), kw)
            ));
        }

        Page<Gwin> result = gwinRepository.findAll(spec, PageRequest.of(page, size, Sort.by("time").descending()));

        List<Map<String, Object>> contentList = new java.util.ArrayList<>();
        for (Gwin g : result.getContent()) {
            contentList.add(mapGwinToDetail(g));
        }

        PageData<Map<String, Object>> pageData = PageData.of(contentList, page, size, result.getTotalElements(), result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.ok(pageData));
    }

    @GetMapping("/inbound/{msgid}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInboundMessage(@PathVariable("msgid") Long msgid) {
        Gwin msg = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound message", msgid));

        Map<String, Object> msgDetail = mapGwinToDetail(msg);

        Map<String, Object> result = Map.of(
                "message", msgDetail,
                "dispatches", gwinDispatchRepository.findByGwinId(msgid));

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapGwinToDetail(Gwin g) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("msgid", g.getMsgid());
        m.put("messageId", g.getMessageId());
        m.put("source", g.getSource());
        m.put("subject", g.getSubject());
        m.put("priority", g.getPriority());
        m.put("amhsRecipients", g.getAmhsRecipients());
        m.put("time", g.getTime() != null ? g.getTime().toString() : null);
        m.put("payloadContent", g.getPayloadContent());
        m.put("bodyType", g.getBodyType());
        m.put("contentType", g.getContentType());
        m.put("origin", g.getOrigin());
        m.put("address", g.getAddress());
        m.put("addressingSource", g.getAddressingSource());
        m.put("atsmhsServiceLevel", g.getAtsmhsServiceLevel());
        m.put("status", g.getStatus());
        Map<String, Object> parsedProps = new java.util.HashMap<>();
        if (g.getAmqpProperties() != null && !g.getAmqpProperties().isBlank()) {
            try {
                parsedProps = objectMapper.readValue(g.getAmqpProperties(), Map.class);
            } catch (Exception ignored) {}
        }
        m.put("amqpProperties", g.getAmqpProperties());
        m.put("parsedAmqpProperties", parsedProps);

        String rejReason = g.getRejectionReason();
        if ((rejReason == null || rejReason.isBlank()) && parsedProps != null) {
            rejReason = (String) parsedProps.get("rejection_reason");
            if (rejReason == null || rejReason.isBlank()) {
                rejReason = (String) parsedProps.get("rejectionReason");
            }
        }
        m.put("rejectionReason", rejReason);

        String rejDiag = g.getRejectionDiagnostic();
        if ((rejDiag == null || rejDiag.isBlank()) && parsedProps != null) {
            rejDiag = (String) parsedProps.get("rejection_note");
            if (rejDiag == null || rejDiag.isBlank()) {
                rejDiag = (String) parsedProps.get("rejection_diagnostic");
            }
            if (rejDiag == null || rejDiag.isBlank()) {
                rejDiag = (String) parsedProps.get("rejectionDiagnostic");
            }
        }
        m.put("rejectionDiagnostic", rejDiag);
        String rejSrc = g.getRejectionSource() != null ? g.getRejectionSource() : (rejReason != null ? "SWIM" : null);
        m.put("rejectionSource", rejSrc);
        m.put("errorSource", rejSrc);

        String ft = (String) parsedProps.get("amhs_ats_ft");
        if (ft == null || ft.isBlank()) {
            ft = (String) parsedProps.get("creation-time");
        }
        if (ft == null || ft.isBlank()) {
            ft = (String) parsedProps.get("creation_time");
        }
        m.put("filingTime", (ft != null && !ft.isBlank()) ? ft : "-");
        m.put("amhs_ats_ft", ft);

        String atsPri = (String) parsedProps.get("ats_priority");
        if (atsPri == null || atsPri.isBlank()) {
            atsPri = (String) parsedProps.get("amhs_ats_pri");
        }
        m.put("atsPriority", atsPri);
        m.put("amhs_ats_pri", atsPri);

        String ohi = (String) parsedProps.get("amhs_ats_ohi");
        m.put("optionalHeading", ohi);
        m.put("amhs_ats_ohi", ohi);

        String ipmId = (String) parsedProps.get("amhs_ipm_id");
        m.put("ipmId", ipmId);
        m.put("amhs_ipm_id", ipmId);

        String bodypartType = (String) parsedProps.get("amhs_bodypart_type");
        m.put("bodyPartType", bodypartType != null ? bodypartType : g.getBodyType());

        String ftbpFileName = (String) parsedProps.get("amhs_ftbp_file_name");
        m.put("ftbpFileName", ftbpFileName);

        Object ftbpObjectSize = parsedProps.get("amhs_ftbp_object_size");
        m.put("ftbpObjectSize", ftbpObjectSize != null ? String.valueOf(ftbpObjectSize) : null);

        String ftbpLastMod = (String) parsedProps.get("amhs_ftbp_last_mod");
        m.put("ftbpLastMod", ftbpLastMod);

        return m;
    }

    @GetMapping("/outbound")
    public ResponseEntity<ApiResponse<PageData<Map<String, Object>>>> getOutboundMessages(
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "fromTime", required = false) String fromTime,
            @RequestParam(name = "toTime", required = false) String toTime,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {

        Specification<Gwout> spec = Specification.where(null);
        if (status != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (fromTime != null && !fromTime.trim().isEmpty()) {
            LocalDateTime from = parseDateTime(fromTime);
            if (from != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("time"), from));
        }
        if (toTime != null && !toTime.trim().isEmpty()) {
            LocalDateTime to = parseDateTime(toTime);
            if (to != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("time"), to));
        }
        if (query != null && !query.trim().isEmpty()) {
            String kw = "%" + query.trim().toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> cb.or(
                    cb.like(cb.lower(r.get("origin")), kw),
                    cb.like(cb.lower(r.get("address")), kw),
                    cb.like(cb.lower(r.get("amhsid")), kw),
                    cb.like(cb.lower(r.get("ipmId")), kw),
                    cb.like(cb.lower(r.get("amqpMessageId")), kw),
                    cb.like(cb.lower(r.get("subject")), kw),
                    cb.like(cb.lower(r.get("text")), kw),
                    cb.like(cb.lower(r.get("ftbpFileName")), kw)
            ));
        }

        Page<Gwout> result = gwoutRepository.findAll(spec, PageRequest.of(page, size, Sort.by("time").descending()));

        List<Map<String, Object>> contentList = new java.util.ArrayList<>();
        for (Gwout g : result.getContent()) {
            contentList.add(mapGwoutToDetail(g));
        }

        PageData<Map<String, Object>> pageData = PageData.of(contentList, page, size, result.getTotalElements(), result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.ok(pageData));
    }

    @GetMapping("/outbound/{msgid}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOutboundMessage(@PathVariable("msgid") Long msgid) {
        Gwout msg = gwoutRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Outbound message", msgid));

        Map<String, Object> msgDetail = mapGwoutToDetail(msg);

        Map<String, Object> result = Map.of(
                "message", msgDetail,
                "dispatches", gwoutDispatchRepository.findByGwoutId(msgid));

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private Map<String, Object> mapGwoutToDetail(Gwout g) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("msgid", g.getMsgid());
        m.put("amhsid", g.getAmhsid());
        m.put("amhsPriority", g.getAmhsPriority());
        m.put("atsPriority", g.getAmhsPriority());
        m.put("amhs_ats_pri", g.getAmhsPriority());
        m.put("time", g.getTime() != null ? g.getTime().toString() : null);
        m.put("filingTime", g.getFilingTime() != null ? g.getFilingTime() : "-");
        m.put("amhs_ats_ft", g.getFilingTime());
        m.put("text", g.getText());
        m.put("payloadContent", g.getText());
        m.put("bodyType", g.getBodyType());
        m.put("origin", g.getOrigin());
        m.put("address", g.getAddress());
        m.put("optionalHeading", g.getOptionalHeading());
        m.put("amhs_ats_ohi", g.getOptionalHeading());
        m.put("subject", g.getSubject());
        m.put("amhsTtl", g.getAmhsTtl() != null ? g.getAmhsTtl().toString() : null);
        m.put("amhsRegisteredId", g.getAmhsRegisteredId());
        m.put("ipmId", g.getIpmId());
        m.put("amhs_ipm_id", g.getIpmId());
        m.put("swimPriority", g.getSwimPriority());
        m.put("amqpMessageId", g.getAmqpMessageId());
        m.put("messageId", g.getAmqpMessageId());
        m.put("bodyPartType", g.getBodyPartType());
        m.put("bodyPartCharset", g.getBodyPartCharset());
        m.put("ftbpFileName", g.getFtbpFileName());
        m.put("ftbpObjectSize", g.getFtbpObjectSize());
        m.put("ftbpLastMod", g.getFtbpLastMod());
        m.put("messageSigned", g.getMessageSigned());
        m.put("rejectionReason", g.getRejectionReason());
        m.put("rejectionDiagnostic", g.getRejectionDiagnostic());
        String rejSrcOut = g.getRejectionSource() != null ? g.getRejectionSource() : (g.getRejectionReason() != null ? "AMHS" : null);
        m.put("rejectionSource", rejSrcOut);
        m.put("errorSource", rejSrcOut);
        m.put("amhsDeliveryReport", g.getAmhsDeliveryReport());
        m.put("contentType", g.getContentType());
        // Dữ liệu gốc từ MTE/IPM làm căn cứ cho các NDR §4.4.1.1 / §4.4.2.1 / §4.4.2.2,
        // Control Position cần thấy được để đối chiếu khi nghiệm thu (CTSW008/016/007).
        m.put("x400ContentType", g.getX400ContentType());
        m.put("originEit", g.getOriginEit());
        m.put("numberOfAttachment", g.getNumberOfAttachment());
        m.put("status", g.getStatus());
        return m;
    }

    @PostMapping("/inbound/{msgid}/retry")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryInbound(@PathVariable("msgid") Long msgid) {
        Gwin msg = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound message", msgid));

        msg.setStatus(InboundStatus.PENDING.getValue());
        gwinRepository.save(msg);

        return ResponseEntity.ok(ApiResponse.ok("Queued for retry", Map.of("success", true, "msgid", msgid, "message", "Queued for retry")));
    }

    @PostMapping("/inbound/{msgid}/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolveInbound(@PathVariable("msgid") Long msgid) {
        Gwin msg = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Inbound message", msgid));

        msg.setStatus(InboundStatus.RESOLVED.getValue());
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

        msg.setStatus(InboundStatus.CANCELLED.getValue());
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

        msg.setStatus(OutboundStatus.PENDING.getValue());
        gwoutRepository.save(msg);

        return ResponseEntity.ok(ApiResponse.ok("Queued for retry", Map.of("success", true, "msgid", msgid, "message", "Queued for retry")));
    }

    @PostMapping("/outbound/{msgid}/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolveOutbound(@PathVariable("msgid") Long msgid) {
        Gwout msg = gwoutRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Outbound message", msgid));

        msg.setStatus(OutboundStatus.RESOLVED.getValue());
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

        msg.setStatus(OutboundStatus.CANCELLED.getValue());
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

    private LocalDateTime parseDateTime(String value) {
        try {
            if (value.contains("T")) {
                return LocalDateTime.parse(value);
            } else if (value.contains(" ")) {
                return LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                return LocalDateTime.parse(value + "T00:00:00");
            }
        } catch (Exception e) {
            return null;
        }
    }
}


