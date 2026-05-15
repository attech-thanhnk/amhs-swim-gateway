package vn.asg.swim.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.GwoutDispatch;
import vn.asg.swim.repository.GwoutDispatchRepository;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.service.AlertService;
import vn.asg.swim.service.ConfigService;
import vn.asg.swim.service.ConnectionManagerService;
import vn.asg.swim.service.OutboundDispatchService;

import java.util.Arrays;
import java.util.List;

/**
 * Periodically polls gwout and gwout_dispatch tables to process
 * AMHS → SWIM messages.
 *
 * Task 1 (poll gwout): Polls PENDING gwout records → creates gwout_dispatch for
 * each recipient.
 * Task 2 (poll dispatch): Polls PENDING/FAILED gwout_dispatch records → invokes
 * OutboundDispatchService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GwoutPollerScheduler {

    private final GwoutRepository gwoutRepository;
    private final GwoutDispatchRepository gwoutDispatchRepository;
    private final OutboundDispatchService outboundDispatchService;
    private final ConnectionManagerService connectionManager;
    private final ConfigService configService;
    private final AlertService alertService;

    /**
     * Task 1: Poll PENDING gwout records → create dispatch rows for each recipient.
     */
    @Scheduled(fixedDelayString = "#{configService.getPollIntervalMs()}", initialDelay = 5000)
    @Transactional
    public void pollGwoutAndCreateDispatches() {
        if (!connectionManager.getConnected().get()) {
            log.debug("AMQP not connected, skipping gwout poll");
            return;
        }

        int batchSize = configService.getInt("OUTBOUND_BATCH_SIZE");
        List<Gwout> batch;
        try {
            batch = gwoutRepository.findPendingBatch(batchSize);
        } catch (Exception e) {
            log.error("Error polling gwout: {}", e.getMessage());
            return;
        }

        if (batch.isEmpty())
            return;
        log.debug("pollGwout: {} records found", batch.size());

        for (Gwout gwout : batch) {
            try {
                createDispatches(gwout);
            } catch (Exception e) {
                log.error("Error creating dispatches for gwout#{}: {}", gwout.getMsgid(), e.getMessage());
            }
        }
    }

    /**
     * Task 2: Poll PENDING/FAILED gwout_dispatch records → process.
     */
    @Scheduled(fixedDelayString = "#{configService.getPollIntervalMs()}", initialDelay = 6000)
    public void pollDispatchesAndProcess() {
        if (!connectionManager.getConnected().get())
            return;

        int batchSize = configService.getInt("OUTBOUND_BATCH_SIZE");
        List<GwoutDispatch> dispatches;
        try {
            dispatches = gwoutDispatchRepository.findPendingBatch(batchSize);
        } catch (Exception e) {
            log.error("Error polling gwout_dispatch: {}", e.getMessage());
            return;
        }

        for (GwoutDispatch dispatch : dispatches) {
            try {
                outboundDispatchService.processDispatch(dispatch);
            } catch (Exception e) {
                log.error("Unexpected error processing dispatch#{}: {}", dispatch.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Creates gwout_dispatch for each recipient in gwout.address.
     */
    private void createDispatches(Gwout gwout) {
        String address = gwout.getAddress();
        if (address == null || address.isBlank()) {
            log.warn("gwout#{} has no recipients, skipping", gwout.getMsgid());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + " has no recipients",
                    "gwout", gwout.getMsgid());
            gwout.setStatus(Gwout.STATUS_DEAD);
            gwoutRepository.save(gwout);
            return;
        }

        List<String> recipients = Arrays.stream(address.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> {
                    if (s.matches("^[A-Z]{8}$")) return true;
                    log.warn("gwout#{} contains invalid AFTN address: {}", gwout.getMsgid(), s);
                    return false;
                })
                .distinct()
                .toList();

        if (recipients.isEmpty()) {
            log.warn("gwout#{} has no valid AFTN recipients after filtering", gwout.getMsgid());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + " has no valid AFTN recipients",
                    "gwout", gwout.getMsgid());
            gwout.setStatus(Gwout.STATUS_DEAD);
            gwoutRepository.save(gwout);
            return;
        }

        for (String recipient : recipients) {
            GwoutDispatch dispatch = new GwoutDispatch();
            dispatch.setGwoutId(gwout.getMsgid());
            dispatch.setRecipient(recipient);
            dispatch.setStatus(GwoutDispatch.STATUS_PENDING);
            gwoutDispatchRepository.save(dispatch);
        }

        gwout.setStatus(Gwout.STATUS_PROCESSING);
        gwoutRepository.save(gwout);
        log.debug("gwout#{} → {} dispatch(es) created", gwout.getMsgid(), recipients.size());
    }
}
