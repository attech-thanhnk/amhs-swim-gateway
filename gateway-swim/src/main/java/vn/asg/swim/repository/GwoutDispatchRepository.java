package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.GwoutDispatch;

import java.util.List;

@Repository
public interface GwoutDispatchRepository extends JpaRepository<GwoutDispatch, Long> {

  /**
   * Poll a batch of pending or failed dispatches that are ready to be processed.
   * Uses SELECT FOR UPDATE to prevent race conditions.
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

  /** Find all dispatches associated with a single gwout record. */
  List<GwoutDispatch> findByGwoutId(Long gwoutId);

  long countByStatus(String status);
}

