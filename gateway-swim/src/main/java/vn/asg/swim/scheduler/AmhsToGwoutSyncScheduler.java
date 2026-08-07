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
                    o.address AS recipient_address
                FROM (
                    SELECT id, content, atsFilingTime, atsPriority, atsOhi, bodyPartType, ipmId, messageId, orAddress
                    FROM mtcu_tmp
                    ORDER BY id DESC
                    LIMIT 200
                ) t
                JOIN mtcu_to o ON t.id = o.receiveMessage_id
                WHERE (o.address LIKE :gatewayAddress OR o.address LIKE :vvtsswimPattern)
                  AND t.messageId IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM gwout g WHERE g.amhsid = t.messageId
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM gwout_history gh WHERE gh.amhsid = t.messageId
                  )
                ORDER BY t.id ASC
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

            log.info("Found {} new AMHS messages to sync to gwout", rows.size());

            for (Object[] row : rows) {
                try {
                    String content = asString(row[1]);
                    String atsFilingTime = asString(row[2]);
                    String atsPriority = asString(row[3]);
                    String atsOhi = asString(row[4]);
                    String bodyPartType = asString(row[5]);
                    String messageId = asString(row[7]);
                    String orAddress = asString(row[8]);
                    String recipientAddress = asString(row[9]);

                    Gwout gwout = new Gwout();
                    if (messageId != null && messageId.length() > 200) messageId = messageId.substring(0, 200);
                    gwout.setAmhsid(messageId);
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
                        } else {
                            standardBodyPartType = "file-transfer-body-part";
                            bodyType = "ftbp";
                        }
                    }
                    gwout.setBodyPartType(standardBodyPartType);
                    gwout.setBodyType(bodyType);
                    
                    // Convert originator to short format
                    String shortOrigin = AddressUtil.getShort(orAddress);
                    String finalOrigin = shortOrigin != null ? shortOrigin : orAddress;
                    if (finalOrigin != null && finalOrigin.length() > 200) finalOrigin = finalOrigin.substring(0, 200);
                    gwout.setOrigin(finalOrigin);
                    
                    // Convert recipient to short format
                    String shortRecipient = AddressUtil.getShort(recipientAddress);
                    String finalRecipient = shortRecipient != null ? shortRecipient : recipientAddress;
                    if (finalRecipient != null && finalRecipient.length() > 1000) finalRecipient = finalRecipient.substring(0, 1000);
                    gwout.setAddress(finalRecipient);
                    
                    // Map ATS priority to AMQP numeric priority
                    int numericPriority = 2;
                    if (atsPriority != null) {
                        numericPriority = AmqpProperties.mapAtsPriorityToAmqp(atsPriority);
                    }
                    gwout.setSwimPriority(numericPriority);
                    
                    // Set initial status to PENDING
                    gwout.setStatus(MessageStatus.OUT_PENDING.getValue());

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
