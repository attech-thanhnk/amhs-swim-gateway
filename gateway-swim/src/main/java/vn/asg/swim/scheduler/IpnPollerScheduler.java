package vn.asg.swim.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.asg.swim.entity.McuIpn;
import vn.asg.swim.repository.McuIpnRepository;
import vn.asg.swim.service.ConfigService;
import vn.asg.swim.service.IpnProcessingService;

import java.util.List;

/**
 * Quét bảng {@code mtcu_ipn} để xử lý IPN (RN/NRN) mà AMHS Component ghi vào.
 * <p>
 * EUR Doc 047 §4.4.7, Appendix A CTSW014 và CTSW015.
 * <p>
 * Bảng {@code mtcu_ipn} do AMHS Component tạo và ghi; nếu chưa tồn tại thì scheduler chỉ ghi
 * cảnh báo một lần rồi bỏ qua, để gateway vẫn chạy bình thường trong lúc chờ amss triển khai.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IpnPollerScheduler {

    private final McuIpnRepository ipnRepository;
    private final IpnProcessingService ipnProcessingService;
    private final ConfigService configService;

    private boolean tableMissingLogged = false;

    @Scheduled(fixedDelayString = "#{configService.getPollIntervalMs()}", initialDelay = 7000)
    public void pollIpn() {
        List<McuIpn> batch;
        try {
            batch = ipnRepository.findPendingBatch(configService.getInt("INBOUND_BATCH_SIZE", 10));
        } catch (Exception e) {
            if (!tableMissingLogged) {
                log.warn("Chưa đọc được bảng mtcu_ipn ({}), bỏ qua xử lý IPN. "
                        + "Bảng này do AMHS Component cung cấp (CTSW014/CTSW015).", e.getMessage());
                tableMissingLogged = true;
            }
            return;
        }

        if (batch.isEmpty()) {
            return;
        }

        log.info("Tìm thấy {} IPN chờ xử lý", batch.size());
        for (McuIpn ipn : batch) {
            try {
                ipnProcessingService.processIpn(ipn);
            } catch (Exception e) {
                log.error("Lỗi xử lý IPN#{}: {}", ipn.getId(), e.getMessage(), e);
            }
        }
    }
}
