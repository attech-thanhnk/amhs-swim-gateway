package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.MessageConversionLog;
import vn.asg.swim.repository.MessageConversionLogRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Ánh xạ độ ưu tiên, OHI, body part type, filing time và ghi log chuyển tiếp
 * bản tin giữa luồng SWIM và AMHS. Nội dung bản tin (body) được giữ nguyên,
 * không convert định dạng ở cả hai chiều (theo ICAO Doc 047).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageConversionService {

    private final MessageConversionLogRepository conversionLogRepo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Ghi log chuyển đổi chiều gửi đi AMHS sang SWIM.
     */
    public void logAmhsToSwim(Gwout gwout, String amqpMessageId, String status, String actionTaken,
            String mtsId, String ipmId, String nonDeliveryDiagnostic, String supplementaryInfo) {
        try {
            MessageConversionLog logEntry = new MessageConversionLog();
            logEntry.setDate(LocalDate.now().format(DATE_FMT));
            logEntry.setType("AMHS");
            logEntry.setCategory("OUT");
            logEntry.setReferenceId(gwout.getMsgid());
            logEntry.setMessageId(gwout.getAmhsid());
            logEntry.setMtsId(mtsId);
            logEntry.setIpmId(ipmId);
            logEntry.setAmqpMessageId(amqpMessageId);
            logEntry.setPriority(gwout.getAmhsPriority() != null ? gwout.getAmhsPriority() : "KK");
            logEntry.setOhi(gwout.getOptionalHeading());
            logEntry.setOrigin(gwout.getOrigin());
            logEntry.setFilingTime(gwout.getFilingTime());
            logEntry.setContent(gwout.getText());
            logEntry.setConvertedTime(LocalDateTime.now());
            if (actionTaken != null && actionTaken.length() > 255) {
                logEntry.setActionTaken(actionTaken.substring(0, 255));
                logEntry.setRemark(actionTaken);
            } else {
                logEntry.setActionTaken(actionTaken);
            }
            logEntry.setStatus(status);
            // EUR Doc 047 §4.3.1.2(d)/§4.4.8: NDR reason-code is always "unable-to-transfer"
            // in every rejection scenario this ITCU implements; only the diagnostic-code varies.
            if (nonDeliveryDiagnostic != null) {
                logEntry.setNonDeliveryReason("unable-to-transfer");
                logEntry.setNonDeliveryDiagnostic(nonDeliveryDiagnostic);
            }
            if (supplementaryInfo != null) {
                logEntry.setSupplementaryInfo(
                        supplementaryInfo.length() > 512 ? supplementaryInfo.substring(0, 512) : supplementaryInfo);
            }
            conversionLogRepo.saveAndFlush(logEntry);
        } catch (Exception e) {
            log.error("Failed to write conversion log for gwout#{}: {}", gwout.getMsgid(), e.getMessage());
        }
    }

    /**
     * Log chuyển đổi AMHS sang SWIM, tự lấy MTS-Identifier/IPM-Identifier từ gwout.
     */
    public void logAmhsToSwim(Gwout gwout, String amqpMessageId, String status, String actionTaken) {
        logAmhsToSwim(gwout, amqpMessageId, status, actionTaken, gwout.getAmhsid(), gwout.getIpmId(), null, null);
    }

    /**
     * Log bản tin AMHS→SWIM bị từ chối kèm NDR non-delivery-diagnostic-code theo ma trận
     * EUR Doc 047 §4.4.1-§4.4.2/§4.4.6 (reason-code luôn là "unable-to-transfer").
     */
    public void logAmhsToSwimRejected(Gwout gwout, String actionTaken, String nonDeliveryDiagnostic,
            String supplementaryInfo) {
        logAmhsToSwim(gwout, null, "REJECTED", actionTaken, gwout.getAmhsid(), gwout.getIpmId(),
                nonDeliveryDiagnostic, supplementaryInfo);
    }

    /**
     * Ghi log chuyển đổi chiều nhận về SWIM sang AMHS.
     */
    public void logSwimToAmhs(String amqpMessageId, String originator,
            String status, String actionTaken,
            String rejectionReason, String ipmId, String content) {
        try {
            MessageConversionLog logEntry = new MessageConversionLog();
            logEntry.setDate(LocalDate.now().format(DATE_FMT));
            logEntry.setType("SWIM");
            logEntry.setCategory("IN");
            logEntry.setAmqpMessageId(amqpMessageId);
            logEntry.setIpmId(ipmId);
            logEntry.setOrigin(originator);
            logEntry.setContent(content);
            logEntry.setConvertedTime(LocalDateTime.now());
            if (actionTaken != null && actionTaken.length() > 255) {
                logEntry.setActionTaken(actionTaken.substring(0, 255));
                logEntry.setRemark(actionTaken.length() > 1000 ? actionTaken.substring(0, 1000) : actionTaken);
            } else {
                logEntry.setActionTaken(actionTaken);
            }
            logEntry.setStatus(status != null && status.length() > 8 ? status.substring(0, 8) : status);
            if (rejectionReason != null) {
                if (rejectionReason.length() > 64) {
                    logEntry.setNonDeliveryReason(rejectionReason.substring(0, 64));
                    if (logEntry.getRemark() == null) {
                        logEntry.setRemark(rejectionReason.length() > 1000 ? rejectionReason.substring(0, 1000) : rejectionReason);
                    }
                } else {
                    logEntry.setNonDeliveryReason(rejectionReason);
                }
            }
            conversionLogRepo.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to write conversion log for AMQP {}: {}", amqpMessageId, e.getMessage());
        }
    }

    public void logSwimToAmhs(String amqpMessageId, String originator,
            String status, String actionTaken,
            String rejectionReason, String ipmId) {
        logSwimToAmhs(amqpMessageId, originator, status, actionTaken, rejectionReason, ipmId, null);
    }

    /**
     * Log chuyển đổi SWIM sang AMHS (không kèm theo IPM-ID).
     */
    public void logSwimToAmhs(String amqpMessageId, String originator,
            String status, String actionTaken, String rejectionReason) {
        logSwimToAmhs(amqpMessageId, originator, status, actionTaken, rejectionReason, null, null);
    }

}
