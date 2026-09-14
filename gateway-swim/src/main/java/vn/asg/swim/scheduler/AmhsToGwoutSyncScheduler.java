package vn.asg.swim.scheduler;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.OutboundStatus;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.util.AddressUtil;
import vn.asg.swim.model.AmqpProperties;
import vn.asg.swim.service.AlertService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler to sync messages from AMHS tables 
 */
@Component
@Slf4j
public class AmhsToGwoutSyncScheduler {

    @PersistenceContext
    private final EntityManager entityManager;
    private final GwoutRepository gwoutRepository;
    private final AlertService alertService;

    public AmhsToGwoutSyncScheduler(EntityManager entityManager, GwoutRepository gwoutRepository, AlertService alertService) {
        this.entityManager = entityManager;
        this.gwoutRepository = gwoutRepository;
        this.alertService = alertService;
    }


    private String asString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return obj.toString();
    }

    /**
     * Đọc một cột kiểu số từ dòng kết quả, trả về -1 nếu thiếu hoặc không hợp lệ.
     */
    private int asInt(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) return -1;
        if (row[index] instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(asString(row[index]).trim());
        } catch (NumberFormatException e) {
            log.warn("Giá trị số '{}' không hợp lệ ở cột {} của kết quả mtcu", row[index], index);
            return -1;
        }
    }

    /**
     * Kiểm tra recipient có responsibility = responsible.
     */
    private boolean isResponsible(Object[] row) {
        if (row == null || row.length <= 21 || row[21] == null) {
            return false;
        }
        Object value = row[21];
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        if (value instanceof byte[] bytes) return bytes.length > 0 && bytes[0] != 0;
        String s = value.toString().trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    /**
     * Nạp nội dung nhị phân của file đính kèm (mtcu_tmp.data) cho một bản tin.
     */
    private byte[] loadFtbpData(Long msgTmpId) {
        try {
            Object result = entityManager
                    .createNativeQuery("SELECT data FROM mtcu_tmp WHERE id = :id")
                    .setParameter("id", msgTmpId)
                    .getSingleResult();
            if (result instanceof byte[] bytes) {
                return bytes;
            }
            if (result != null) {
                log.warn("mtcu_tmp#{}: cột data có kiểu {} không mong đợi, bỏ qua",
                        msgTmpId, result.getClass().getName());
            }
            return null;
        } catch (Exception e) {
            log.error("mtcu_tmp#{}: không nạp được nội dung file đính kèm: {}", msgTmpId, e.getMessage());
            return null;
        }
    }

    /**
     * Ánh xạ charset thô sang repertoire chuẩn (ISO-646 / ISO-8859-1).
     */
    private String mapBodyPartCharset(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toUpperCase();

        if (normalized.contains("ITA2")) return "ITA2";
        if (normalized.contains("8859-1") || normalized.contains("8859 1")) return "ISO-8859-1";
        if (normalized.contains("646") || normalized.contains("ASCII") || normalized.contains("IA5")) return "ISO-646";

        if (normalized.matches("[0-9]+([,;\\s]+[0-9]+)*")) {
            java.util.Set<Integer> regs = new java.util.LinkedHashSet<>();
            for (String tok : normalized.split("[,;\\s]+")) {
                try {
                    regs.add(Integer.valueOf(tok));
                } catch (NumberFormatException ignored) {
                }
            }
            if (regs.contains(100)) return "ISO-8859-1";
            java.util.Set<Integer> iso646 = java.util.Set.of(1, 6);
            if (iso646.containsAll(regs)) return "ISO-646";
            for (Integer reg : regs) {
                if (!iso646.contains(reg)) {
                    return "ISO-REG-" + reg;
                }
            }
        }

        if (normalized.contains("8859")) return "ISO-8859-1";
        log.warn("Unrecognized bodyPartCharacterSet value '{}' from mtcu_tmp, leaving amhs_content_encoding unset", raw);
        return null;
    }

    /**
     * Quét mtcu_tmp lấy bản tin AMHS và đồng bộ sang gwout.
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 1000)
    public void syncAmhsToGwout() {
        log.info("AMHS sync scheduler tick - checking mtcu_tmp...");
        try {
            String sql = """
                SELECT
                    t.id,
                    t.content,
                    t.atsFilingTime,
                    t.atsPriority,
                    t.atsOhi,
                    t.bodyPartType,
                    t.ipmId,
                    t.messageId,
                    t.orAddress,
                    o.address AS recipient_address,
                    t.bodyPartCharacterSet,
                    t.file_name,
                    OCTET_LENGTH(t.data) AS data_size,
                    NULL AS unused_ftbp_last_mod,
                    t.numberOfAttachment,
                    t.originEncodeInformationType,
                    o.reportRequest,
                    o.mtaReportRequest,
                    t.contentType,
                    t.subject,
                    o.precedence,
                    o.responsibility
                FROM (
                    SELECT id, content, atsFilingTime, atsPriority, atsOhi, bodyPartType, ipmId, messageId, orAddress, bodyPartCharacterSet, file_name, data, numberOfAttachment, originEncodeInformationType, contentType, subject
                    FROM mtcu_tmp m
                    WHERE m.messageId IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM gwout g WHERE g.amhsid = m.messageId
                      )
                      AND EXISTS (
                          SELECT 1
                          FROM mtcu_to gw
                          JOIN routing r ON r.direction = 'OUT' AND r.active = 1
                          WHERE gw.receiveMessage_id = m.id
                            AND (
                                  r.recipients LIKE CONCAT('%',
                                      SUBSTRING_INDEX(SUBSTRING_INDEX(gw.address, '/CN=', -1), '/', 1), '%')
                               OR r.recipients LIKE '%*%'
                            )
                      )
                    ORDER BY m.id ASC
                    LIMIT 200
                ) t
                JOIN mtcu_to o ON t.id = o.receiveMessage_id
                ORDER BY t.id ASC, o.id ASC
            """;

            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(sql)
                    .getResultList();

            log.info("AMHS sync query returned {} rows", rows != null ? rows.size() : 0);

            if (rows == null || rows.isEmpty()) {
                return;
            }

            java.util.LinkedHashMap<Long, List<Object[]>> groupedByMessage = new java.util.LinkedHashMap<>();
            for (Object[] row : rows) {
                Long msgTmpId = ((Number) row[0]).longValue();
                groupedByMessage.computeIfAbsent(msgTmpId, k -> new java.util.ArrayList<>()).add(row);
            }

            log.info("Found {} new AMHS messages to sync to gwout", groupedByMessage.size());

            for (List<Object[]> msgRows : groupedByMessage.values()) {
                Object[] row = msgRows.get(0);
                try {
                    Long msgTmpId = ((Number) row[0]).longValue();
                    String content = asString(row[1]);
                    String atsFilingTime = asString(row[2]);
                    String atsPriority = asString(row[3]);
                    String atsOhi = asString(row[4]);
                    String bodyPartType = asString(row[5]);
                    String ipmId = asString(row[6]);
                    String messageId = asString(row[7]);
                    String orAddress = asString(row[8]);
                    String bodyPartCharacterSet = asString(row[10]);
                    String ftbpFileName = asString(row[11]);
                    long ftbpDataSize = row[12] instanceof Number n ? n.longValue() : 0L;
                    Integer numberOfAttachment = null;
                    if (row.length > 14 && row[14] != null) {
                        try {
                            numberOfAttachment = Integer.valueOf(asString(row[14]).trim());
                        } catch (NumberFormatException ignored) {
                            log.warn("Giá trị numberOfAttachment '{}' không hợp lệ từ mtcu_tmp", row[14]);
                        }
                    }

                    boolean hasResponsibilityData = msgRows.stream()
                            .anyMatch(r -> r.length > 21 && r[21] != null);
                    List<String> realRecipients = msgRows.stream()
                            .filter(r -> !hasResponsibilityData || isResponsible(r))
                            .map(r -> AddressUtil.getShort(asString(r[9])))
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .toList();

                    Integer highestPrecedence = msgRows.stream()
                            .filter(r -> !hasResponsibilityData || isResponsible(r))
                            .map(r -> r.length > 20 && r[20] instanceof Number n ? n.intValue() : null)
                            .filter(java.util.Objects::nonNull)
                            .max(Integer::compareTo)
                            .orElse(null);

                    Gwout gwout = new Gwout();
                    if (messageId != null && messageId.length() > 200) messageId = messageId.substring(0, 200);
                    gwout.setAmhsid(messageId);
                    if (ipmId != null && ipmId.length() > 200) ipmId = ipmId.substring(0, 200);
                    gwout.setIpmId(ipmId);
                    gwout.setText(content);
                    gwout.setTime(LocalDateTime.now());
                    if (atsFilingTime != null && atsFilingTime.length() > 32) {
                        atsFilingTime = atsFilingTime.substring(0, 32);
                    }
                    gwout.setFilingTime(atsFilingTime);
                    if (atsOhi != null && atsOhi.length() > 255) {
                        atsOhi = atsOhi.substring(0, 255);
                    }
                    gwout.setOptionalHeading(atsOhi);
                    if (atsPriority != null && atsPriority.length() > 10) atsPriority = atsPriority.substring(0, 10);
                    gwout.setAmhsPriority(atsPriority);
                    String standardBodyPartType = null;
                    String bodyType = "text";
                    if (bodyPartType != null) {
                        if ("401".equals(bodyPartType) || "ia5-text".equalsIgnoreCase(bodyPartType) || "ia5-text-body-part".equalsIgnoreCase(bodyPartType)) {
                            standardBodyPartType = "ia5-text-body-part";
                            bodyType = "text";
                        } else if ("402".equals(bodyPartType) || "407".equals(bodyPartType) || "general-text".equalsIgnoreCase(bodyPartType) || "general-text-body-part".equalsIgnoreCase(bodyPartType)) {
                            standardBodyPartType = "general-text-body-part";
                            bodyType = "text";
                        } else if ("403".equals(bodyPartType) || "file-transfer".equalsIgnoreCase(bodyPartType) || "file-transfer-body-part".equalsIgnoreCase(bodyPartType)) {
                            standardBodyPartType = "file-transfer-body-part";
                            bodyType = "ftbp";
                        } else {
                            standardBodyPartType = bodyPartType;
                            bodyType = "text";
                            log.warn("bodyPartType '{}' không thuộc danh sách cho phép, giữ nguyên để từ chối ở bước sau",
                                    bodyPartType);
                        }
                    }
                    if (ftbpDataSize > 0) {
                        standardBodyPartType = "file-transfer-body-part";
                        bodyType = "ftbp";
                    }
                    gwout.setBodyPartType(standardBodyPartType);
                    gwout.setBodyType(bodyType);
                    gwout.setNumberOfAttachment(numberOfAttachment);
                    gwout.setOriginEit(row.length > 15 ? asString(row[15]) : null);
                    int rawContentType = asInt(row, 18);
                    gwout.setX400ContentType(rawContentType >= 0 ? rawContentType : null);

                    String subject = row.length > 19 ? asString(row[19]) : null;
                    if (subject != null && subject.length() > 200) subject = subject.substring(0, 200);
                    gwout.setSubject(subject);

                    gwout.setAmhsDeliveryReport(msgRows.stream().anyMatch(r ->
                            asInt(r, 16) == 2
                                    || asInt(r, 17) == 2
                                    || asInt(r, 17) == 3));
                    if ("general-text-body-part".equals(standardBodyPartType)
                            || "ia5-text-body-part".equals(standardBodyPartType)) {
                        gwout.setBodyPartCharset(mapBodyPartCharset(bodyPartCharacterSet));
                    }
                    if ("file-transfer-body-part".equals(standardBodyPartType)) {
                        gwout.setFtbpFileName(ftbpFileName != null && ftbpFileName.length() > 255
                                ? ftbpFileName.substring(0, 255) : ftbpFileName);
                        if (ftbpDataSize > 0) {
                            gwout.setFtbpObjectSize(String.valueOf(ftbpDataSize));
                        }
                        byte[] ftbpData = loadFtbpData(msgTmpId);
                        if (ftbpData != null && ftbpData.length > 0) {
                            gwout.setText(java.util.Base64.getEncoder().encodeToString(ftbpData));
                        }
                    }

                    String shortOrigin = AddressUtil.getShort(orAddress);
                    String finalOrigin = shortOrigin != null ? shortOrigin : orAddress;
                    if (finalOrigin != null && finalOrigin.length() > 200) finalOrigin = finalOrigin.substring(0, 200);
                    gwout.setOrigin(finalOrigin);
                    
                    if (realRecipients.isEmpty()) {
                        log.warn("gwout sync: messageId={} không có recipient nào 'responsible', không chuyển sang SWIM",
                                messageId);
                        alertService.create(
                                GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                                "Bản tin " + messageId + " không có recipient mang responsibility 'responsible'",
                                "mtcu_tmp", msgTmpId);
                        gwout.setAddress("");
                        gwout.setStatus(OutboundStatus.FAILED.getValue());
                        gwout.setRejectionReason("no-responsible-recipient");
                        gwoutRepository.saveAndFlush(gwout);
                        continue;
                    }
                    gwout.setAddress(String.join(",", realRecipients));
                    
                    gwout.setPrecedence(highestPrecedence);
                    String effectivePriority = AmqpProperties.mapPrecedenceToAtsPriority(highestPrecedence);
                    if (effectivePriority != null) {
                        gwout.setAmhsPriority(effectivePriority);
                    } else {
                        effectivePriority = atsPriority;
                        if (highestPrecedence != null) {
                            log.warn("gwout sync: messageId={} có precedence {} không thuộc danh mục quy định, "
                                    + "giữ ATS-message-priority '{}'", messageId, highestPrecedence, atsPriority);
                        }
                    }
                    gwout.setSwimPriority(AmqpProperties.mapAtsPriorityToAmqp(effectivePriority));

                    gwout.setStatus(OutboundStatus.PENDING.getValue());

                    gwoutRepository.saveAndFlush(gwout);
                    log.info("Synced AMHS message ID {} -> gwout#{}", messageId, gwout.getMsgid());

                } catch (Exception e) {
                    log.error("Failed to sync AMHS message row: {}", e.getMessage(), e);
                    try {
                        Long failedId = row[0] instanceof Number n ? n.longValue() : null;
                        alertService.create(
                                GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                                "Không đồng bộ được bản tin AMHS mtcu_tmp#" + failedId
                                        + " sang gwout: " + e.getMessage(),
                                "mtcu_tmp", failedId);
                    } catch (Exception alertError) {
                        log.error("Không ghi được cảnh báo đồng bộ: {}", alertError.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AMHS to gwout sync check error: {}", e.getMessage());
        }
    }
}