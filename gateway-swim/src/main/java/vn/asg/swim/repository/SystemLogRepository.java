package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.SystemLog;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, String> {
}
