package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.GwinDispatch;

import java.util.List;

@Repository
public interface GwinDispatchRepository extends JpaRepository<GwinDispatch, Long> {

    /**
     * Poll batch dispatch cần xử lý: status PENDING hoặc FAILED với next_retry_at
     * đã đến.
     * FOR UPDATE SKIP LOCKED để tránh race condition.
     */
    @Query(value = """
            SELECT * FROM gwin_dispatch
            WHERE status IN ('PENDING', 'FAILED')
              AND (next_retry_at IS NULL OR next_retry_at <= NOW())
            ORDER BY next_retry_at ASC
            LIMIT :batchSize
            FOR UPDATE
            """, nativeQuery = true)
    List<GwinDispatch> findPendingBatch(@Param("batchSize") int batchSize);

    /** Lấy tất cả dispatch của 1 gwin để check/sync status tổng */
    List<GwinDispatch> findByGwinId(Long gwinId);

    long countByStatus(String status);
}
