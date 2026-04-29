package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.PerformanceMetrics;

@Repository
public interface PerformanceMetricsRepository extends JpaRepository<PerformanceMetrics, Long> {
}
