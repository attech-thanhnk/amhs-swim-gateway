package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.Gwin;

@Repository
public interface GwinRepository extends JpaRepository<Gwin, Long> {

    /** Check for duplicate AMQP message-id */
    boolean existsByMessageId(String messageId);

    long countByStatus(int status);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO gwin_history (
                msgid, cpa, message_id, source, subject, amqp_properties, priority, time, payload_content, text, body_type, content_type, origin, address, addressing_source, status, error_type
            )
            SELECT 
                msgid, cpa, message_id, source, subject, amqp_properties, priority, time, payload_content, text, body_type, content_type, origin, address, addressing_source, status, error_type
            FROM gwin
            WHERE status IN (3, 4) AND time <= :threshold
            """, nativeQuery = true)
    int archiveOldRecords(@Param("threshold") java.time.LocalDateTime threshold);

    @Modifying
    @Query(value = "DELETE g FROM gwin g INNER JOIN gwin_history gh ON g.msgid = gh.msgid", nativeQuery = true)
    int deleteArchivedRecords();

    @Modifying
    @Query(value = "DELETE FROM gwin_history WHERE time < :thresholdDate", nativeQuery = true)
    int deleteOldHistoryRecords(@Param("thresholdDate") java.time.LocalDateTime thresholdDate);
}
