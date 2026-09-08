package vn.asg.swim.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.model.AmhsFeedback;

import java.util.ArrayList;
import java.util.List;

/**
 * Đọc bảng {@code cp} — phản hồi AMHS (RN/NRN/DR/NDR) do AMHS Component ghi vào.
 * <p>
 * Dùng native query chứ không phải JPA entity, vì hai lý do đều đã từng gây lỗi thật:
 * <ol>
 *   <li><b>Tên cột camelCase.</b> Spring Boot áp naming strategy chuyển {@code subjectIPM} thành
 *       {@code subject_ipm} kể cả khi khai báo {@code @Column} tường minh — lỗi gặp ngày 26/08.
 *       Native query theo vị trí cột không dính.</li>
 *   <li><b>Bảng {@code cp} dùng charset utf8mb3</b> trong khi {@code gwin} dùng utf8mb4. JOIN hai
 *       varchar khác collation sẽ nổ "Illegal mix of collations", nên lớp này KHÔNG JOIN — chỉ
 *       đọc {@code cp} ra Java, việc tra {@code gwin} do service làm bằng tham số riêng.</li>
 * </ol>
 * Ba cột {@code reasonCode}, {@code diagnosticCode}, {@code subjectMTS} phục vụ nhánh NDR có thể
 * chưa tồn tại (AMHS Component bổ sung sau). Lớp này tự dò một lần lúc chạy đầu tiên và lùi về
 * bộ cột cơ bản nếu thiếu, để phần RN/NRN vẫn chạy bình thường trong lúc chờ.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class AmhsFeedbackRepository {

    @PersistenceContext
    private final EntityManager entityManager;

    /** Bộ cột luôn có, theo đúng DDL AMHS Component đã chốt */
    private static final String BASE_COLUMNS =
            "id, ipnType, subjectIPM, origin, recipient, receiptTime, "
            + "nonReceipReason, discardReason, supplementaryInfomation, status";

    /** Ba cột bổ sung cho nhánh NDR */
    private static final String NDR_COLUMNS = "reasonCode, diagnosticCode, subjectMTS";

    /** null = chưa dò; true/false = kết quả đã dò */
    private Boolean ndrColumnsPresent = null;

    private boolean tableMissingLogged = false;

    /** Khoá trong gateway_config giữ mốc {@code cp.id} ITCU đã xử lý tới đâu */
    public static final String WATERMARK_KEY = "CP_FEEDBACK_LAST_ID";

    /**
     * Lấy một lô phản hồi mới hơn mốc đã xử lý.
     * <p>
     * Dùng mốc {@code id} chứ KHÔNG dùng {@code cp.status}, vì cột đó là phân loại riêng của AMHS
     * Component chứ không phải cờ dành cho ITCU — quan sát trên dữ liệu thật ngày 08/09:
     * {@code status=3} kèm ghi chú "CÓ có điện văn yêu cầu RN với ipmId này", {@code status=4} kèm
     * "Không có điện văn yêu cầu RN...". Ghi đè cột này sẽ xoá mất thông tin amss đang hiển thị
     * trên Control Position. Mốc {@code id} tăng đơn điệu theo AUTO_INCREMENT nên đủ an toàn.
     */
    @Transactional(readOnly = true)
    public List<AmhsFeedback> findNewBatch(long lastProcessedId, int batchSize) {
        try {
            return query(lastProcessedId, batchSize, hasNdrColumns());
        } catch (Exception e) {
            if (!tableMissingLogged) {
                log.warn("Chưa đọc được bảng cp ({}), bỏ qua xử lý phản hồi AMHS. "
                        + "Bảng này do AMHS Component cung cấp (CTSW014/015/113/114).", e.getMessage());
                tableMissingLogged = true;
            }
            return List.of();
        }
    }

    /** Mốc đã xử lý tới đâu; 0 nghĩa là chưa từng chạy. */
    @Transactional(readOnly = true)
    public long getWatermark() {
        try {
            Object value = entityManager
                    .createNativeQuery("SELECT config_value FROM gateway_config WHERE config_key = :k")
                    .setParameter("k", WATERMARK_KEY)
                    .getSingleResult();
            return value == null ? 0L : Long.parseLong(value.toString().trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * Đẩy mốc lên sau khi xử lý xong một bản ghi. Ghi vào {@code gateway_config} để mốc sống sót
     * qua lần khởi động lại — nếu không, mỗi lần restart sẽ xử lý lại toàn bộ lịch sử và sinh ra
     * hàng loạt cảnh báo trùng trên Control Position.
     */
    @Transactional
    public void setWatermark(long id) {
        entityManager.createNativeQuery("""
                INSERT INTO gateway_config (config_key, config_value, description, updated_at)
                VALUES (:k, :v, 'Moc cp.id ma ITCU da xu ly toi (tu dong cap nhat)', NOW())
                ON DUPLICATE KEY UPDATE config_value = :v, updated_at = NOW()
                """)
                .setParameter("k", WATERMARK_KEY)
                .setParameter("v", String.valueOf(id))
                .executeUpdate();
    }

    /**
     * Dò một lần xem ba cột NDR đã tồn tại chưa. Dò bằng information_schema thay vì bắt lỗi của
     * câu SELECT, để một lần thiếu cột không làm bẩn transaction đang chạy.
     */
    private boolean hasNdrColumns() {
        if (ndrColumnsPresent != null) {
            return ndrColumnsPresent;
        }
        try {
            Number found = (Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'cp'
                       AND column_name IN ('reasonCode', 'diagnosticCode', 'subjectMTS')
                    """).getSingleResult();
            ndrColumnsPresent = found != null && found.intValue() == 3;
        } catch (Exception e) {
            ndrColumnsPresent = false;
        }
        if (!ndrColumnsPresent) {
            log.warn("Bảng cp chưa có đủ 3 cột reasonCode/diagnosticCode/subjectMTS - nhánh RN/NRN "
                    + "vẫn chạy, riêng NDR (CTSW114) thiếu mã lý do cho tới khi AMHS Component bổ sung.");
        }
        return ndrColumnsPresent;
    }

    private List<AmhsFeedback> query(long lastProcessedId, int batchSize, boolean withNdr) {
        String columns = withNdr ? BASE_COLUMNS + ", " + NDR_COLUMNS : BASE_COLUMNS;
        String sql = "SELECT " + columns + " FROM cp"
                + " WHERE id > :lastId"
                + " ORDER BY id ASC LIMIT " + Math.max(1, batchSize);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("lastId", lastProcessedId)
                .getResultList();

        List<AmhsFeedback> result = new ArrayList<>();
        for (Object[] row : rows) {
            AmhsFeedback f = new AmhsFeedback();
            f.setId(toLong(row[0]));
            f.setIpnType(toStr(row[1]));
            f.setSubjectIpm(toStr(row[2]));
            f.setOrigin(toStr(row[3]));
            f.setRecipient(toStr(row[4]));
            f.setReceiptTime(toStr(row[5]));
            f.setNonReceiptReason(toInt(row[6]));
            f.setDiscardReason(toInt(row[7]));
            f.setSupplementaryInfo(toStr(row[8]));
            f.setStatus(toInt(row[9]));
            if (withNdr) {
                f.setReasonCode(toStr(row[10]));
                f.setDiagnosticCode(toStr(row[11]));
                f.setSubjectMts(toStr(row[12]));
            }
            result.add(f);
        }
        return result;
    }

    private Long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private Integer toInt(Object v) {
        return v instanceof Number n ? n.intValue() : null;
    }

    private String toStr(Object v) {
        return v == null ? null : v.toString();
    }
}
