package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.converter.ConverterFacade;
import vn.asg.converter.ReverterFacade;
import vn.asg.converter.core.ConversionResult;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.MessageConversionLog;
import vn.asg.swim.repository.MessageConversionLogRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Conversion logic for the SWIM <-> AMHS pipeline.
 * Maps priority, OHI, body part type, and filing time according to Spec §4.3.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageConversionService {

    private final MessageConversionLogRepository conversionLogRepo;
    private final ConverterFacade converterFacade = new ConverterFacade();
    private final ReverterFacade reverterFacade = new ReverterFacade();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Converts AMHS plain text body → SWIM format (XML).
     * Uses gateway-converter to generate IWXXM, FIXM, or AIXM.
     */
    public String toSwim(String amhsBody, String messageType) {
        if (amhsBody == null || amhsBody.isBlank())
            return "";

        try {
            log.debug("Converting AMHS message to SWIM (Type: {})", messageType);
            ConversionResult result = converterFacade.convert(amhsBody);

            if (result.isSuccess()) {
                return result.getXml();
            } else if (result.getStatus() == ConversionResult.Status.UNSUPPORTED) {
                // Message type không support XML conversion (VD: CHG, CNL, DLA)
                // → Gateway forward TAC text thay vì XML để đảm bảo message được gửi
                log.info("Message type {} not supported for XML conversion - forwarding as TAC text", messageType);
                return result.getOriginalTac();
            } else {
                throw new RuntimeException(result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Failed to convert message to SWIM format: {}", e.getMessage());
            throw new RuntimeException("Conversion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Converts SWIM body (XML) → AMHS plain text format (TAC).
     * Uses gateway-converter's ReverterFacade.
     */
    public String toAmhs(String swimBody, String subject) {
        if (swimBody == null || swimBody.isBlank())
            return "";

        try {
            log.debug("Reverting SWIM message to AMHS (Subject: {})", subject);
            String tac = reverterFacade.revert(swimBody);
            if (tac == null) {
                throw new RuntimeException("Auto-revert failed or unknown XML format");
            }
            return tac;
        } catch (Exception e) {
            log.error("Failed to revert message to AMHS format: {}", e.getMessage());
            throw new RuntimeException("Revert failed: " + e.getMessage(), e);
        }
    }

    // ─── Priority Mapping §4.3.1 ──────────────────────────────────────────────

    /**
     * Converts priority (int 0-9) to ATS priority string (SS/DD/FF/GG/KK).
     */
    public String mapPriorityToAts(int amqpPriority) {
        return switch (amqpPriority) {
            case 6 -> "SS";
            case 5 -> "DD";
            case 4 -> "FF";
            case 3 -> "GG";
            default -> "KK";
        };
    }

    /**
     * Converts ATS priority string to AMQP numeric priority.
     */
    public byte mapAtsPriorityToAmqp(String atsPriority) {
        if (atsPriority == null)
            return 2;
        return switch (atsPriority.toUpperCase()) {
            case "SS" -> 6;
            case "DD" -> 5;
            case "FF" -> 4;
            case "GG" -> 3;
            default -> 2;
        };
    }

    /**
     * SWIM → AMHS: Maps AMQP priority (0-9) to ATS string.
     */
    public String mapAmqpPriorityToAts(int amqpPriority) {
        if (amqpPriority >= 6)
            return "SS";
        if (amqpPriority == 5)
            return "DD";
        if (amqpPriority == 4)
            return "FF";
        if (amqpPriority == 3)
            return "GG";
        return "KK";
    }

    // ─── OHI §4.3.6 ──────────────────────────────────────────────────────────

    /**
     * Truncates OHI according to rules in §4.3.6.
     *
     * @param ohi          Original OHI value
     * @param amqpPriority AMQP priority (0-9)
     * @return Processed OHI or null if empty
     */
    public String processOhi(String ohi, int amqpPriority) {
        if (ohi == null || ohi.isBlank())
            return null;
        int maxLen = (amqpPriority >= 6) ? 48 : 53;
        return ohi.length() > maxLen ? ohi.substring(0, maxLen) : ohi;
    }

    // ─── amhs_content_encoding §4.3.3 ────────────────────────────────────────

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
     * Logs after successful AMQP publish (AMHS → SWIM direction).
     * EUR Doc 047 §4.3.4e,f (G-13, G-14): Log MTS-ID and IPM-ID
     */
    public void logAmhsToSwim(Gwout gwout, String amqpMessageId, String status, String actionTaken,
            String mtsId, String ipmId) {
        try {
            conversionLogRepo.save(MessageConversionLog.builder()
                    .date(LocalDate.now().format(DATE_FMT))
                    .type("AMHS")
                    .category("OUT")
                    .referenceId(gwout.getMsgid())
                    .messageId(gwout.getAmhsid())
                    .mtsId(mtsId) // EUR Doc 047 §4.3.4e (G-13)
                    .ipmId(ipmId) // EUR Doc 047 §4.3.4f (G-14)
                    .amqpMessageId(amqpMessageId)
                    .priority(mapPriorityToAts(gwout.getPriority() != null ? gwout.getPriority() : 2))
                    .ohi(gwout.getOptionalHeading())
                    .origin(gwout.getOrigin())
                    .filingTime(gwout.getFilingTime())
                    .content(gwout.getText())
                    .convertedTime(LocalDateTime.now())
                    .actionTaken(actionTaken)
                    .status(status)
                    .build());
        } catch (Exception e) {
            log.error("Failed to write conversion log for gwout#{}: {}", gwout.getMsgid(), e.getMessage());
        }
    }

    /**
     * Overload method for backward compatibility (without MTS/IPM IDs)
     */
    public void logAmhsToSwim(Gwout gwout, String amqpMessageId, String status, String actionTaken) {
        logAmhsToSwim(gwout, amqpMessageId, status, actionTaken, null, null);
    }

    /**
     * Logs after receiving AMQP and writing to Gwin (SWIM → AMHS direction).
     * EUR Doc 047 §4.3.4f (G-14): Log IPM-ID if available.
     */
    public void logSwimToAmhs(String amqpMessageId, String originator,
            String status, String actionTaken,
            String rejectionReason, String ipmId) {
        try {
            conversionLogRepo.save(MessageConversionLog.builder()
                    .date(LocalDate.now().format(DATE_FMT))
                    .type("SWIM")
                    .category("IN")
                    .amqpMessageId(amqpMessageId)
                    .ipmId(ipmId)
                    .origin(originator)
                    .convertedTime(LocalDateTime.now())
                    .actionTaken(actionTaken)
                    .status(status)
                    .nonDeliveryReason(rejectionReason)
                    .build());
        } catch (Exception e) {
            log.error("Failed to write conversion log for AMQP {}: {}", amqpMessageId, e.getMessage());
        }
    }

    /**
     * Overload without ipmId for backward compatibility (rejection paths).
     */
    public void logSwimToAmhs(String amqpMessageId, String originator,
            String status, String actionTaken, String rejectionReason) {
        logSwimToAmhs(amqpMessageId, originator, status, actionTaken, rejectionReason, null);
    }

    // EUR Doc 047 compliance items handled via AlertService calls in dispatch
    // services.

    /**
     * Validate filing_time: must be exactly 6 digits (DDhhmm).
     */
    public boolean isValidFilingTime(String ft) {
        return ft != null && ft.matches("\\d{6}");
    }
}
