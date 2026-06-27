package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.GwoutDispatchHistory;

@Repository
public interface GwoutDispatchHistoryRepository extends JpaRepository<GwoutDispatchHistory, Long> {
}
