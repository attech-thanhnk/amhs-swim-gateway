package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.PerformanceMetrics;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PerformanceMetricsRepository extends JpaRepository<PerformanceMetrics, Long> {
    List<PerformanceMetrics> findByTimestampAfterOrderByTimestampAsc(Instant since);

    Optional<PerformanceMetrics> findFirstByOrderByTimestampDesc();
}
