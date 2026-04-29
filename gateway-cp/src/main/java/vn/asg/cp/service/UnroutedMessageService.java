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
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.repository.GwinRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for managing UNROUTED messages.
 * <p>
 * Handles:
 * - Get UNROUTED messages
 * - Manual routing
 * - Rejection
 * - Batch operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnroutedMessageService {

    private final GwinRepository gwinRepository;

    /**
     * Get all UNROUTED messages (status = 5).
     */
    public Page<Gwin> getUnroutedMessages(LocalDateTime fromTime, LocalDateTime toTime,
            String source, Pageable pageable) {
        // Custom query with filtering
        if (fromTime != null && toTime != null && source != null) {
            return gwinRepository.findByStatusAndTimeBetweenAndSource(
                    Gwin.STATUS_UNROUTED, fromTime, toTime, source, pageable);
        }

        if (fromTime != null && toTime != null) {
            return gwinRepository.findByStatusAndTimeBetween(
                    Gwin.STATUS_UNROUTED, fromTime, toTime, pageable);
        }

        if (source != null) {
            return gwinRepository.findByStatusAndSource(Gwin.STATUS_UNROUTED, source, pageable);
        }

        return gwinRepository.findByStatus(Gwin.STATUS_UNROUTED, pageable);
    }

    /**
     * Get UNROUTED message by ID.
     */
    public Optional<Gwin> getUnroutedMessageById(Long msgid) {
        return gwinRepository.findById(msgid)
                .filter(gwin -> gwin.getStatus() == Gwin.STATUS_UNROUTED);
    }

    /**
     * Manually route UNROUTED message.
     */
    @Transactional
    public Gwin manuallyRoute(Long msgid, ManualRouteRequest request) {
        Gwin gwin = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Message", msgid));

        if (gwin.getStatus() != Gwin.STATUS_UNROUTED) {
            throw new IllegalStateException("Message is not UNROUTED: status=" + gwin.getStatus());
        }

        // Update addressing
        gwin.setOrigin(request.getOriginator());
        gwin.setAddress(request.getRecipients());
        gwin.setAddressingSource("MANUAL_ROUTE");
        gwin.setStatus(Gwin.STATUS_PENDING);

        log.info("Manually routed message #{}: {} → {}", msgid, request.getOriginator(), request.getRecipients());
        return gwinRepository.save(gwin);
    }

    /**
     * Reject UNROUTED message.
     */
    @Transactional
    public Gwin rejectMessage(Long msgid, RejectMessageRequest request) {
        Gwin gwin = gwinRepository.findById(msgid)
                .orElseThrow(() -> new ResourceNotFoundException("Message", msgid));

        if (gwin.getStatus() != Gwin.STATUS_UNROUTED) {
            throw new IllegalStateException("Message is not UNROUTED: status=" + gwin.getStatus());
        }

        // Update status to DEAD
        gwin.setStatus(Gwin.STATUS_DEAD);
        // Store rejection reason in amqpProperties (temporary solution)
        String rejectionInfo = String.format("{\"rejection_reason\":\"%s\",\"rejection_note\":\"%s\"}",
                request.getReason(), request.getNote() != null ? request.getNote() : "");
        gwin.setAmqpProperties(rejectionInfo);

        log.info("Rejected message #{}: {}", msgid, request.getReason());
        return gwinRepository.save(gwin);
    }

    /**
     * Batch route UNROUTED messages.
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
                if (gwin.getStatus() != Gwin.STATUS_UNROUTED) {
                    response.addError(msgid, "Message is not UNROUTED");
                    failed++;
                    continue;
                }

                // Update addressing
                gwin.setOrigin(request.getOriginator());
                gwin.setAddress(request.getRecipients());
                gwin.setAddressingSource("MANUAL_ROUTE_BATCH");
                gwin.setStatus(Gwin.STATUS_PENDING);
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
     * Get UNROUTED count for statistics.
     */
    public long getUnroutedCount() {
        return gwinRepository.countByStatus(Gwin.STATUS_UNROUTED);
    }

    /**
     * Get UNROUTED count in time range.
     */
    public long getUnroutedCountInRange(LocalDateTime fromTime, LocalDateTime toTime) {
        return gwinRepository.countByStatusAndTimeBetween(Gwin.STATUS_UNROUTED, fromTime, toTime);
    }
}
