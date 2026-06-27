package vn.asg.swim.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.repository.GwoutDispatchRepository;
import vn.asg.swim.repository.GwinRepository;
import vn.asg.swim.service.ConfigService;

import java.time.LocalDateTime;

/**
 * Scheduled task for database archiving and history purging.
 * Workflow:
 * 1. Move processed messages (SENT or DEAD) to history tables to keep active tables small (every 5 minutes).
 * 2. Purge historical records older than retention configuration (daily at 2:00 AM).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseCleanupScheduler {

    private final GwoutRepository gwoutRepository;
    private final GwoutDispatchRepository gwoutDispatchRepository;
    private final GwinRepository gwinRepository;
    private final ConfigService configService;

    /**
     * Task 1: Archive processed records (SENT or DEAD) to history tables
     * and delete them from active queue tables.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void archiveCompletedRecords() {
        log.trace("Starting database archiving task...");
        int archiveAfterHours = configService.getInt("CLEANUP_ARCHIVE_AFTER_HOURS", 24);
        LocalDateTime threshold = LocalDateTime.now().minusHours(archiveAfterHours);

        try {
            // 1. Archive Gwout records
            int archivedGwoutDispatches = gwoutDispatchRepository.archiveOldDispatches(threshold);
            int archivedGwouts = gwoutRepository.archiveOldRecords(threshold);
            
            if (archivedGwouts > 0 || archivedGwoutDispatches > 0) {
                log.info("Archived: {} gwout dispatches, {} gwout records to history (older than {} hours).", 
                        archivedGwoutDispatches, archivedGwouts, archiveAfterHours);

                // Delete from active queue tables (child tables first)
                int deletedGwoutDispatches = gwoutDispatchRepository.deleteArchivedDispatches();
                int deletedGwouts = gwoutRepository.deleteArchivedRecords();
                
                log.info("Cleaned up gwout queue: Removed {} dispatches, {} gwout records from active tables.", deletedGwoutDispatches, deletedGwouts);
            }

            // 2. Archive Gwin records
            int archivedGwins = gwinRepository.archiveOldRecords(threshold);

            if (archivedGwins > 0) {
                log.info("Archived: {} gwin records to history (older than {} hours).", archivedGwins, archiveAfterHours);

                // Delete from active queue tables
                int deletedGwins = gwinRepository.deleteArchivedRecords();

                log.info("Cleaned up gwin queue: Removed {} gwin records from active tables.", deletedGwins);
            }
        } catch (Exception e) {
            log.error("Error during database archiving: {}", e.getMessage(), e);
        }
    }

    /**
     * Task 2: Purge old history records (older than retention config).
     * Runs daily at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldHistoryRecords() {
        int retentionDays = configService.getInt("CLEANUP_RETENTION_DAYS", 30);
        log.info("Purging history tables (older than {} days)...", retentionDays);

        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);

        try {
            // Delete child history first, then parent history for gwout
            int deletedGwoutDispatches = gwoutDispatchRepository.deleteOldHistoryRecords(threshold);
            int deletedGwouts = gwoutRepository.deleteOldHistoryRecords(threshold);

            if (deletedGwouts > 0 || deletedGwoutDispatches > 0) {
                log.info("Gwout history purged: Removed {} old dispatches, {} old gwouts.", deletedGwoutDispatches, deletedGwouts);
            }

            // Delete parent history for gwin
            int deletedGwins = gwinRepository.deleteOldHistoryRecords(threshold);

            if (deletedGwins > 0) {
                log.info("Gwin history purged: Removed {} old gwins.", deletedGwins);
            }
        } catch (Exception e) {
            log.error("Error purging history records: {}", e.getMessage(), e);
        }
    }
}
