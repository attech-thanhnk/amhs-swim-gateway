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
     * EUR Doc 047 §4.4.3.4.4 / CTSW001: recipient có responsibility element = "responsible".
     * mtcu_to.responsibility là bit(1) nên JDBC có thể trả về Boolean, Number hoặc byte[].
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
     * Nạp nội dung nhị phân của file đính kèm (mtcu_tmp.data) cho MỘT bản tin.
     * <p>
     * Tách thành truy vấn riêng thay vì lấy kèm trong câu SELECT chính, vì câu chính quét tới
     * 200 bản tin mỗi lượt còn cột {@code data} là longblob — kéo blob của mọi dòng sẽ rất nặng
     * trong khi hầu hết bản tin ATS không có file đính kèm. Câu chính chỉ lấy
     * {@code OCTET_LENGTH(data)} để biết có file hay không, có mới nạp.
     *
     * @return mảng byte của file, hoặc null nếu không có / đọc lỗi
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
     * Ánh xạ giá trị bodyPartCharacterSet thô từ mtcu_tmp sang repertoire chuẩn
     * EUR Doc 047 §4.4.3.4.9 (Basic ISO-646 / Basic-1 ISO-8859-1).
     * <p>
     * Chấp nhận cả hai dạng amss có thể ghi:
     * <ul>
     *   <li>dạng chữ: "ISO 8859-1", "US-ASCII", "IA5", "ITA2"...</li>
     *   <li>dạng character set registration number theo ISO 2375 mà CTSW018/CTSW019 dùng:
     *       "1,6" = Basic ISO 646; "1,6,100" = ISO 8859-1; các số khác (Cyrillic 144,
     *       Greek 126, Arabic 127, Hebrew 138, CJK...) trả về "ISO-REG-&lt;n&gt;" để
     *       MessageValidationService áp dụng chính sách nội bộ của CTSW019.</li>
     * </ul>
     * Trả về null khi không nhận dạng được, khi đó amhs_content_encoding sẽ không được gán.
     */
    private String mapBodyPartCharset(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toUpperCase();

        // Dạng chữ
        if (normalized.contains("ITA2")) return "ITA2";
        if (normalized.contains("8859-1") || normalized.contains("8859 1")) return "ISO-8859-1";
        if (normalized.contains("646") || normalized.contains("ASCII") || normalized.contains("IA5")) return "ISO-646";

        // Dạng character set registration number: "1,6" / "1 6 100" / "1,6,144"
        if (normalized.matches("[0-9]+([,;\\s]+[0-9]+)*")) {
            java.util.Set<Integer> regs = new java.util.LinkedHashSet<>();
            for (String tok : normalized.split("[,;\\s]+")) {
                try {
                    regs.add(Integer.valueOf(tok));
                } catch (NumberFormatException ignored) {
                    // đã lọc bằng regex ở trên, không thể xảy ra
                }
            }
            // 100 = phần bên phải của ISO 8859-1 (Western European supplementary set)
            if (regs.contains(100)) return "ISO-8859-1";
            // 1 = ISO 646 IRV, 6 = US-ASCII -> Basic ISO 646
            java.util.Set<Integer> iso646 = java.util.Set.of(1, 6);
            if (iso646.containsAll(regs)) return "ISO-646";
            // Repertoire khác ISO 646: giữ lại registration number đầu tiên ngoài 1/6
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
     * Quét mtcu_tmp lấy bản tin AMHS có recipient là AMQP consumer đã khai báo và đồng bộ sang gwout.
     * <p>
     * KHÔNG gắn {@code @Transactional} ở mức phương thức, để mỗi {@code saveAndFlush} chạy trong
     * transaction riêng của repository. Nếu gói cả lô vào một transaction thì một vi phạm ràng
     * buộc (ví dụ {@code uk_gwout_amhsid} khi có hai instance cùng chạy) sẽ đánh dấu transaction
     * rollback-only và huỷ luôn các bản tin còn lại trong lô, dù đã có try/catch từng bản tin.
     * Với thứ tự quét ASC bên dưới, một dòng hỏng như vậy sẽ chặn vĩnh viễn cả hàng đợi.
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

            // Gộp các dòng mtcu_to theo message: 1 IPM có thể có nhiều recipient,
            // câu SQL ở trên trả về 1 dòng cho MỖI recipient.
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
                    // Server 188 cung cấp tên file và nội dung nhị phân của FTBP.
                    // Kích thước lấy bằng OCTET_LENGTH(data) thay vì một cột riêng — không có
                    // nguồn nào cho date-and-time-of-last-modification nên amhs_ftbp_last_mod
                    // sẽ vắng mặt (hợp lệ: §4.4.3.4.2 ghi cả ba thuộc tính FTBP là optional).
                    String ftbpFileName = asString(row[11]);
                    long ftbpDataSize = row[12] instanceof Number n ? n.longValue() : 0L;
                    // §4.4.2.2: số body part của IPM, dùng để phát hiện IPM nhiều body part
                    Integer numberOfAttachment = null;
                    if (row.length > 14 && row[14] != null) {
                        try {
                            numberOfAttachment = Integer.valueOf(asString(row[14]).trim());
                        } catch (NumberFormatException ignored) {
                            log.warn("Giá trị numberOfAttachment '{}' không hợp lệ từ mtcu_tmp", row[14]);
                        }
                    }

                    // CTSW001 (§4.4.3.4.4): amhs_recipients gồm recipient-name của các
                    // per-recipient-fields có responsibility = "responsible" - KHÔNG có bộ lọc
                    // nào khác. Bản trước còn gạt thêm địa chỉ AMHS của chính gateway; luật đó
                    // không có trong đặc tả và chỉ đúng khi bản tin được đánh địa chỉ tới gateway
                    // thay vì tới AMQP consumer. Xem chú thích của câu SQL bên trên.
                    //
                    // mtcu_to.responsibility là bit(1): 1 = responsible, 0 = not-responsible,
                    // NULL = AMHS Component chưa cung cấp -> giữ nguyên hành vi cũ (nhận tất cả)
                    // để không làm mất recipient khi dữ liệu chưa sẵn sàng.
                    boolean hasResponsibilityData = msgRows.stream()
                            .anyMatch(r -> r.length > 21 && r[21] != null);
                    List<String> realRecipients = msgRows.stream()
                            .filter(r -> !hasResponsibilityData || isResponsible(r))
                            .map(r -> AddressUtil.getShort(asString(r[9])))
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .toList();

                    // CTSW001 (Extended IPM): amhs_ats_pri và AMQP priority lấy từ precedence
                    // CAO NHẤT trong các recipient "responsible" (Table 5), không phải từ
                    // ATS-message-priority. CTSW020: precedence 107 phải báo Control Position.
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
                    // CTSW004: KHÔNG cắt về 6 ký tự. Cắt ở đây sẽ biến một filing-time hỏng như
                    // "0704301234" thành "070430" hợp lệ và bước validateAtsMessageHeader mất
                    // luôn ca kiểm thử. Cột gwout.filing_time đã được nới lên varchar(32) để giá
                    // trị sai khuôn cũng lưu được nguyên vẹn cho bước từ chối phía sau.
                    if (atsFilingTime != null && atsFilingTime.length() > 32) {
                        atsFilingTime = atsFilingTime.substring(0, 32);
                    }
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
                    // §4.4.3.4.9: "Upon reception of a message with two body parts, one
                    // file-transfer-body part and one text body part, the amhs_bodypart_type
                    // application property shall contain the value file-transfer-body part."
                    // Có nội dung nhị phân trong mtcu_tmp.data nghĩa là bản tin có FTBP, bất kể
                    // bodyPartType báo giá trị nào — kể cả cặp text + FTBP (CTSW007 điện văn 1-2).
                    if (ftbpDataSize > 0) {
                        standardBodyPartType = "file-transfer-body-part";
                        bodyType = "ftbp";
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

                    // CTSW001 (§4.4.3.4.8): amhs_subject mang giá trị phần tử subject của IPM heading.
                    String subject = row.length > 19 ? asString(row[19]) : null;
                    if (subject != null && subject.length() > 200) subject = subject.substring(0, 200);
                    gwout.setSubject(subject);

                    // CTSW003 (§4.4.8 / Doc 9880 §4.5.6.2.20): per-recipient-indicators quyết định
                    // có phải sinh Delivery Report hay không. Cần DR khi originator-report-request
                    // = report(2), HOẶC originating-MTA-report-request = report(2)/audited-report(3).
                    // Cờ nằm ở mức từng recipient nên duyệt hết các dòng mtcu_to của bản tin.
                    gwout.setAmhsDeliveryReport(msgRows.stream().anyMatch(r ->
                            asInt(r, 16) == 2
                                    || asInt(r, 17) == 2
                                    || asInt(r, 17) == 3));
                    // EUR Doc 047 §4.4.3.4.9: repertoire áp dụng cho general-text-body-part (CTSW018/019)
                    // và cho ia5-text-body-part (CTSW017 phải phát hiện được repertoire ita2 để từ chối).
                    // file-transfer-body-part không có repertoire.
                    if ("general-text-body-part".equals(standardBodyPartType)
                            || "ia5-text-body-part".equals(standardBodyPartType)) {
                        gwout.setBodyPartCharset(mapBodyPartCharset(bodyPartCharacterSet));
                    }
                    // EUR Doc 047 §4.4.3.4.2 Table 4: file-attribute các tham số FTBP chỉ áp dụng
                    // cho file-transfer-body-part; T1 (conditionally translated) - chỉ gán khi có dữ liệu thật
                    if ("file-transfer-body-part".equals(standardBodyPartType)) {
                        gwout.setFtbpFileName(ftbpFileName != null && ftbpFileName.length() > 255
                                ? ftbpFileName.substring(0, 255) : ftbpFileName);
                        if (ftbpDataSize > 0) {
                            gwout.setFtbpObjectSize(String.valueOf(ftbpDataSize));
                        }
                        // §4.4.3.5.1: "For messages that contain an FTBP, the data of the FTBP
                        // shall be used." Nội dung nhị phân được nạp riêng (chỉ với bản tin thật
                        // sự có file) rồi mã hoá base64 vào gwout.text — OutboundDispatchService
                        // giải mã lại khi dựng BytesMessage.
                        byte[] ftbpData = loadFtbpData(msgTmpId);
                        if (ftbpData != null && ftbpData.length > 0) {
                            gwout.setText(java.util.Base64.getEncoder().encodeToString(ftbpData));
                        }
                    }

                    // Convert originator to short format
                    String shortOrigin = AddressUtil.getShort(orAddress);
                    String finalOrigin = shortOrigin != null ? shortOrigin : orAddress;
                    if (finalOrigin != null && finalOrigin.length() > 200) finalOrigin = finalOrigin.substring(0, 200);
                    gwout.setOrigin(finalOrigin);
                    
                    // Danh sách rỗng chỉ còn xảy ra khi MỌI recipient của IPM đều mang
                    // responsibility = not-responsible: theo §4.4.3.4.4 thì ITCU không chịu trách
                    // nhiệm chuyển giao cho ai cả, không có gì để đưa sang SWIM. Vẫn ghi một dòng
                    // gwout FAILED thay vì bỏ qua, để bản tin không bị quét lại vô hạn mỗi 2 giây
                    // và để Control Position tra cứu được.
                    if (realRecipients.isEmpty()) {
                        log.warn("gwout sync: messageId={} không có recipient nào 'responsible', không chuyển sang SWIM",
                                messageId);
                        alertService.create(
                                GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                                "Bản tin " + messageId + " không có recipient nào mang responsibility "
                                        + "'responsible' — ITCU không chịu trách nhiệm chuyển giao (§4.4.3.4.4).",
                                "mtcu_tmp", msgTmpId);
                        gwout.setAddress("");
                        gwout.setStatus(OutboundStatus.FAILED.getValue());
                        gwout.setRejectionReason("no-responsible-recipient");
                        gwoutRepository.saveAndFlush(gwout);
                        continue;
                    }
                    // CTSW010: KHÔNG cắt danh sách recipient. Cột gwout.address là MEDIUMTEXT nên
                    // chứa được tối đa "Maximum message number of recipients" (512 recipient ~ 4.6KB).
                    // Cắt chuỗi ở đây sẽ làm mất recipient âm thầm và bản tin bị chuyển thiếu người nhận,
                    // trong khi §4.4.2.7 yêu cầu vượt ngưỡng thì phải TỪ CHỐI cả bản tin bằng NDR
                    // "too-many-recipients" — việc đó do OutboundDispatchService thực hiện sau.
                    gwout.setAddress(String.join(",", realRecipients));
                    
                    // CTSW001 (§4.4.3.4.3 + Table 5): Extended IPM ưu tiên dùng precedence cao nhất;
                    // Basic IPM (không có precedence) dùng ATS-message-priority như trước.
                    gwout.setPrecedence(highestPrecedence);
                    String effectivePriority = AmqpProperties.mapPrecedenceToAtsPriority(highestPrecedence);
                    if (effectivePriority != null) {
                        gwout.setAmhsPriority(effectivePriority);
                    } else {
                        effectivePriority = atsPriority;
                        if (highestPrecedence != null) {
                            log.warn("gwout sync: messageId={} có precedence {} không thuộc Table 5, "
                                    + "giữ ATS-message-priority '{}'", messageId, highestPrecedence, atsPriority);
                        }
                    }
                    gwout.setSwimPriority(AmqpProperties.mapAtsPriorityToAmqp(effectivePriority));

                    gwout.setStatus(OutboundStatus.PENDING.getValue());

                    gwoutRepository.saveAndFlush(gwout);
                    log.info("Synced AMHS message ID {} -> gwout#{}", messageId, gwout.getMsgid());

                } catch (Exception e) {
                    log.error("Failed to sync AMHS message row: {}", e.getMessage(), e);
                    // Với thứ tự quét ASC, một dòng hỏng nằm ở đầu hàng đợi sẽ được thử lại mỗi
                    // 2 giây và chiếm chỗ trong cửa sổ 200 bản tin. Báo Control Position để tình
                    // huống đó nhìn thấy được thay vì chỉ nằm trong log.
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