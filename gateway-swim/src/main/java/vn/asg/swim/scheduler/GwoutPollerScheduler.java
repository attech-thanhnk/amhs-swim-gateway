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
     * Single Outbound Pipeline: Run forwarding, dispatch creation, and publishing
     * sequentially to prevent Spring Thread Starvation.
     */
    @Scheduled(fixedDelayString = "#{configService.getPollIntervalMs()}", initialDelay = 5000)
    public void executeOutboundPipeline() {
        if (!connectionManager.getConnected().get()) {
            return;
        }

        // Step 1: Forward raw messages unchanged (gwout.status: PENDING=0 -> TRANSFORMED=1)
        pollGwoutAndForward();

        // Step 2: Create gwout_dispatch rows for each recipient (gwout.status stays TRANSFORMED=1)
        pollGwoutAndCreateDispatches();

        // Step 3: Publish dispatches to Solace (gwout.status becomes PUBLISHED=2 or FAILED=3 once all dispatches finish)
        pollDispatchesAndProcess();
    }

    void pollGwoutAndForward() {
        int batchSize = configService.getInt("OUTBOUND_BATCH_SIZE");
        List<Gwout> batch;
        try {
            batch = gwoutRepository.findPendingForwardBatch(batchSize);
        } catch (Exception e) {
            log.error("Error polling gwout for forwarding: {}", e.getMessage());
            return;
        }

        if (batch.isEmpty())
            return;

        log.info("Found {} pending gwout messages to forward", batch.size());

        for (Gwout gwout : batch) {
            try {
                outboundDispatchService.processOutboundMessage(gwout);
            } catch (Exception e) {
                log.error("Error forwarding gwout#{}: {}", gwout.getMsgid(), e.getMessage());
            }
        }
    }

    void pollGwoutAndCreateDispatches() {
        int batchSize = configService.getInt("OUTBOUND_BATCH_SIZE");
        List<Gwout> batch;
        try {
            batch = gwoutRepository.findPendingPublishBatch(batchSize);
        } catch (Exception e) {
            log.error("Error polling gwout for dispatch creation: {}", e.getMessage());
            return;
        }

        if (batch.isEmpty())
            return;

        for (Gwout gwout : batch) {
            try {
                outboundDispatchService.createDispatches(gwout);
            } catch (Exception e) {
                log.error("Error creating dispatches for gwout#{}: {}", gwout.getMsgid(), e.getMessage());
            }
        }
    }

    void pollDispatchesAndProcess() {
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
}
