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
         * Poll a batch of PENDING records (status = 0 or NULL) for conversion.
         */
        @Query(value = """
                        SELECT * FROM gwout
                        WHERE status = 0
                        ORDER BY priority ASC, time ASC
                        LIMIT :batchSize
                        FOR UPDATE
                        """, nativeQuery = true)
        List<Gwout> findPendingConvertBatch(@Param("batchSize") int batchSize);

        /**
         * Poll a batch of TRANSFORMED records (status = 2) for publishing to Solace.
         */
        @Query(value = """
                        SELECT * FROM gwout
                        WHERE status = 2
                        ORDER BY priority ASC, time ASC
                        LIMIT :batchSize
                        FOR UPDATE
                        """, nativeQuery = true)
        List<Gwout> findPendingPublishBatch(@Param("batchSize") int batchSize);

        @Modifying
        @Query("UPDATE Gwout g SET g.status = :status WHERE g.msgid = :msgid")
        void updateStatus(@Param("msgid") Long msgid, @Param("status") int status);

        long countByStatus(int status);

        @Modifying
        @Query(value = "INSERT IGNORE INTO gwout_history SELECT * FROM gwout WHERE status IN (4, 6, 7) AND time <= :threshold", nativeQuery = true)
        int archiveOldRecords(@Param("threshold") java.time.LocalDateTime threshold);

        @Modifying
        @Query(value = "DELETE g FROM gwout g INNER JOIN gwout_history gh ON g.msgid = gh.msgid", nativeQuery = true)
        int deleteArchivedRecords();

        @Modifying
        @Query(value = "DELETE FROM gwout_history WHERE time < :thresholdDate", nativeQuery = true)
        int deleteOldHistoryRecords(@Param("thresholdDate") java.time.LocalDateTime thresholdDate);
}
