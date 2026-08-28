package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.GwoutReport;
import vn.asg.swim.repository.GwoutReportRepository;

import java.util.Arrays;
import java.util.List;

/**
 * EUR Doc 047 §4.4.8 - Sinh AMHS report (DR/NDR) cho chiều AMHS → SWIM.
 * <p>
 * ITCU chỉ quyết định và xếp vào hàng đợi {@code gwout_report}; việc dựng và phát report ra
 * đường truyền X.400 do AMHS Component (amss) thực hiện.
 * <p>
 * Report của X.400 mang per-recipient-fields nên mỗi recipient là một dòng riêng. Với bản tin
 * bị từ chối toàn bộ, mọi recipient nhận cùng một bộ (reason, diagnostic, supplementary); với
 * probe của CTSW012 thì mỗi recipient có kết quả khác nhau và hợp thành combined report.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final GwoutReportRepository reportRepository;

    /**
     * Xếp NDR cho TOÀN BỘ recipient của bản tin — dùng cho các trường hợp từ chối ở mức bản tin
     * (sai content-type, quá kích thước, sai cú pháp ATS-message-header, EIT không hỗ trợ...).
     */
    public void recordNdrForAll(Gwout gwout, String diagnosticCode, String supplementaryInfo) {
        List<String> recipients = splitRecipients(gwout.getAddress());
        if (recipients.isEmpty()) {
            // Không có recipient hợp lệ nào (ví dụ address rỗng): vẫn ghi 1 dòng để amss biết
            // bản tin bị từ chối, dùng chính địa chỉ thô làm tham chiếu.
            recordNdr(gwout, safeRecipient(gwout.getAddress()), diagnosticCode, supplementaryInfo);
            return;
        }
        for (String recipient : recipients) {
            recordNdr(gwout, recipient, diagnosticCode, supplementaryInfo);
        }
    }

    /**
     * Xếp NDR cho MỘT recipient — dùng cho CTSW012, khi chỉ một phần recipient của probe không
     * chuyển đổi được sang địa chỉ AF.
     */
    public void recordNdr(Gwout gwout, String recipient, String diagnosticCode, String supplementaryInfo) {
        save(gwout, recipient, GwoutReport.TYPE_NDR, GwoutReport.REASON_UNABLE_TO_TRANSFER,
                diagnosticCode, supplementaryInfo);
    }

    /**
     * Xếp DR cho TOÀN BỘ recipient — CTSW003, khi bản tin đã chuyển đổi thành công và
     * per-recipient-indicators có yêu cầu report.
     */
    public void recordDrForAll(Gwout gwout) {
        for (String recipient : splitRecipients(gwout.getAddress())) {
            recordDr(gwout, recipient);
        }
    }

    /**
     * Xếp DR cho MỘT recipient — CTSW011/CTSW012, xác nhận probe conveyance test thành công.
     */
    public void recordDr(Gwout gwout, String recipient) {
        save(gwout, recipient, GwoutReport.TYPE_DR, null, null, null);
    }

    private void save(Gwout gwout, String recipient, String reportType, String reasonCode,
            String diagnosticCode, String supplementaryInfo) {
        if (recipient == null || recipient.isBlank()) {
            log.warn("gwout#{}: bỏ qua {} vì thiếu recipient", gwout.getMsgid(), reportType);
            return;
        }
        String trimmed = recipient.trim();
        try {
            // Idempotency: cùng (bản tin, recipient, loại report) chỉ ghi một lần
            if (reportRepository.findByGwoutIdAndRecipientAndReportType(
                    gwout.getMsgid(), trimmed, reportType).isPresent()) {
                log.debug("gwout#{}: {} cho {} đã tồn tại, bỏ qua", gwout.getMsgid(), reportType, trimmed);
                return;
            }

            GwoutReport report = new GwoutReport();
            report.setGwoutId(gwout.getMsgid());
            report.setMtsId(gwout.getAmhsid());
            report.setReportType(reportType);
            report.setRecipient(trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed);
            report.setReasonCode(reasonCode);
            report.setDiagnosticCode(diagnosticCode);
            if (supplementaryInfo != null) {
                report.setSupplementaryInfo(supplementaryInfo.length() > 512
                        ? supplementaryInfo.substring(0, 512) : supplementaryInfo);
            }
            report.setStatus(GwoutReport.STATUS_PENDING);
            reportRepository.save(report);
            log.info("gwout#{}: xếp hàng {} cho {} (diagnostic={})",
                    gwout.getMsgid(), reportType, trimmed, diagnosticCode);
        } catch (Exception e) {
            // Không để lỗi ghi report làm hỏng luồng xử lý bản tin chính
            log.error("gwout#{}: không ghi được {} cho {}: {}",
                    gwout.getMsgid(), reportType, trimmed, e.getMessage());
        }
    }

    private List<String> splitRecipients(String address) {
        if (address == null || address.isBlank()) {
            return List.of();
        }
        return Arrays.stream(address.trim().split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private String safeRecipient(String address) {
        if (address == null || address.isBlank()) {
            return "UNKNOWN";
        }
        String trimmed = address.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }
}
