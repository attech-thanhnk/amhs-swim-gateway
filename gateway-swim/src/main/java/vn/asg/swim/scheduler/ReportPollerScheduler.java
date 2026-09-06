package vn.asg.swim.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.asg.swim.entity.McuReport;
import vn.asg.swim.repository.McuReportRepository;
import vn.asg.swim.service.ConfigService;
import vn.asg.swim.service.ReportReceptionService;

import java.util.List;

/**
 * Quét bảng mtcu_report để xử lý DR/NDR mà AMHS Component ghi vào.
 * <p>
 * EUR Doc 047 4.4.1.3 (Report reception), Appendix A CTSW114.
 * <p>
 * Bảng mtcu_report do AMHS Component ghi; nếu chưa tồn tại thì scheduler chỉ ghi cảnh
 * báo một lần rồi bỏ qua, để gateway vẫn chạy bình thường trong lúc chờ amss triển khai —
 * cùng cách IpnPollerScheduler đang làm với mtcu_ipn.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportPollerScheduler {

    private final McuReportRepository reportRepository;
    private final ReportReceptionService reportReceptionService;
    private final ConfigService configService;

    private boolean tableMissingLogged = false;

    @Scheduled(fixedDelayString = "#{configService.getPollIntervalMs()}", initialDelay = 9000)
    public void pollReports() {
        List<McuReport> batch;
        try {
            batch = reportRepository.findPendingBatch(configService.getInt("INBOUND_BATCH_SIZE", 10));
        } catch (Exception e) {
            if (!tableMissingLogged) {
                log.warn("Chưa đọc được bảng mtcu_report ({}), bỏ qua xử lý report đến. "
                        + "Bảng này do AMHS Component cung cấp (CTSW114).", e.getMessage());
                tableMissingLogged = true;
            }
            return;
        }

        if (batch.isEmpty()) {
            return;
        }

        log.info("Tìm thấy {} report (DR/NDR) chờ xử lý", batch.size());
        for (McuReport report : batch) {
            try {
                reportReceptionService.processReport(report);
            } catch (Exception e) {
                log.error("Lỗi xử lý report#{}: {}", report.getId(), e.getMessage(), e);
            }
        }
    }
}
