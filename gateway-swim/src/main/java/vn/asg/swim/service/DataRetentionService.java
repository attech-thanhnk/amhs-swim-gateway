package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.asg.swim.repository.GwinRepository;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.repository.MessageConversionLogRepository;

import java.time.LocalDateTime;

/**
 * Service quản lý việc dọn dẹp dữ liệu cũ (Data Retention).
 * Tự động xóa các bản tin và log cũ để giải phóng dung lượng Database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataRetentionService {

    private final GwinRepository gwinRepository;
    private final GwoutRepository gwoutRepository;
    private final MessageConversionLogRepository conversionLogRepository;

    @Value("${swim.retention.days:30}")
    private int retentionDays;

    /**
     * Tác vụ dọn dẹp chạy định kỳ hàng ngày vào lúc 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldData() {
        log.info("Starting scheduled database cleanup (Retention days: {})...", retentionDays);
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        
        try {
            int deletedGwin = gwinRepository.deleteOldMessages(cutoffDate);
            log.info("Cleaned up {} old Gwin records.", deletedGwin);
            
            int deletedGwout = gwoutRepository.deleteOldMessages(cutoffDate);
            log.info("Cleaned up {} old Gwout records.", deletedGwout);
            
            int deletedLogs = conversionLogRepository.deleteOldLogs(cutoffDate);
            log.info("Cleaned up {} old conversion log records.", deletedLogs);
            
            log.info("Database cleanup completed successfully.");
        } catch (Exception e) {
            log.error("Database cleanup FAILED: {}", e.getMessage());
        }
    }
}
