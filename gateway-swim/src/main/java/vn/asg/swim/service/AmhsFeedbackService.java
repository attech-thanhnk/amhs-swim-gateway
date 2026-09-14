package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwin;
import vn.asg.swim.entity.InboundStatus;
import vn.asg.swim.model.AmhsFeedback;
import vn.asg.swim.model.AmqpProperties;
import vn.asg.swim.repository.AmhsFeedbackRepository;
import vn.asg.swim.repository.GwinRepository;

import java.util.List;

/**
 * Xử lý phản hồi AMHS (DR, NDR, RN, NRN) cho điện văn gửi ở chiều SWIM → AMHS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AmhsFeedbackService {

    private final AmhsFeedbackRepository feedbackRepository;
    private final GwinRepository gwinRepository;
    private final AlertService alertService;
    private final MessageConversionService conversionService;

    @Transactional
    public void processFeedback(AmhsFeedback feedback) {
        Gwin subject = findSubjectMessage(feedback);

        if (feedback.isReport()) {
            handleReport(feedback, subject);
        } else if (subject == null) {
            handleMisroutedIpn(feedback);
        } else if (!"SS".equalsIgnoreCase(atsPriorityOf(subject))) {
            handleNonSsPriority(feedback, subject);
        } else {
            handleValidIpn(feedback, subject);
        }

    }

    private Gwin findSubjectMessage(AmhsFeedback feedback) {
        boolean preferMts = feedback.isReport();

        for (String key : new String[] {
                preferMts ? feedback.getSubjectMts() : feedback.getSubjectIpm(),
                preferMts ? feedback.getSubjectIpm() : feedback.getSubjectMts() }) {
            Gwin found = lookup(key, preferMts);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Gwin lookup(String key, boolean preferMts) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        List<Gwin> rows = preferMts
                ? gwinRepository.findByMtsId(trimmed)
                : gwinRepository.findByIpmId(trimmed);
        if (rows.isEmpty()) {
            rows = preferMts
                    ? gwinRepository.findByIpmId(trimmed)
                    : gwinRepository.findByMtsId(trimmed);
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Xử lý báo cáo tầng vận chuyển (DR/NDR).
     */
    private void handleReport(AmhsFeedback feedback, Gwin subject) {
        if (!AmhsFeedback.TYPE_NDR.equalsIgnoreCase(feedback.getIpnType())) {
            if (subject == null) {
                log.info("DR#{} không tra được điện văn gốc (mtsId={}) - bỏ qua",
                        feedback.getId(), feedback.getSubjectMts());
                return;
            }
            log.info("DR#{} xác nhận gwin#{} đã tới {}",
                    feedback.getId(), subject.getMsgid(), feedback.getRecipient());
            conversionService.logSwimToAmhs(subject.getMessageId(), subject.getOrigin(), "OK",
                    "dr_received: " + feedback.getRecipient(), null, subject.getIpmId());
            return;
        }

        String diagnostic = describeDiagnostic(feedback);

        if (subject == null) {
            log.warn("NDR#{} không tra được điện văn gốc (mtsId={} ipmId={}) - vẫn báo Control Position",
                    feedback.getId(), feedback.getSubjectMts(), feedback.getSubjectIpm());
            alertService.create(
                    GwAlert.TYPE_MESSAGE_DEAD, GwAlert.SEV_WARNING,
                    "NDR#" + feedback.getId() + " từ AMHS cho " + feedback.getRecipient()
                            + " (" + diagnostic + ") - không tra được điện văn gốc (mtsId="
                            + feedback.getSubjectMts() + "), cần Control Position xử lý",
                    "cp", feedback.getId());
            conversionService.logSwimToAmhs(null, feedback.getRecipient(), "REJECTED",
                    "ndr_received_orphan: " + diagnostic, "non-delivery-report",
                    feedback.getSubjectIpm());
            return;
        }

        log.warn("NDR#{} cho gwin#{}: điện văn không giao được tới {} ({})",
                feedback.getId(), subject.getMsgid(), feedback.getRecipient(), diagnostic);

        subject.setStatus(InboundStatus.FAILED.getValue());
        subject.setRejectionReason("non-delivery-report");
        subject.setRejectionDiagnostic(diagnostic);
        subject.setRejectionSource("AMHS");
        gwinRepository.save(subject);

        alertService.create(
                GwAlert.TYPE_MESSAGE_DEAD, GwAlert.SEV_ERROR,
                "Điện văn gwin#" + subject.getMsgid() + " không giao được tới " + feedback.getRecipient()
                        + ": AMHS trả NDR (" + diagnostic + ") - báo Control Position",
                "gwin", subject.getMsgid());

        conversionService.logSwimToAmhs(subject.getMessageId(), subject.getOrigin(), "REJECTED",
                "ndr_received: " + feedback.getRecipient() + " - " + diagnostic,
                "non-delivery-report", subject.getIpmId());
    }

    /**
     * Xử lý IPN lạc tuyến (không tìm thấy điện văn gốc).
     */
    private void handleMisroutedIpn(AmhsFeedback feedback) {
        log.warn("IPN#{} ({}) lạc tuyến: không tìm thấy điện văn gốc ipmId={} mtsId={}",
                feedback.getId(), feedback.getIpnType(), feedback.getSubjectIpm(), feedback.getSubjectMts());

        alertService.create(
                GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                "IPN#" + feedback.getId() + " (" + feedback.getIpnType() + ") từ " + feedback.getOrigin()
                        + " lạc tuyến: điện văn gốc (ipmId=" + feedback.getSubjectIpm()
                        + ") chưa từng đi qua gateway - cần Control Position xử lý",
                "cp", feedback.getId());

        conversionService.logSwimToAmhs(null, feedback.getOrigin(), "REJECTED",
                "misrouted_ipn: " + feedback.getIpnType() + " ipmId=" + feedback.getSubjectIpm()
                        + " mtsId=" + feedback.getSubjectMts(),
                "misrouted-ipn", feedback.getSubjectIpm());
    }

    /**
     * Xử lý IPN có điện văn gốc priority khác SS (từ chối).
     */
    private void handleNonSsPriority(AmhsFeedback feedback, Gwin subject) {
        String priority = atsPriorityOf(subject);
        log.warn("IPN#{} bị từ chối: điện văn gốc gwin#{} có priority {} khác SS",
                feedback.getId(), subject.getMsgid(), priority);

        alertService.create(
                GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                "IPN#" + feedback.getId() + " (" + feedback.getIpnType() + ") bị từ chối: điện văn gốc "
                        + "gwin#" + subject.getMsgid() + " có priority " + priority
                        + " khác SS - lưu lại để Control Position xử lý",
                "cp", feedback.getId());

        conversionService.logSwimToAmhs(subject.getMessageId(), subject.getOrigin(), "REJECTED",
                "ipn_rejected_priority: " + priority + " (IPN#" + feedback.getId() + ")",
                "ipn-priority-not-ss", subject.getIpmId());
    }

    /**
     * Xử lý IPN hợp lệ (RN/NRN).
     */
    private void handleValidIpn(AmhsFeedback feedback, Gwin subject) {
        boolean notRead = AmhsFeedback.TYPE_NRN.equalsIgnoreCase(feedback.getIpnType());
        log.info("IPN#{} ({}) hợp lệ cho điện văn gốc gwin#{} (priority SS) - báo Control Position",
                feedback.getId(), feedback.getIpnType(), subject.getMsgid());

        String detail = notRead
                ? " báo điện văn ưu tiên SS gwin#" + subject.getMsgid() + " tới nơi nhưng KHÔNG được đọc"
                        + reasonSuffix(feedback)
                : " xác nhận điện văn ưu tiên SS gwin#" + subject.getMsgid() + " đã được nhận";

        alertService.create(
                GwAlert.TYPE_VALIDATION_ERROR,
                notRead ? GwAlert.SEV_WARNING : GwAlert.SEV_INFO,
                "IPN#" + feedback.getId() + " (" + feedback.getIpnType() + ") từ " + feedback.getOrigin()
                        + detail,
                "cp", feedback.getId());

        conversionService.logSwimToAmhs(subject.getMessageId(), subject.getOrigin(),
                notRead ? "REJECTED" : "OK",
                "ipn_" + (notRead ? "non_receipt" : "accepted") + ": " + feedback.getIpnType()
                        + " từ " + feedback.getOrigin(),
                notRead ? "non-receipt-notification" : null,
                subject.getIpmId());
    }

    private String reasonSuffix(AmhsFeedback feedback) {
        if (feedback.getNonReceiptReason() != null) {
            return " (non-receipt-reason=" + feedback.getNonReceiptReason()
                    + (feedback.getDiscardReason() != null
                            ? ", discard-reason=" + feedback.getDiscardReason() : "") + ")";
        }
        return "";
    }

    /**
     * Lấy ATS priority của điện văn gốc.
     */
    private String atsPriorityOf(Gwin subject) {
        if (subject.getAtsPriority() != null && !subject.getAtsPriority().isBlank()) {
            return subject.getAtsPriority().trim().toUpperCase();
        }
        if (subject.getPriority() == null) {
            return null;
        }
        try {
            return AmqpProperties.mapPriorityToAmhs(subject.getPriority());
        } catch (IllegalArgumentException e) {
            log.warn("gwin#{} có AMQP priority {} ngoài dải 0-9, không suy được ATS priority",
                    subject.getMsgid(), subject.getPriority());
            return null;
        }
    }

    /**
     * Lấy mô tả lỗi của NDR cho Control Position.
     */
    private String describeDiagnostic(AmhsFeedback feedback) {
        if (feedback.getDiagnosticCode() != null && !feedback.getDiagnosticCode().isBlank()) {
            return feedback.getDiagnosticCode().trim();
        }
        if (feedback.getReasonCode() != null && !feedback.getReasonCode().isBlank()) {
            return feedback.getReasonCode().trim();
        }
        return "unable-to-transfer";
    }

}
