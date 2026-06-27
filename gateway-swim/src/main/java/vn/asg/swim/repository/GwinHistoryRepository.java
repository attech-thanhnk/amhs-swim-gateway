package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.GwinHistory;

@Repository
public interface GwinHistoryRepository extends JpaRepository<GwinHistory, Long> {
}
