package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.converter.ConverterFacade;
import vn.asg.converter.core.ConversionResult;
import vn.asg.converter.core.OutputFormat;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.MessageConversionLog;
import vn.asg.swim.repository.MessageConversionLogRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logic chuyển đổi định dạng bản tin (conversion) cho luồng SWIM <-> AMHS.
 * Ánh xạ (map) độ ưu tiên, OHI, body part type, và filing time theo đặc tả Spec §4.3.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageConversionService {

    private final MessageConversionLogRepository conversionLogRepo;
    private final ConverterFacade converterFacade;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Chuyển đổi định dạng AMHS (TAC) sang SWIM (JSON).
     */
    public String toSwim(String amhsBody, String messageType) throws Exception {
        if (amhsBody == null || amhsBody.isBlank()) {
            return "";
        }

        ConversionResult result = converterFacade.convert(amhsBody, messageType, OutputFormat.JSON);
        if (!result.isSuccess()) {
            throw new Exception("Conversion to JSON failed: " + result.getErrorMessage());
        }
        return result.getPayload();
    }

    /**
     * Chuyển đổi định dạng SWIM (JSON/XML) ngược lại AMHS (TAC).
     */
    public String toAmhs(String swimBody, String messageType) throws Exception {
        if (swimBody == null || swimBody.isBlank()) {
            return "";
        }

        // Nếu payload đã ở định dạng TAC (bắt đầu bằng ký tự '('), trả về luôn
        if (swimBody.trim().startsWith("(")) {
            return swimBody.trim();
        }

        ConversionResult result = converterFacade.convert(swimBody, messageType, OutputFormat.TEXT);
        if (!result.isSuccess()) {
            throw new Exception(result.getErrorMessage());
        }
        return result.getPayload();
    }

    // ─── Priority Mapping §4.3.1 ──────────────────────────────────────────────

    /**
     * Ánh xạ chuỗi priority ATS sang giá trị numeric priority AMQP.
     */
    public byte mapAtsPriorityToAmqp(String atsPriority) {
        return (byte) vn.asg.swim.model.AmqpProperties.mapAtsPriorityToAmqp(atsPriority);
    }

    /**
     * SWIM → AMHS: Ánh xạ priority AMQP (0-9) sang chuỗi ATS.
     */
    public String mapAmqpPriorityToAts(int amqpPriority) {
        return vn.asg.swim.model.AmqpProperties.mapPriorityToAts(amqpPriority);
    }

    // ─── OHI §4.3.6 ──────────────────────────────────────────────────────────

    /**
     * Cắt ngắn OHI theo các quy tắc trong §4.3.6.
     *
     * @param ohi          Giá trị OHI gốc
     * @param amqpPriority Độ ưu tiên AMQP (0-9)
     * @return OHI đã qua xử lý hoặc null nếu rỗng
     */
    public String processOhi(String ohi, int amqpPriority) {
        if (ohi == null || ohi.isBlank())
            return null;
        int maxLen = (amqpPriority >= 6) ? 48 : 53;
        return ohi.length() > maxLen ? ohi.substring(0, maxLen) : ohi;
    }

    // ─── amhs_content_encoding §4.3.3 ────────────────────────────────────────

    /**
     * Ánh xạ body part type sang dạng mã hóa (encoding) tương ứng.
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
     * Ghi log sau khi publish AMQP thành công (chiều AMHS → SWIM).
     * EUR Doc 047 §4.3.4e,f (G-13, G-14): Log MTS-ID và IPM-ID
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
     * Phương thức nạp chồng (overload) để tương thích ngược (không có MTS/IPM ID)
     */
    /**
     * Ghi log chuyển đổi chiều AMHS -> SWIM (tương thích ngược).
     */
    public void logAmhsToSwim(Gwout gwout, String amqpMessageId, String status, String actionTaken) {
        logAmhsToSwim(gwout, amqpMessageId, status, actionTaken, null, null);
    }

    /**
     * Ghi log sau khi nhận bản tin AMQP và ghi vào Gwin (chiều SWIM → AMHS).
     * EUR Doc 047 §4.3.4f (G-14): Log IPM-ID nếu có.
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
     * Phương thức nạp chồng để tương thích ngược (dành cho các nhánh từ chối bản tin).
     */
    /**
     * Ghi log chuyển đổi chiều SWIM -> AMHS (tương thích ngược).
     */
    public void logSwimToAmhs(String amqpMessageId, String originator,
            String status, String actionTaken, String rejectionReason) {
        logSwimToAmhs(amqpMessageId, originator, status, actionTaken, rejectionReason, null);
    }

    // Các điều khoản tuân thủ EUR Doc 047 được xử lý thông qua gọi AlertService trong các dispatch services.

    /**
     * Kiểm tra tính hợp lệ của filing_time: phải có đúng 6 chữ số (DDhhmm).
     */
    /**
     * Kiểm tra tính hợp lệ của thời gian nộp bản tin (Filing Time).
     */
    public boolean isValidFilingTime(String ft) {
        return ft != null && ft.matches("\\d{6}");
    }
}
