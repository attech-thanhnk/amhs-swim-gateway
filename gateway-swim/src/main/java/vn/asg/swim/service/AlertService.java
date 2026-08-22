package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.repository.GwAlertRepository;

import java.time.LocalDateTime;

/**
 * Tạo bản ghi cảnh báo gw_alert.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final GwAlertRepository gwAlertRepository;

    /**
     * Tạo cảnh báo mới.
     */
    public void create(String alertType, String severity, String message,
            String refTable, Long refId) {
        try {
            GwAlert alert = new GwAlert();
            alert.setAlertType(alertType);
            alert.setSeverity(severity);
            alert.setMessage(message);
            alert.setRefTable(refTable);
            alert.setRefId(refId);
            gwAlertRepository.save(alert);

            log.warn("Alert created [{}][{}]: {}", alertType, severity, message);
        } catch (Exception e) {
            log.error("Failed to create gw_alert: {}", e.getMessage());
        }
    }

    /**
     * Đóng toàn bộ cảnh báo đang mở của một loại khi sự cố đã tự khắc phục.
     *
     * Ví dụ: cảnh báo CONNECTION_LOST trước đây chỉ tắt được bằng thao tác tay trên
     * Control Position, nên màn hình vẫn báo đỏ dù kết nối AMQP đã khôi phục.
     */
    public void resolveOpenAlerts(String alertType, String reason) {
        try {
            var openAlerts = gwAlertRepository.findByAlertTypeAndStatusNot(alertType, GwAlert.STATUS_RESOLVED);
            if (openAlerts.isEmpty()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            for (GwAlert alert : openAlerts) {
                alert.setStatus(GwAlert.STATUS_RESOLVED);
                alert.setResolvedAt(now);
            }
            gwAlertRepository.saveAll(openAlerts);

            log.info("Auto-resolved {} alert(s) [{}]: {}", openAlerts.size(), alertType, reason);
        } catch (Exception e) {
            log.error("Failed to auto-resolve gw_alert [{}]: {}", alertType, e.getMessage());
        }
    }
}
