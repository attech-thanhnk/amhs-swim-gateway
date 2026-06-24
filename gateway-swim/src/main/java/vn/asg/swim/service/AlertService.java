package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.repository.GwAlertRepository;

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
}
