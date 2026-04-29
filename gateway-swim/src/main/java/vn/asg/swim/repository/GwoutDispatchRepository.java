package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.GwoutDispatch;

import java.util.List;

@Repository
public interface GwoutDispatchRepository extends JpaRepository<GwoutDispatch, Long> {

  /**
   * Poll batch dispatch cần xử lý: status PENDING hoặc FAILED với next_retry_at
   * đã đến.
   * FOR UPDATE SKIP LOCKED để tránh race condition giữa các thread.
   */
  @Query(value = """
      SELECT * FROM gwout_dispatch
      WHERE status IN ('PENDING', 'FAILED')
        AND (next_retry_at IS NULL OR next_retry_at <= NOW())
      ORDER BY next_retry_at ASC
      LIMIT :batchSize
      FOR UPDATE
      """, nativeQuery = true)
  List<GwoutDispatch> findPendingBatch(@Param("batchSize") int batchSize);

  /** Lấy tất cả dispatch của 1 gwout để check/sync status tổng */
  List<GwoutDispatch> findByGwoutId(Long gwoutId);

  long countByStatus(String status);
}
