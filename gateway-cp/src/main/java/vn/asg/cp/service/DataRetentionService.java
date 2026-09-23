package vn.asg.cp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.cp.repository.GwinDispatchRepository;
import vn.asg.cp.repository.GwinRepository;
import vn.asg.cp.repository.GwoutDispatchRepository;
import vn.asg.cp.repository.GwoutRepository;
import vn.asg.cp.repository.MessageConversionLogRepository;
import vn.asg.cp.repository.SystemLogRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataRetentionService {

    private static final String KEY_RETENTION_DAYS = "LOG_RETENTION_DAYS";
    private static final int DEFAULT_RETENTION_DAYS = 30;

    private final ConfigService configService;
    private final SystemHistoryService historyService;

    private final GwinDispatchRepository gwinDispatchRepository;
    private final GwinRepository gwinRepository;
    private final GwoutDispatchRepository gwoutDispatchRepository;
    private final GwoutRepository gwoutRepository;
    private final MessageConversionLogRepository conversionLogRepository;
    private final SystemLogRepository systemLogRepository;

    public int getRetentionDays() {
        try {
            int days = configService.getInt(KEY_RETENTION_DAYS);
            return days > 0 ? days : DEFAULT_RETENTION_DAYS;
        } catch (Exception e) {
            log.warn("[Retention] Cannot read LOG_RETENTION_DAYS, using default {}d", DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
    }

    @Transactional
    public Map<String, Integer> runCleanup() {
        int retentionDays = getRetentionDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        log.info("[Retention] Starting cleanup - cutoff={} ({}d)", cutoff, retentionDays);

        Map<String, Integer> result = new LinkedHashMap<>();

        try {
            result.put("gwin_dispatch",          gwinDispatchRepository.deleteByCreatedAtBefore(cutoff));
            result.put("gwout_dispatch",         gwoutDispatchRepository.deleteByCreatedAtBefore(cutoff));
            result.put("gwin",                   gwinRepository.deleteByTimeBefore(cutoff));
            result.put("gwout",                  gwoutRepository.deleteByTimeBefore(cutoff));
            result.put("message_conversion_log", conversionLogRepository.deleteByConvertedTimeBefore(cutoff));
            result.put("system_log",             systemLogRepository.deleteByTimestampBefore(cutoff));

            int total = result.values().stream().mapToInt(Integer::intValue).sum();
            log.info("[Retention] Done - {} rows deleted: {}", total, result);

            historyService.info("DATA_RETENTION",
                String.format("Auto cleanup: deleted %d rows (retention=%dd)", total, retentionDays));

        } catch (Exception e) {
            log.error("[Retention] Cleanup failed: {}", e.getMessage(), e);
            historyService.error("DATA_RETENTION_ERROR", "Data retention cleanup failed", e.getMessage());
            throw new RuntimeException("Data retention cleanup failed: " + e.getMessage(), e);
        }

        return result;
    }
}
