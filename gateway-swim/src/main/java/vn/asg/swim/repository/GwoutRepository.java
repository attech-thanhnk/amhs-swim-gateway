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
         * Poll batch bản ghi PENDING, ORDER BY priority ASC, time ASC.
         * FOR UPDATE để tránh race condition giữa nhiều thread.
         */
        @Query(value = """
                        SELECT * FROM gwout
                        WHERE status = 0
                        ORDER BY priority ASC, time ASC
                        LIMIT :batchSize
                        FOR UPDATE
                        """, nativeQuery = true)
        List<Gwout> findPendingBatch(@Param("batchSize") int batchSize);

        @Modifying
        @Query("UPDATE Gwout g SET g.status = :status WHERE g.msgid = :msgid")
        void updateStatus(@Param("msgid") Long msgid, @Param("status") int status);

        long countByStatus(int status);
}
