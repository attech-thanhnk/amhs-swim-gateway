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

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM PerformanceMetrics pm WHERE pm.timestamp < :cutoff")
    int deleteByTimestampBefore(@org.springframework.data.repository.query.Param("cutoff") Instant cutoff);
}
