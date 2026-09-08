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
 * <p>
 * EUR Doc 047 §4.4.7 và §4.4.1.3 - Appendix A CTSW014, CTSW015, CTSW113, CTSW114.
 * <p>
 * Theo dõi tiến độ bằng <b>mốc {@code cp.id}</b> lưu trong {@code gateway_config}, KHÔNG ghi đè
 * {@code cp.status}: cột đó là phân loại riêng của AMHS Component ({@code 3} = tìm thấy điện văn
 * chủ đề, {@code 4} = không tìm thấy) và đang hiển thị trên Control Position, ghi đè là xoá mất.
 * <p>
 * Bảng {@code cp} do AMHS Component tạo và ghi; nếu chưa đọc được thì repository chỉ ghi cảnh báo
 * một lần rồi trả danh sách rỗng, để gateway vẫn chạy bình thường.
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
            // Đẩy mốc kể cả khi bản ghi vừa rồi lỗi: giữ nguyên mốc sẽ khiến vòng quét kẹt vĩnh
            // viễn ở đúng bản ghi hỏng đó và mọi phản hồi sau nó không bao giờ tới Control Position.
            // Lỗi đã được ghi log ở trên để tra lại.
            feedbackRepository.setWatermark(feedback.getId());
        }
    }
}
