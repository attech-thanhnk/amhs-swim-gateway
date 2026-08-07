package vn.asg.swim.scheduler;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.MessageStatus;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.converter.common.AddressUtil;
import vn.asg.swim.model.AmqpProperties;
import vn.asg.swim.service.ConfigService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler to sync messages from AMHS tables 
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AmhsToGwoutSyncScheduler {

    @PersistenceContext
    private final EntityManager entityManager;
    private final GwoutRepository gwoutRepository;
    private final ConfigService configService;

    private boolean tableMissingLogged = false;

    /**
     * Checks if a table exists in the current database schema.
     */
    private boolean isTablePresent(String tableName) {
        try {
            Number count = (Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = LOWER(:tableName)")
                    .setParameter("tableName", tableName)
                    .getSingleResult();
            return count != null && count.longValue() > 0;
        } catch (Exception e) {
            log.warn("Failed to check existence for table '{}' via INFORMATION_SCHEMA: {}", tableName, e.getMessage());
            return true; // Fallback to true so native query executes
        }
    }

    /**
     * Periodically syncs new messages destined for VVTSSWIM from AMHS database 
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    @Transactional
    public void syncAmhsToGwout() {
        if (!isTablePresent("mtcu_tmp")) {
            if (!tableMissingLogged) {
                log.warn("AMHS source table 'mtcu_tmp' does not exist in database. Skipping AMHS -> gwout sync.");
                tableMissingLogged = true;
            }
            return;
        }
        tableMissingLogged = false;

        String localAddress = "VVTSSWIM";
        try {
            localAddress = configService.getDefaultOriginator();
        } catch (Exception e) {
            log.warn("Failed to read default originator from config, fallback to 'VVTSSWIM'");
        }
        String localAddressPattern = "%" + localAddress + "%";

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
                o.address AS recipient_address
            FROM mtcu_tmp t
            JOIN mtcu_to o ON t.id = o.receiveMessage_id
            WHERE o.address LIKE :gatewayAddress
              AND t.messageId IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1 FROM gwout g WHERE g.amhsid = t.messageId
              )
        """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("gatewayAddress", localAddressPattern)
                .getResultList();
        if (rows.isEmpty()) {
            return;
        }

        log.info("Found {} new AMHS messages to sync to gwout", rows.size());

        for (Object[] row : rows) {
            try {
                String content = (String) row[1];
                String atsFilingTime = (String) row[2];
                String atsPriority = (String) row[3];
                String atsOhi = (String) row[4];
                String bodyPartType = row[5] != null ? row[5].toString() : null;
                String messageId = (String) row[7];
                String orAddress = (String) row[8];
                String recipientAddress = (String) row[9];

                Gwout gwout = new Gwout();
                gwout.setAmhsid(messageId);
                gwout.setText(content);
                gwout.setTime(LocalDateTime.now());
                gwout.setFilingTime(atsFilingTime);
                gwout.setOptionalHeading(atsOhi);
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
                    } else {
                        standardBodyPartType = "file-transfer-body-part";
                        bodyType = "ftbp";
                    }
                }
                gwout.setBodyPartType(standardBodyPartType);
                gwout.setBodyType(bodyType);
                
                // Convert originator to short format
                String shortOrigin = AddressUtil.getShort(orAddress);
                gwout.setOrigin(shortOrigin != null ? shortOrigin : orAddress);
                
                // Convert recipient to short format
                String shortRecipient = AddressUtil.getShort(recipientAddress);
                gwout.setAddress(shortRecipient != null ? shortRecipient : recipientAddress);
                
                // Map ATS priority to AMQP numeric priority
                int numericPriority = 2;
                if (atsPriority != null) {
                    numericPriority = AmqpProperties.mapAtsPriorityToAmqp(atsPriority);
                }
                gwout.setSwimPriority(numericPriority);
                
                // Set initial status to PENDING
                gwout.setStatus(MessageStatus.OUT_PENDING.getValue());

                gwoutRepository.save(gwout);
                log.info("Synced AMHS message ID {} -> gwout#{}", messageId, gwout.getMsgid());

            } catch (Exception e) {
                log.error("Failed to sync AMHS message row: {}", e.getMessage(), e);
            }
        }
    }
}
