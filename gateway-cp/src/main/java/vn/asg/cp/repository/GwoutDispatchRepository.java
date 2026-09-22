package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.GwoutDispatch;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GwoutDispatchRepository extends JpaRepository<GwoutDispatch, Long>, JpaSpecificationExecutor<GwoutDispatch> {
    List<GwoutDispatch> findByGwoutId(Long gwoutId);
    long countByStatus(String status);

    /** Xóa dispatch records cũ hơn cutoff. */
    @Modifying
    @Query(value = "DELETE FROM gwout_dispatch WHERE created_at < :cutoff", nativeQuery = true)
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
