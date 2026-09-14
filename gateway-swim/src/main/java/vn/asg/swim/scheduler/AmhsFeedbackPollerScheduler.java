package vn.asg.swim.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.asg.swim.model.AmhsFeedback;
import vn.asg.swim.repository.AmhsFeedbackRepository;
import vn.asg.swim.service.AmhsFeedbackService;
import vn.asg.swim.service.ConfigService;

import java.util.List;

/**
 * Quét bảng {@code cp} để xử lý phản hồi AMHS (RN/NRN/DR/NDR) mà AMHS Component ghi vào.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AmhsFeedbackPollerScheduler {

    private final AmhsFeedbackRepository feedbackRepository;
    private final AmhsFeedbackService feedbackService;
    private final ConfigService configService;

    @Scheduled(fixedDelayString = "#{configService.getPollIntervalMs()}", initialDelay = 7000)
    public void pollFeedback() {
        long lastId = feedbackRepository.getWatermark();
        int batchSize = configService.getInt("INBOUND_BATCH_SIZE", 10);

        List<AmhsFeedback> batch = feedbackRepository.findNewBatch(lastId, batchSize);
        if (batch.isEmpty()) {
            return;
        }

        log.info("Tìm thấy {} phản hồi AMHS mới (sau cp#{})", batch.size(), lastId);
        for (AmhsFeedback feedback : batch) {
            try {
                feedbackService.processFeedback(feedback);
            } catch (Exception e) {
                log.error("Lỗi xử lý phản hồi cp#{}: {}", feedback.getId(), e.getMessage(), e);
            }
            // Cập nhật watermark kể cả khi xử lý bản ghi bị lỗi
            feedbackRepository.setWatermark(feedback.getId());
        }
    }
}
