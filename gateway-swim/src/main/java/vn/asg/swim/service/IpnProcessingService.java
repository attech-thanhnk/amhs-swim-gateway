package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.McuIpn;
import vn.asg.swim.entity.OutboundStatus;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.repository.McuIpnRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EUR Doc 047 §4.4.7 - Xử lý IPN (Receipt/Non-Receipt Notification) nhận từ AMHS.
 * <p>
 * Appendix A CTSW014 và CTSW015. IPN KHÔNG được chuyển sang môi trường SWIM (§2.2.1.1);
 * ITCU kiểm tra rồi log và báo Control Position, hoặc sinh NDR nếu RN lạc tuyến.
 * <p>
 * Ba nhánh theo §4.4.7.1 - §4.4.7.3:
 * <ol>
 *   <li><b>Không tìm thấy bản tin chủ đề</b> (§4.4.7.1) - RN lạc tuyến: sinh NDR
 *       {@code invalid-arguments} kèm supplementary-information, log và báo Control Position.</li>
 *   <li><b>Tìm thấy nhưng priority khác "SS"</b> (§4.4.7.2) - từ chối: log và báo Control
 *       Position để xử lý thủ công, không sinh NDR.</li>
 *   <li><b>Tìm thấy và priority là "SS"</b> (§4.4.7.3) - RN hợp lệ: log và báo Control Position.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpnProcessingService {

    private final McuIpnRepository ipnRepository;
    private final GwoutRepository gwoutRepository;
    private final ReportService reportService;
    private final AlertService alertService;
    private final MessageConversionService conversionService;

    /**
     * Xử lý một IPN đến. Bản ghi luôn được đánh dấu PROCESSED để không lặp lại,
     * kể cả khi bị từ chối — Control Position vẫn tra cứu được qua alert và traffic log.
     */
    @Transactional
    public void processIpn(McuIpn ipn) {
        Gwout subject = findSubjectMessage(ipn);

        if (subject == null) {
            handleMisroutedIpn(ipn);
        } else if (!"SS".equalsIgnoreCase(subject.getAmhsPriority())) {
            handleNonSsPriority(ipn, subject);
        } else {
            handleValidRn(ipn, subject);
        }

        ipn.setStatus(McuIpn.STATUS_PROCESSED);
        ipnRepository.save(ipn);
    }

    /**
     * §4.4.7.1 a) - bản tin chủ đề đã được ITCU sinh ra trước đó hay chưa.
     * Ưu tiên IPM-Identifier, dự phòng bằng MTS-Identifier.
     */
    private Gwout findSubjectMessage(McuIpn ipn) {
        if (ipn.getSubjectIpmId() != null && !ipn.getSubjectIpmId().isBlank()) {
            List<Gwout> byIpm = gwoutRepository.findByIpmId(ipn.getSubjectIpmId().trim());
            if (!byIpm.isEmpty()) {
                return byIpm.get(0);
            }
        }
        if (ipn.getSubjectMtsId() != null && !ipn.getSubjectMtsId().isBlank()) {
            List<Gwout> byMts = gwoutRepository.findByAmhsid(ipn.getSubjectMtsId().trim());
            if (!byMts.isEmpty()) {
                return byMts.get(0);
            }
        }
        return null;
    }

    /**
     * CTSW015 (§4.4.7.1) - RN có bản tin chủ đề không tồn tại: sinh NDR misrouted,
     * log và lưu lại để Control Position xử lý.
     */
    private void handleMisroutedIpn(McuIpn ipn) {
        log.warn("IPN#{} ({}) lạc tuyến: không tìm thấy bản tin chủ đề ipmId={} mtsId={}",
                ipn.getId(), ipn.getNotificationType(), ipn.getSubjectIpmId(), ipn.getSubjectMtsId());

        alertService.create(
                GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                "IPN#" + ipn.getId() + " (" + ipn.getNotificationType() + ") từ " + ipn.getOrAddress()
                        + " lạc tuyến: bản tin chủ đề (ipmId=" + ipn.getSubjectIpmId()
                        + ") chưa từng đi qua gateway - cần Control Position xử lý (§4.4.7.1)",
                "mtcu_ipn", ipn.getId());

        // NDR đi qua cùng hàng đợi gwout_report, mà gwout_report.gwout_id có khoá ngoại
        // fk_report_gwout trỏ về gwout(msgid). Trước đây chỗ này dựng một Gwout tạm trong bộ nhớ
        // với msgid = mtcu_ipn.id — một giá trị KHÔNG thuộc không gian id của gwout — nên INSERT
        // luôn vi phạm khoá ngoại, ReportService nuốt lỗi và NDR của CTSW015 mất im lặng.
        //
        // Vì vậy phải GHI THẬT một dòng gwout cho sự kiện IPN lạc tuyến. Mọi cột gwout đều
        // nullable trừ msgid nên chi phí không đáng kể, và amss không phải đổi gì: vẫn đọc
        // gwout_report và gom theo gwout_id như với mọi bản tin khác.
        Gwout placeholder = new Gwout();
        // amhsid để NULL: ràng buộc uk_gwout_amhsid không cho hai IPN lạc tuyến cùng subject
        // MTS-Id cùng tồn tại. MTS-Id vẫn xuống tới amss qua tham số của recordNdr bên dưới.
        // ipm_id để NULL: nếu điền, findSubjectMessage() sẽ khớp trúng chính dòng placeholder này
        // ở lần IPN sau và rẽ nhầm sang nhánh §4.4.7.2 thay vì nhánh lạc tuyến §4.4.7.1.
        placeholder.setOrigin(ipn.getOrAddress());
        placeholder.setAddress(ipn.getOrAddress());
        placeholder.setTime(LocalDateTime.now());
        placeholder.setStatus(OutboundStatus.FAILED.getValue());
        placeholder.setRejectionSource("AMHS");
        placeholder.setRejectionReason("misrouted-ipn");
        placeholder.setRejectionDiagnostic("invalid-arguments");
        gwoutRepository.saveAndFlush(placeholder);

        reportService.recordNdr(placeholder.getMsgid(), ipn.getSubjectMtsId(), ipn.getOrAddress(),
                "invalid-arguments", "unable to notify RN to SWIM due to misrouted RN");
        // Traffic log giữ đủ subject IPM-Id / MTS-Id để Control Position tra ngược được,
        // dù hai cột tương ứng trên dòng gwout placeholder cố tình để NULL.
        conversionService.logAmhsToSwim(placeholder, null, "REJECTED",
                "ndr_misrouted_ipn: " + ipn.getNotificationType() + " ipmId=" + ipn.getSubjectIpmId(),
                ipn.getSubjectMtsId(), ipn.getSubjectIpmId(),
                "invalid-arguments", "unable to notify RN to SWIM due to misrouted RN");
    }

    /**
     * CTSW014 (điện văn 2, §4.4.7.2) - bản tin chủ đề có priority khác "SS": RN bị từ chối,
     * log và báo Control Position, không sinh NDR.
     */
    private void handleNonSsPriority(McuIpn ipn, Gwout subject) {
        log.warn("IPN#{} bị từ chối: bản tin chủ đề gwout#{} có priority '{}' khác SS (§4.4.7.2)",
                ipn.getId(), subject.getMsgid(), subject.getAmhsPriority());

        alertService.create(
                GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                "IPN#" + ipn.getId() + " (" + ipn.getNotificationType() + ") bị từ chối: bản tin chủ đề "
                        + "gwout#" + subject.getMsgid() + " có priority '" + subject.getAmhsPriority()
                        + "' khác SS - lưu lại để Control Position xử lý (§4.4.7.2)",
                "mtcu_ipn", ipn.getId());

        conversionService.logAmhsToSwim(subject, null, "REJECTED",
                "ipn_rejected_priority: " + subject.getAmhsPriority() + " (IPN#" + ipn.getId() + ")");
    }

    /**
     * CTSW014 (điện văn 1, §4.4.7.3) - RN hợp lệ: không bị từ chối, được log và báo
     * Control Position. IPN không chuyển sang SWIM (§2.2.1.1).
     */
    private void handleValidRn(McuIpn ipn, Gwout subject) {
        log.info("IPN#{} hợp lệ cho bản tin chủ đề gwout#{} (priority SS) - báo Control Position",
                ipn.getId(), subject.getMsgid());

        alertService.create(
                GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_INFO,
                "IPN#" + ipn.getId() + " (" + ipn.getNotificationType() + ") từ " + ipn.getOrAddress()
                        + " xác nhận bản tin ưu tiên SS gwout#" + subject.getMsgid()
                        + " đã được nhận (§4.4.7.3)",
                "mtcu_ipn", ipn.getId());

        conversionService.logAmhsToSwim(subject, null, "OK",
                "ipn_accepted: " + ipn.getNotificationType() + " từ " + ipn.getOrAddress());
    }
}
