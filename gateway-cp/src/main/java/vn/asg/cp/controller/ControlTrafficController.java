package vn.asg.cp.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.AmhsFeedbackDto;
import vn.asg.cp.dto.ApiResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phản hồi AMHS bay ngược về cho điện văn đã gửi ở chiều SWIM → AMHS: RN, NRN, DR, NDR.
 */
@RestController
@RequestMapping("/api/control-traffic")
@RequiredArgsConstructor
@Slf4j
public class ControlTrafficController {

    @PersistenceContext
    private final EntityManager entityManager;

    private static final String BASE_COLUMNS =
            "id, ipnType, subjectIPM, origin, recipient, receiptTime, "
            + "nonReceipReason, discardReason, supplementaryInfomation, status";

    private static final String NDR_COLUMNS = "reasonCode, diagnosticCode, subjectMTS";

    private Boolean ndrColumnsPresent = null;

    /**
     * Danh sách phản hồi, mới nhất trước.
     *
     * @param type lọc theo RN / NRN / DR / NDR; bỏ trống để lấy tất cả
     */
    @GetMapping("/feedback")
    public ResponseEntity<ApiResponse<List<AmhsFeedbackDto>>> list(
            @RequestParam(value = "type", required = false) String type) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(query(type)));
        } catch (Exception e) {
            log.warn("Chưa đọc được bảng cp: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(
                    "Chưa đọc được bảng cp (" + e.getMessage()
                            + "). Bảng này do AMHS Component cung cấp.",
                    List.of()));
        }
    }

    /** Số liệu tóm tắt cho thẻ đếm trên đầu màn hình. */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Long>>> summary() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ndr", 0L);
        counts.put("dr", 0L);
        counts.put("nrn", 0L);
        counts.put("rn", 0L);
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager
                    .createNativeQuery("SELECT ipnType, COUNT(*) FROM cp GROUP BY ipnType")
                    .getResultList();
            for (Object[] row : rows) {
                String key = row[0] == null ? null : row[0].toString().trim().toLowerCase();
                if (key != null && counts.containsKey(key)) {
                    counts.put(key, ((Number) row[1]).longValue());
                }
            }
        } catch (Exception e) {
            log.warn("Chưa đếm được bảng cp: {}", e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }

    private List<AmhsFeedbackDto> query(String type) {
        boolean withNdr = hasNdrColumns();
        String columns = withNdr ? BASE_COLUMNS + ", " + NDR_COLUMNS : BASE_COLUMNS;
        boolean filtered = type != null && !type.isBlank();

        String sql = "SELECT " + columns + " FROM cp"
                + (filtered ? " WHERE ipnType = :type" : "")
                + " ORDER BY id DESC LIMIT 500";

        var nativeQuery = entityManager.createNativeQuery(sql);
        if (filtered) {
            nativeQuery.setParameter("type", type.trim().toUpperCase());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = nativeQuery.getResultList();

        List<AmhsFeedbackDto> result = new ArrayList<>();
        for (Object[] row : rows) {
            AmhsFeedbackDto dto = new AmhsFeedbackDto();
            dto.setId(toLong(row[0]));
            dto.setIpnType(toStr(row[1]));
            dto.setSubjectIpm(toStr(row[2]));
            dto.setOrigin(toStr(row[3]));
            dto.setRecipient(toStr(row[4]));
            dto.setReceiptTime(toStr(row[5]));
            dto.setNonReceiptReason(toInt(row[6]));
            dto.setDiscardReason(toInt(row[7]));
            dto.setSupplementaryInfo(toStr(row[8]));
            dto.setStatus(toInt(row[9]));
            if (withNdr) {
                dto.setReasonCode(toStr(row[10]));
                dto.setDiagnosticCode(toStr(row[11]));
                dto.setSubjectMts(toStr(row[12]));
            }
            result.add(dto);
        }
        return result;
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
        return ndrColumnsPresent;
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
