package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.Gwin;

import java.util.List;

@Repository
public interface GwinRepository extends JpaRepository<Gwin, Long> {

    /**
     * Poll batch gwin PENDING, ORDER BY priority ASC, time ASC.
     * FOR UPDATE is used to prevent race conditions during polling.
     */
    @Query(value = """
            SELECT * FROM gwin
            WHERE status = 0
            ORDER BY priority ASC, time ASC
            LIMIT :batchSize
            FOR UPDATE
            """, nativeQuery = true)
    List<Gwin> findPendingBatch(@Param("batchSize") int batchSize);

    /** Check for duplicate AMQP message-id */
    boolean existsByMessageId(String messageId);

    long countByStatus(int status);
}
