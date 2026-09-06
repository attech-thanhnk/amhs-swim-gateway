package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwin;
import vn.asg.swim.entity.InboundStatus;
import vn.asg.swim.entity.McuReport;
import vn.asg.swim.repository.GwinRepository;
import vn.asg.swim.repository.McuReportRepository;

import java.util.List;

/**
 * EUR Doc 047 §4.4.1.3 (Report reception) — xử lý DR/NDR nhận từ AMHS cho bản tin ITCU đã gửi
 * ở chiều SWIM → AMHS. Appendix A CTSW114.
 * <p>
 * Tiêu chí chấm của CTSW114 gọn đúng một câu: <i>"Check that the IUT logs and reports the
 * situation to the Control Position."</i> Nên ITCU chỉ cần ghi log, tạo alert, và đánh dấu lại
 * bản tin gốc — không phải dựng lại report, không đẩy gì sang SWIM (§2.2.1.1 cấm chuyển
 * Report sang môi trường SWIM).
 * <p>
 * <b>Đây là chiều ngược với {@link ReportService}.</b> {@code ReportService} xếp report ITCU
 * TỰ SINH vào {@code gwout_report} để amss phát ra X.400 (chiều AMHS → SWIM). Lớp này đọc
 * report ITCU NHẬN VỀ từ {@code mtcu_report} (chiều SWIM → AMHS). Hai việc khác hẳn nhau.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportReceptionService {

    private final McuReportRepository reportRepository;
    private final GwinRepository gwinRepository;
    private final AlertService alertService;
    private final MessageConversionService conversionService;

    /**
     * Xử lý một report đến. Bản ghi luôn được đánh dấu PROCESSED để không lặp lại, kể cả khi
     * không tra ra bản tin gốc — CTSW114 vẫn đòi log và báo Control Position trong mọi trường hợp.
     */
    @Transactional
    public void processReport(McuReport report) {
        Gwin subject = findSubjectMessage(report);

        if (report.isNonDelivery()) {
            handleNonDeliveryReport(report, subject);
        } else {
            handleDeliveryReport(report, subject);
        }

        report.setStatus(McuReport.STATUS_PROCESSED);
        reportRepository.save(report);
    }

    /**
     * Tra bản tin gốc trong {@code gwin} — chiều SWIM → AMHS, vì report nói về bản tin ITCU
     * đã gửi đi. Ba mức, ưu tiên khoá chắc chắn nhất trước.
     */
    private Gwin findSubjectMessage(McuReport report) {
        if (report.getGwinId() != null) {
            // amss đã map sẵn - tin cậy nhất
            Gwin byId = gwinRepository.findById(report.getGwinId()).orElse(null);
            if (byId != null) {
                return byId;
            }
        }
        if (isUsable(report.getSubjectMtsId())) {
            List<Gwin> byMts = gwinRepository.findByMtsId(report.getSubjectMtsId().trim());
            if (!byMts.isEmpty()) {
                return byMts.get(0);
            }
        }
        if (isUsable(report.getSubjectIpmId())) {
            List<Gwin> byIpm = gwinRepository.findByIpmId(report.getSubjectIpmId().trim());
            if (!byIpm.isEmpty()) {
                return byIpm.get(0);
            }
        }
        return null;
    }

    /**
     * NDR về: bản tin không tới được người nhận. Log, báo Control Position, và sửa
     * lại trạng thái bản tin gốc.
     * <p>
     * Việc sửa trạng thái là cần thiết chứ không phải trang trí: amss đặt {@code gwin.status}
     * = 10 (DELIVERED) ngay khi bàn giao thành công cho MTA. NDR về sau thời điểm đó, nên nếu
     * không sửa thì Control Position vĩnh viễn hiển thị một bản tin thất bại là "đã giao xong".
     */
    private void handleNonDeliveryReport(McuReport report, Gwin subject) {
        String diagnostic = describeDiagnostic(report);

        if (subject == null) {
            // Không tra ra bản tin gốc vẫn phải báo — có thể bản tin đã bị dọn, hoặc amss chưa
            // ghi mts_id ngược vào gwin. Mất dấu vết còn tệ hơn một alert thiếu tham chiếu.
            log.warn("NDR#{} không tra được bản tin gốc (gwinId={} mtsId={} ipmId={}) - vẫn báo Control Position",
                    report.getId(), report.getGwinId(), report.getSubjectMtsId(), report.getSubjectIpmId());

            alertService.create(
                    GwAlert.TYPE_MESSAGE_DEAD, GwAlert.SEV_WARNING,
                    "NDR#" + report.getId() + " từ AMHS cho recipient " + report.getRecipient()
                            + " (" + diagnostic + ") - không tra được bản tin gốc (mtsId="
                            + report.getSubjectMtsId() + "), cần Control Position xử lý (§4.4.1.3)",
                    "mtcu_report", report.getId());

            conversionService.logSwimToAmhs(null, report.getRecipient(), "REJECTED",
                    "ndr_received_orphan: " + diagnostic, "non-delivery-report",
                    report.getSubjectIpmId());
            return;
        }

        log.warn("NDR#{} cho gwin#{}: bản tin không giao được tới {} ({})",
                report.getId(), subject.getMsgid(), report.getRecipient(), diagnostic);

        subject.setStatus(InboundStatus.FAILED.getValue());
        subject.setRejectionReason("non-delivery-report");
        subject.setRejectionDiagnostic(diagnostic);
        // Đặt SAU rejectionReason: setter của rejectionReason tự gán source = "SWIM" khi source
        // còn trống, mà lỗi này đến từ phía AMHS chứ không phải SWIM.
        subject.setRejectionSource("AMHS");
        gwinRepository.save(subject);

        alertService.create(
                GwAlert.TYPE_MESSAGE_DEAD, GwAlert.SEV_ERROR,
                "Bản tin gwin#" + subject.getMsgid() + " không giao được tới " + report.getRecipient()
                        + ": AMHS trả NDR (" + diagnostic + ") - báo Control Position (§4.4.1.3)",
                "gwin", subject.getMsgid());

        conversionService.logSwimToAmhs(subject.getMessageId(), subject.getOrigin(), "REJECTED",
                "ndr_received: " + report.getRecipient() + " - " + diagnostic,
                "non-delivery-report", subject.getIpmId());
    }

    /**
     * DR về: bản tin đã tới hòm thư người nhận. Không nằm trong tiêu chí chấm của CTSW114
     * nhưng amss dùng chung bảng, nên ghi nhận để Control Position tra cứu được.
     */
    private void handleDeliveryReport(McuReport report, Gwin subject) {
        if (subject == null) {
            log.info("DR#{} không tra được bản tin gốc (mtsId={}) - bỏ qua",
                    report.getId(), report.getSubjectMtsId());
            return;
        }

        log.info("DR#{} xác nhận gwin#{} đã tới {}",
                report.getId(), subject.getMsgid(), report.getRecipient());

        conversionService.logSwimToAmhs(subject.getMessageId(), subject.getOrigin(), "OK",
                "dr_received: " + report.getRecipient(), null, subject.getIpmId());
    }

    /**
     * Mô tả lỗi để đưa lên Control Position.
     * <p>
     * CTSW114 quy định NDR của nó mang {@code unable-to-transfer} cho reason-code và
     * <b>để TRỐNG</b> diagnostic-code. Nên diagnostic rỗng là dữ liệu đúng chuẩn, không phải
     * bản ghi hỏng: khi thiếu thì lấy reason-code, thiếu cả hai thì vẫn trả về một nhãn có
     * nghĩa chứ không để chuỗi rỗng lọt lên giao diện.
     */
    private String describeDiagnostic(McuReport report) {
        if (isUsable(report.getDiagnosticCode())) {
            return report.getDiagnosticCode().trim();
        }
        if (isUsable(report.getReasonCode())) {
            return report.getReasonCode().trim();
        }
        return "unable-to-transfer";
    }

    private boolean isUsable(String value) {
        return value != null && !value.isBlank();
    }
}
