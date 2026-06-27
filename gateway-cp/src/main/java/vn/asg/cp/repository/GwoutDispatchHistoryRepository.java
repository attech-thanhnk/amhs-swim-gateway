package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.GwoutDispatchHistory;

import java.util.List;

@Repository
public interface GwoutDispatchHistoryRepository extends JpaRepository<GwoutDispatchHistory, Long>, JpaSpecificationExecutor<GwoutDispatchHistory> {
    List<GwoutDispatchHistory> findByGwoutId(Long gwoutId);
}
