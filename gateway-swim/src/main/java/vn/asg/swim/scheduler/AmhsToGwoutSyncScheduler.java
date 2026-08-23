package vn.asg.swim.scheduler;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.OutboundStatus;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.util.AddressUtil;
import vn.asg.swim.model.AmqpProperties;
import vn.asg.swim.service.ConfigService;

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
    private final ConfigService configService;

    public AmhsToGwoutSyncScheduler(EntityManager entityManager, GwoutRepository gwoutRepository, ConfigService configService) {
        this.entityManager = entityManager;
        this.gwoutRepository = gwoutRepository;
        this.configService = configService;
    }

    private boolean tableMissingLogged = false;

    private String asString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return obj.toString();
    }

    /**
     * Đọc một cột kiểu số từ dòng kết quả, trả về -1 nếu thiếu hoặc không hợp lệ
     * để các phép so sánh giá trị (CTSW003 report-request, CTSW008 content-type) không khớp nhầm.
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
     * Ánh xạ giá trị bodyPartCharacterSet thô từ mtcu_tmp sang repertoire chuẩn
     * EUR Doc 047 §4.4.3.4.9 (Basic ISO-646 / Basic-1 ISO-8859-1).
     */
    private String mapBodyPartCharset(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toUpperCase();
        if (normalized.contains("8859")) return "ISO-8859-1";
        if (normalized.contains("646") || normalized.contains("ASCII") || normalized.contains("IA5")) return "ISO-646";
        log.warn("Unrecognized bodyPartCharacterSet value '{}' from mtcu_tmp, leaving amhs_content_encoding unset", raw);
        return null;
    }

    /**
     * Periodically syncs new messages destined for VVTSSWIM from AMHS database 
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 1000)
    @Transactional
    public void syncAmhsToGwout() {
        log.info("AMHS sync scheduler tick - checking mtcu_tmp...");
        try {
            String localAddress = "VVTSSWIM";
            try {
                String cfg = configService.getDefaultOriginator();
                if (cfg != null && !cfg.isBlank()) {
                    localAddress = cfg;
                }
            } catch (Exception e) {
                log.warn("Failed to read default originator from config, fallback to 'VVTSSWIM'");
            }
            String localAddressPattern = "%" + localAddress + "%";
            String vvtsswimPattern = "%VVTSSWIM%";

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
                    t.ftbpFileName,
                    t.ftbpObjectSize,
                    t.ftbpLastMod,
                    t.numberOfAttachment,
                    t.originEncodeInformationType,
                    o.reportRequest,
                    o.mtaReportRequest,
                    t.contentType
                FROM (
                    SELECT id, content, atsFilingTime, atsPriority, atsOhi, bodyPartType, ipmId, messageId, orAddress, bodyPartCharacterSet, ftbpFileName, ftbpObjectSize, ftbpLastMod, numberOfAttachment, originEncodeInformationType, contentType
                    FROM mtcu_tmp
                    ORDER BY id DESC
                    LIMIT 200
                ) t
                JOIN mtcu_to o ON t.id = o.receiveMessage_id
                WHERE t.messageId IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM gwout g WHERE g.amhsid = t.messageId
                  )
                  AND EXISTS (
                      SELECT 1 FROM mtcu_to gw
                      WHERE gw.receiveMessage_id = t.id
                        AND (gw.address LIKE :gatewayAddress OR gw.address LIKE :vvtsswimPattern)
                  )
                ORDER BY t.id ASC, o.id ASC
            """;

            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(sql)
                    .setParameter("gatewayAddress", localAddressPattern)
                    .setParameter("vvtsswimPattern", vvtsswimPattern)
                    .getResultList();

            log.info("AMHS sync query returned {} rows", rows != null ? rows.size() : 0);

            if (rows == null || rows.isEmpty()) {
                return;
            }

            // Gộp các dòng mtcu_to theo message: 1 IPM có thể có nhiều recipient,
            // câu SQL ở trên trả về 1 dòng cho MỖI recipient (bao gồm cả chính gateway).
            java.util.LinkedHashMap<Long, List<Object[]>> groupedByMessage = new java.util.LinkedHashMap<>();
            for (Object[] row : rows) {
                Long msgTmpId = ((Number) row[0]).longValue();
                groupedByMessage.computeIfAbsent(msgTmpId, k -> new java.util.ArrayList<>()).add(row);
            }

            final String gatewayShortAddress = localAddress;

            log.info("Found {} new AMHS messages to sync to gwout", groupedByMessage.size());

            for (List<Object[]> msgRows : groupedByMessage.values()) {
                Object[] row = msgRows.get(0);
                try {
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
                    String ftbpObjectSize = asString(row[12]);
                    String ftbpLastMod = asString(row[13]);
                    // §4.4.2.2: số body part của IPM, dùng để phát hiện IPM nhiều body part
                    Integer numberOfAttachment = null;
                    if (row.length > 14 && row[14] != null) {
                        try {
                            numberOfAttachment = Integer.valueOf(asString(row[14]).trim());
                        } catch (NumberFormatException ignored) {
                            log.warn("Giá trị numberOfAttachment '{}' không hợp lệ từ mtcu_tmp", row[14]);
                        }
                    }

                    // ICAO Doc 047: amhs_recipients phải liệt kê các recipient THẬT của IPM
                    // (không phải chính địa chỉ gateway VVTSSWIM dùng để nhận tin).
                    List<String> realRecipients = msgRows.stream()
                            .map(r -> AddressUtil.getShort(asString(r[9])))
                            .filter(java.util.Objects::nonNull)
                            .filter(a -> !a.equalsIgnoreCase(gatewayShortAddress))
                            .distinct()
                            .toList();

                    Gwout gwout = new Gwout();
                    if (messageId != null && messageId.length() > 200) messageId = messageId.substring(0, 200);
                    gwout.setAmhsid(messageId);
                    if (ipmId != null && ipmId.length() > 200) ipmId = ipmId.substring(0, 200);
                    gwout.setIpmId(ipmId);
                    gwout.setText(content);
                    gwout.setTime(LocalDateTime.now());
                    if (atsFilingTime != null && atsFilingTime.length() > 6) atsFilingTime = atsFilingTime.substring(0, 6);
                    gwout.setFilingTime(atsFilingTime);
                    if (atsOhi != null && atsOhi.length() > 60) atsOhi = atsOhi.substring(0, 60);
                    gwout.setOptionalHeading(atsOhi);
                    if (atsPriority != null && atsPriority.length() > 10) atsPriority = atsPriority.substring(0, 10);
                    gwout.setAmhsPriority(atsPriority);
                    // Ánh xạ mã bodyPartType (ví dụ 401) từ DB AMHS sang chuỗi chuẩn của ICAO EUR Doc 047
                    String standardBodyPartType = null;
                    String bodyType = "text";
                    if (bodyPartType != null) {
                        if ("401".equals(bodyPartType) || "ia5-text".equalsIgnoreCase(bodyPartType) || "ia5-text-body-part".equalsIgnoreCase(bodyPartType)) {
                            standardBodyPartType = "ia5-text-body-part";
                            bodyType = "text";
                        } else if ("402".equals(bodyPartType) || "general-text".equalsIgnoreCase(bodyPartType) || "general-text-body-part".equalsIgnoreCase(bodyPartType)) {
                            standardBodyPartType = "general-text-body-part";
                            bodyType = "text";
                        } else if ("403".equals(bodyPartType) || "file-transfer".equalsIgnoreCase(bodyPartType) || "file-transfer-body-part".equalsIgnoreCase(bodyPartType)) {
                            standardBodyPartType = "file-transfer-body-part";
                            bodyType = "ftbp";
                        } else {
                            // EUR Doc 047 §4.4.2.3b: loại body part ngoài danh sách cho phép phải bị
                            // TỪ CHỐI. Giữ nguyên giá trị thô để OutboundDispatchService.validateBodyPartType
                            // phát hiện - KHÔNG được tự coi là file-transfer-body-part.
                            standardBodyPartType = bodyPartType;
                            bodyType = "text";
                            log.warn("bodyPartType '{}' không thuộc danh sách §4.4.2.3, giữ nguyên để từ chối ở bước sau",
                                    bodyPartType);
                        }
                    }
                    gwout.setBodyPartType(standardBodyPartType);
                    gwout.setBodyType(bodyType);
                    gwout.setNumberOfAttachment(numberOfAttachment);
                    gwout.setOriginEit(row.length > 15 ? asString(row[15]) : null);
                    // CTSW008 (§4.4.1.1): content-type abstract-value của MTE, giữ nguyên giá trị thô
                    // để OutboundDispatchService từ chối nếu khác interpersonal-messaging-1988(22).
                    // Lưu ý 0 = unidentified là giá trị THẬT (phải bị từ chối), khác với -1 = thiếu dữ liệu.
                    int rawContentType = asInt(row, 18);
                    gwout.setX400ContentType(rawContentType >= 0 ? rawContentType : null);

                    // CTSW003 (§4.4.8 / Doc 9880 §4.5.6.2.20): per-recipient-indicators quyết định
                    // có phải sinh Delivery Report hay không. Cần DR khi originator-report-request
                    // = report(2), HOẶC originating-MTA-report-request = report(2)/audited-report(3).
                    // Cờ nằm ở mức từng recipient nên duyệt hết các dòng mtcu_to của bản tin.
                    gwout.setAmhsDeliveryReport(msgRows.stream().anyMatch(r ->
                            asInt(r, 16) == 2
                                    || asInt(r, 17) == 2
                                    || asInt(r, 17) == 3));
                    // EUR Doc 047 §4.4.3.4.9: repertoire chỉ có ý nghĩa cho general-text-body-part
                    // (ia5-text* luôn là "ia5", file-transfer-body-part không áp dụng)
                    if ("general-text-body-part".equals(standardBodyPartType)) {
                        gwout.setBodyPartCharset(mapBodyPartCharset(bodyPartCharacterSet));
                    }
                    // EUR Doc 047 §4.4.3.4.2 Table 4: file-attribute các tham số FTBP chỉ áp dụng
                    // cho file-transfer-body-part; T1 (conditionally translated) - chỉ gán khi có dữ liệu thật
                    if ("file-transfer-body-part".equals(standardBodyPartType)) {
                        gwout.setFtbpFileName(ftbpFileName != null && ftbpFileName.length() > 255
                                ? ftbpFileName.substring(0, 255) : ftbpFileName);
                        gwout.setFtbpObjectSize(ftbpObjectSize != null && ftbpObjectSize.length() > 20
                                ? ftbpObjectSize.substring(0, 20) : ftbpObjectSize);
                        gwout.setFtbpLastMod(ftbpLastMod != null && ftbpLastMod.length() > 20
                                ? ftbpLastMod.substring(0, 20) : ftbpLastMod);
                    }

                    // Convert originator to short format
                    String shortOrigin = AddressUtil.getShort(orAddress);
                    String finalOrigin = shortOrigin != null ? shortOrigin : orAddress;
                    if (finalOrigin != null && finalOrigin.length() > 200) finalOrigin = finalOrigin.substring(0, 200);
                    gwout.setOrigin(finalOrigin);
                    
                    // amhs_recipients = danh sách recipient THẬT (không phải chính gateway), phân cách dấu phẩy.
                    // Trường hợp hiếm khi IPM chỉ addressed tới mỗi gateway (không có recipient thật nào khác),
                    // fallback về địa chỉ gateway để không làm mất bản tin.
                    String finalRecipient = !realRecipients.isEmpty()
                            ? String.join(",", realRecipients)
                            : gatewayShortAddress;
                    if (realRecipients.isEmpty()) {
                        log.warn("gwout sync: messageId={} has no real recipient other than the gateway ({}), falling back to gateway address",
                                messageId, gatewayShortAddress);
                    }
                    if (finalRecipient != null && finalRecipient.length() > 1000) finalRecipient = finalRecipient.substring(0, 1000);
                    gwout.setAddress(finalRecipient);
                    
                    int numericPriority = 2;
                    if (atsPriority != null) {
                        numericPriority = AmqpProperties.mapAtsPriorityToAmqp(atsPriority);
                    }
                    gwout.setSwimPriority(numericPriority);

                    gwout.setStatus(OutboundStatus.PENDING.getValue());

                    gwoutRepository.saveAndFlush(gwout);
                    log.info("Synced AMHS message ID {} -> gwout#{}", messageId, gwout.getMsgid());

                } catch (Exception e) {
                    log.error("Failed to sync AMHS message row: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.warn("AMHS to gwout sync check error: {}", e.getMessage());
        }
    }
}