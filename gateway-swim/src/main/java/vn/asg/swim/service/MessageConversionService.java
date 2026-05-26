package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.converter.ConverterFacade;
import vn.asg.converter.core.ConversionResult;
import vn.asg.converter.core.OutputFormat;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.MessageConversionLog;
import vn.asg.swim.exception.ConversionException;
import vn.asg.swim.repository.MessageConversionLogRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logic chuyển đổi định dạng bản tin (conversion) cho luồng SWIM và AMHS.
 * Ánh xạ độ ưu tiên, OHI, body part type và filing time theo đặc tả.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageConversionService {

    private final MessageConversionLogRepository conversionLogRepo;
    private final ConverterFacade converterFacade;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Chuyển đổi TAC sang JSON (chiều AMHS sang SWIM).
     */
    public String toSwim(String amhsBody, String messageType) throws ConversionException {
        if (amhsBody == null || amhsBody.isBlank()) {
            return "";
        }

        ConversionResult result = converterFacade.convert(amhsBody, messageType, OutputFormat.JSON);
        if (!result.isSuccess()) {
            throw new ConversionException(result.getErrorMessage(), messageType, "TAC", "JSON");
        }
        return result.getPayload();
    }

    /**
     * Chuyển đổi JSON sang TAC (chiều SWIM sang AMHS).
     */
    public String toAmhs(String swimBody, String messageType) throws ConversionException {
        if (swimBody == null || swimBody.isBlank()) {
            return "";
        }

        // Đã là định dạng TAC rồi, trả về luôn
        if (swimBody.trim().startsWith("(")) {
            return swimBody.trim();
        }

        ConversionResult result = converterFacade.convert(swimBody, messageType, OutputFormat.TEXT);
        if (!result.isSuccess()) {
            throw new ConversionException(result.getErrorMessage(), messageType, "JSON", "TAC");
        }
        return result.getPayload();
    }

    /**
     * Ánh xạ độ ưu tiên ATS (SS/DD/FF/GG/KK) sang giá trị số AMQP (0-9).
     */
    public byte mapAtsPriorityToAmqp(String atsPriority) {
        return (byte) vn.asg.swim.model.AmqpProperties.mapAtsPriorityToAmqp(atsPriority);
    }

    /**
     * Ánh xạ độ ưu tiên AMQP (0-9) sang định dạng ATS (SS/DD/FF/GG/KK).
     */
    public String mapAmqpPriorityToAts(int amqpPriority) {
        return vn.asg.swim.model.AmqpProperties.mapPriorityToAts(amqpPriority);
    }

    /**
     * Cắt ngắn thông tin OHI theo đặc tả: độ ưu tiên >= 6 tối đa 48 ký tự, ngược lại tối đa 53 ký tự.
     */
    public String processOhi(String ohi, int amqpPriority) {
        if (ohi == null || ohi.isBlank())
            return null;
        int maxLen = (amqpPriority >= 6) ? 48 : 53;
        return ohi.length() > maxLen ? ohi.substring(0, maxLen) : ohi;
    }

    /**
     * Ánh xạ loại body part sang bảng mã tương ứng (IA5, ISO-646, ISO-8859-1).
     */
    public String mapBodyPartTypeToEncoding(String bodyPartType) {
        if (bodyPartType == null)
            return null;
        return switch (bodyPartType.toLowerCase()) {
            case "ia5-text", "ia5-text-body-part" -> "IA5";
            case "general-text-body-part", "general-text-body-part-iso-646",
                    "general-text-body-part (iso-646)" ->
                "ISO-646";
            case "general-text-body-part-iso-8859-1",
                    "general-text-body-part (iso-8859-1)" ->
                "ISO-8859-1";
            default -> null;
        };
    }

    /**
     * Ghi log chuyển đổi chiều gửi đi AMHS sang SWIM.
     */
    public void logAmhsToSwim(Gwout gwout, String amqpMessageId, String status, String actionTaken,
            String mtsId, String ipmId) {
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
            logEntry.setPriority(vn.asg.swim.model.AmqpProperties.mapPriorityToAts(gwout.getPriority() != null ? gwout.getPriority() : 2));
            logEntry.setOhi(gwout.getOptionalHeading());
            logEntry.setOrigin(gwout.getOrigin());
            logEntry.setFilingTime(gwout.getFilingTime());
            logEntry.setContent(gwout.getText());
            logEntry.setConvertedTime(LocalDateTime.now());
            logEntry.setActionTaken(actionTaken);
            logEntry.setStatus(status);
            conversionLogRepo.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to write conversion log for gwout#{}: {}", gwout.getMsgid(), e.getMessage());
        }
    }

    /**
     * Log chuyển đổi AMHS sang SWIM (không kèm theo MTS/IPM ID).
     */
    public void logAmhsToSwim(Gwout gwout, String amqpMessageId, String status, String actionTaken) {
        logAmhsToSwim(gwout, amqpMessageId, status, actionTaken, null, null);
    }

    /**
     * Ghi log chuyển đổi chiều nhận về SWIM sang AMHS.
     */
    public void logSwimToAmhs(String amqpMessageId, String originator,
            String status, String actionTaken,
            String rejectionReason, String ipmId) {
        try {
            MessageConversionLog logEntry = new MessageConversionLog();
            logEntry.setDate(LocalDate.now().format(DATE_FMT));
            logEntry.setType("SWIM");
            logEntry.setCategory("IN");
            logEntry.setAmqpMessageId(amqpMessageId);
            logEntry.setIpmId(ipmId);
            logEntry.setOrigin(originator);
            logEntry.setConvertedTime(LocalDateTime.now());
            logEntry.setActionTaken(actionTaken);
            logEntry.setStatus(status);
            logEntry.setNonDeliveryReason(rejectionReason);
            conversionLogRepo.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to write conversion log for AMQP {}: {}", amqpMessageId, e.getMessage());
        }
    }

    /**
     * Log chuyển đổi SWIM sang AMHS (không kèm theo IPM-ID).
     */
    public void logSwimToAmhs(String amqpMessageId, String originator,
            String status, String actionTaken, String rejectionReason) {
        logSwimToAmhs(amqpMessageId, originator, status, actionTaken, rejectionReason, null);
    }

    /**
     * Kiểm tra định dạng thời gian nộp (filing time) gồm 6 chữ số (DDhhmm).
     */
    public boolean isValidFilingTime(String ft) {
        return ft != null && ft.matches("\\d{6}");
    }
}
