package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.ApiResponse;
import vn.asg.cp.entity.McuIpn;
import vn.asg.cp.entity.McuReport;
import vn.asg.cp.repository.McuIpnRepository;
import vn.asg.cp.repository.McuReportRepository;

import java.util.List;
import java.util.Map;

/**
 * Lưu lượng điều khiển AMHS bay ngược về gateway — IPN (RN/NRN) và Report (DR/NDR).
 * <p>
 * Đây KHÔNG phải bản tin, mà là phản hồi cho bản tin gateway đã gửi ở chiều SWIM → AMHS.
 * EUR Doc 047 §2.2.1.1 cấm chuyển chúng sang môi trường SWIM, nên điểm đến duy nhất là
 * Control Position.
 * <p>
 * Appendix A đòi hỏi đúng điều đó ở bốn test case — CTSW014, CTSW015, CTSW113 và CTSW114 đều
 * yêu cầu <i>"stores the message for appropriate processing at the Control Position"</i>.
 * Một dòng cảnh báo tóm tắt trên màn hình Alerts là chưa đủ: operator phải tra được chính
 * bản ghi RN/NRN/NDR.
 * <p>
 * Hai bảng do AMHS Component ghi và ITCU xử lý; Control Position chỉ đọc. Nếu bảng chưa tồn
 * tại trên môi trường đang chạy thì trả danh sách rỗng kèm cảnh báo, để màn hình vẫn mở được.
 */
@RestController
@RequestMapping("/api/control-traffic")
@RequiredArgsConstructor
public class ControlTrafficController {

    private final McuIpnRepository ipnRepository;
    private final McuReportRepository reportRepository;

    /**
     * IPN đến — CTSW014, CTSW015, CTSW113.
     *
     * @param type lọc theo RN hoặc NRN; bỏ trống để lấy tất cả
     */
    @GetMapping("/ipn")
    public ResponseEntity<ApiResponse<List<McuIpn>>> listIpn(
            @RequestParam(value = "type", required = false) String type) {
        try {
            List<McuIpn> data = (type == null || type.isBlank())
                    ? ipnRepository.findAllByOrderByIdDesc()
                    : ipnRepository.findByNotificationTypeOrderByIdDesc(type.trim().toUpperCase());
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(tableUnavailable("mtcu_ipn", e), List.of()));
        }
    }

    /**
     * Report đến — CTSW114. NDR nghĩa là bản tin đã gửi đi KHÔNG tới được người nhận.
     *
     * @param type lọc theo DR hoặc NDR; bỏ trống để lấy tất cả
     */
    @GetMapping("/report")
    public ResponseEntity<ApiResponse<List<McuReport>>> listReport(
            @RequestParam(value = "type", required = false) String type) {
        try {
            List<McuReport> data = (type == null || type.isBlank())
                    ? reportRepository.findAllByOrderByIdDesc()
                    : reportRepository.findByReportTypeOrderByIdDesc(type.trim().toUpperCase());
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(tableUnavailable("mtcu_report", e), List.of()));
        }
    }

    /** Số liệu tóm tắt cho thẻ đếm trên đầu màn hình. */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Long>>> summary() {
        long rn = countQuietly(() -> (long) ipnRepository.findByNotificationTypeOrderByIdDesc(McuIpn.TYPE_RN).size());
        long nrn = countQuietly(() -> (long) ipnRepository.findByNotificationTypeOrderByIdDesc(McuIpn.TYPE_NRN).size());
        long dr = countQuietly(() -> (long) reportRepository.findByReportTypeOrderByIdDesc(McuReport.TYPE_DR).size());
        long ndr = countQuietly(() -> (long) reportRepository.findByReportTypeOrderByIdDesc(McuReport.TYPE_NDR).size());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("rn", rn, "nrn", nrn, "dr", dr, "ndr", ndr)));
    }

    private long countQuietly(java.util.function.Supplier<Long> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return 0L;
        }
    }

    private String tableUnavailable(String table, Exception e) {
        return "Chưa đọc được bảng " + table + " (" + e.getMessage()
                + "). Bảng này do AMHS Component cung cấp.";
    }
}
