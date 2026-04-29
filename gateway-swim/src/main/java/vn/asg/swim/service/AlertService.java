package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.repository.GwAlertRepository;

/**
 * Creates gw_alert records for Control Position display to operators.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final GwAlertRepository gwAlertRepository;

    /**
     * Creates a new alert (Fault Management).
     * Records to the dedicated gw_alert table (EUR Doc 047 compliance).
     *
     * @param alertType Alert type. Example: GwAlert.TYPE_MESSAGE_DEAD
     * @param severity  Severity level. Example: GwAlert.SEV_CRITICAL
     * @param message   Detailed message content.
     * @param refTable  Related database table. Null if none.
     * @param refId     Related record ID. Null if none.
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
