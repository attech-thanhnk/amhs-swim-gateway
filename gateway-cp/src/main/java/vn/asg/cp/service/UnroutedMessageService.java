package vn.asg.cp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.cp.dto.BatchOperationResponse;
import vn.asg.cp.dto.BatchRouteRequest;
import vn.asg.cp.dto.ManualRouteRequest;
import vn.asg.cp.dto.RejectMessageRequest;
import vn.asg.cp.entity.Gwin;
import vn.asg.cp.entity.InboundStatus;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.repository.GwinRepository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dịch vụ xử lý bản tin UNROUTED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnroutedMessageService {

    private final GwinRepository gwinRepository;

    /**
     * Lấy danh sách bản tin UNROUTED phân trang (backward compatibility).
     */
    public Page<Gwin> getUnroutedMessages(LocalDateTime fromTime, LocalDateTime toTime,
            String source, Pageable pageable) {
        return getUnroutedMessages(fromTime, toTime, source, null, null, pageable);
    }

    /**
     * Lấy danh sách bản tin UNROUTED phân trang hỗ trợ tìm kiếm theo originator, query keyword, source.
     */
    public Page<Gwin> getUnroutedMessages(LocalDateTime fromTime, LocalDateTime toTime,
            String source, String originator, String query, Pageable pageable) {

        Specification<Gwin> spec = (root, q, cb) -> cb.equal(root.get("status"), InboundStatus.UNROUTED.getValue());

        if (fromTime != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("time"), fromTime));
        }

        if (toTime != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("time"), toTime));
        }

        if (originator != null && !originator.trim().isEmpty()) {
            String origKw = "%" + originator.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("origin")), origKw));
        }

        if (query != null && !query.trim().isEmpty()) {
            String trimmed = query.trim();
            String kw = "%" + trimmed.toLowerCase() + "%";
            String numStr = trimmed.startsWith("#") ? trimmed.substring(1).trim() : trimmed;
            Long idVal = null;
            try {
                idVal = Long.parseLong(numStr);
            } catch (NumberFormatException ignored) {}
            final Long finalId = idVal;

            spec = spec.and((r, q, cb) -> {
                List<Predicate> orPredicates = new ArrayList<>();
                if (finalId != null) {
                    orPredicates.add(cb.equal(r.get("msgid"), finalId));
                }
                orPredicates.add(cb.like(cb.lower(r.get("origin")), kw));
                orPredicates.add(cb.like(cb.lower(r.get("source")), kw));
                orPredicates.add(cb.like(cb.lower(r.get("subject")), kw));
                orPredicates.add(cb.like(cb.lower(r.get("address")), kw));
                orPredicates.add(cb.like(cb.lower(r.get("messageId")), kw));
                orPredicates.add(cb.like(cb.lower(r.get("amhsRecipients")), kw));
                orPredicates.add(cb.like(cb.lower(r.get("rejectionDiagnostic")), kw));
                orPredicates.add(cb.like(cb.lower(r.get("rejectionReason")), kw));
                return cb.or(orPredicates.toArray(new Predicate[0]));
            });
        }

        // Handle source parameter:
        // If neither query nor originator is set (e.g. legacy FE sent originator inside source parameter),
        // match both origin and source flexibly. Otherwise, filter by source topic.
        if (source != null && !source.trim().isEmpty()) {
            String srcTrimmed = source.trim();
            String srcKw = "%" + srcTrimmed.toLowerCase() + "%";
            if ((query == null || query.trim().isEmpty()) && (originator == null || originator.trim().isEmpty())) {
                spec = spec.and((r, q, cb) -> cb.or(
                    cb.like(cb.lower(r.get("origin")), srcKw),
                    cb.like(cb.lower(r.get("source")), srcKw)
                ));
            } else {
                spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("source")), srcKw));
            }
        }

        return gwinRepository.findAll(spec, pageable);
    }

    /**
     * Lấy bản tin UNROUTED theo ID.
     */
    public Optional<Gwin> getUnroutedMessageById(Long msgid) {
        return gwinRepository.findById(msgid)
                .filter(gwin -> gwin.getStatus() == InboundStatus.UNROUTED.getValue());
    }

    /**
     * Phân phối thủ công bản tin UNROUTED.
     */
    @Transactional
    public Gwin manuallyRoute(Long msgid, ManualRouteRequest request) {
        Gwin gwin = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Message", msgid));

        if (gwin.getStatus() != InboundStatus.UNROUTED.getValue()) {
            throw new IllegalStateException("Message is not UNROUTED: status=" + gwin.getStatus());
        }

        // Update addressing
        gwin.setOrigin(request.getOriginator());
        gwin.setAddress(request.getRecipients());
        gwin.setAddressingSource("MANUAL_ROUTE");
        gwin.setStatus(InboundStatus.PENDING.getValue());

        log.info("Manually routed message #{}: {} → {}", msgid, request.getOriginator(), request.getRecipients());
        return gwinRepository.save(gwin);
    }

    /**
     * Từ chối bản tin UNROUTED.
     */
    @Transactional
    public Gwin rejectMessage(Long msgid, RejectMessageRequest request) {
        Gwin gwin = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Message", msgid));

        if (gwin.getStatus() != InboundStatus.UNROUTED.getValue()) {
            throw new IllegalStateException("Message is not UNROUTED: status=" + gwin.getStatus());
        }

        // Update status to FAILED
        gwin.setStatus(InboundStatus.FAILED.getValue());
        // Store rejection reason in amqpProperties (temporary solution)
        String rejectionInfo = String.format("{\"rejection_reason\":\"%s\",\"rejection_note\":\"%s\"}",
                request.getReason(), request.getNote() != null ? request.getNote() : "");
        gwin.setAmqpProperties(rejectionInfo);

        log.info("Rejected message #{}: {}", msgid, request.getReason());
        return gwinRepository.save(gwin);
    }

    /**
     * Phân phối thủ công hàng loạt bản tin UNROUTED.
     */
    @Transactional
    public BatchOperationResponse batchRoute(BatchRouteRequest request) {
        BatchOperationResponse response = new BatchOperationResponse();
        response.setProcessed(request.getMsgids().size());

        int succeeded = 0;
        int failed = 0;

        for (Long msgid : request.getMsgids()) {
            try {
                Optional<Gwin> gwinOpt = gwinRepository.findById(msgid);
                if (gwinOpt.isEmpty()) {
                    response.addError(msgid, "Message not found");
                    failed++;
                    continue;
                }

                Gwin gwin = gwinOpt.get();
                if (gwin.getStatus() != InboundStatus.UNROUTED.getValue()) {
                    response.addError(msgid, "Message is not UNROUTED");
                    failed++;
                    continue;
                }

                // Update addressing
                gwin.setOrigin(request.getOriginator());
                gwin.setAddress(request.getRecipients());
                gwin.setAddressingSource("MANUAL_ROUTE_BATCH");
                gwin.setStatus(InboundStatus.PENDING.getValue());
                gwinRepository.save(gwin);

                succeeded++;
                log.debug("Batch routed message #{}", msgid);

            } catch (Exception e) {
                response.addError(msgid, e.getMessage());
                failed++;
                log.error("Failed to batch route message #{}: {}", msgid, e.getMessage());
            }
        }

        response.setSucceeded(succeeded);
        response.setFailed(failed);

        log.info("Batch route completed: {} succeeded, {} failed out of {}",
                succeeded, failed, request.getMsgids().size());

        return response;
    }

    /**
     * Lấy tổng số lượng bản tin UNROUTED.
     */
    public long getUnroutedCount() {
        return gwinRepository.countByStatus(InboundStatus.UNROUTED.getValue());
    }

    /**
     * Lấy số lượng bản tin UNROUTED trong khoảng thời gian chỉ định.
     */
    public long getUnroutedCountInRange(LocalDateTime fromTime, LocalDateTime toTime) {
        return gwinRepository.countByStatusAndTimeBetween(InboundStatus.UNROUTED.getValue(), fromTime, toTime);
    }
}