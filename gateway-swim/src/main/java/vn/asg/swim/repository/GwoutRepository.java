package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.Gwout;

import java.util.List;

@Repository
public interface GwoutRepository extends JpaRepository<Gwout, Long> {

        /**
         * Poll a batch of PENDING records (status = 0 or NULL) to forward.
         */
        @Query(value = """
                        SELECT * FROM gwout
                        WHERE status = 0
                        ORDER BY FIELD(coalesce(amhs_priority, 'KK'), 'SS', 'DD', 'FF', 'GG', 'KK') ASC, time ASC
                        LIMIT :batchSize
                        FOR UPDATE
                        """, nativeQuery = true)
        List<Gwout> findPendingForwardBatch(@Param("batchSize") int batchSize);

        /**
         * Poll a batch of TRANSFORMED records (status = 1) for publishing to Solace.
         */
        @Query(value = """
                        SELECT * FROM gwout
                        WHERE status = 1
                        ORDER BY swim_priority ASC, time ASC
                        LIMIT :batchSize
                        FOR UPDATE
                        """, nativeQuery = true)
        List<Gwout> findPendingPublishBatch(@Param("batchSize") int batchSize);

        long countByStatus(int status);

        @Modifying
        @Query(value = """
                        INSERT IGNORE INTO gwout_history (
                                msgid, amhsid, ipm_id, amhs_priority, swim_priority, time, filing_time, text, body_type, origin, address, optional_heading, subject, amhs_ttl, amhs_registered_id, amhs_delivery_report, content_type, status, payload_content, body_part_type, body_part_charset, ftbp_file_name, ftbp_object_size, ftbp_last_mod, rejection_reason, rejection_diagnostic, amqp_message_id, message_signed, error_type
                        )
                        SELECT
                                msgid, amhsid, ipm_id, amhs_priority, swim_priority, time, filing_time, text, body_type, origin, address, optional_heading, subject, amhs_ttl, amhs_registered_id, amhs_delivery_report, content_type, status, payload_content, body_part_type, body_part_charset, ftbp_file_name, ftbp_object_size, ftbp_last_mod, rejection_reason, rejection_diagnostic, amqp_message_id, message_signed, error_type
                        FROM gwout
                        WHERE status IN (2, 4, 5) AND time <= :threshold
                        """, nativeQuery = true)
        int archiveOldRecords(@Param("threshold") java.time.LocalDateTime threshold);

        @Modifying
        @Query(value = "DELETE g FROM gwout g INNER JOIN gwout_history gh ON g.msgid = gh.msgid", nativeQuery = true)
        int deleteArchivedRecords();

        @Modifying
        @Query(value = "DELETE FROM gwout_history WHERE time < :thresholdDate", nativeQuery = true)
        int deleteOldHistoryRecords(@Param("thresholdDate") java.time.LocalDateTime thresholdDate);
}
