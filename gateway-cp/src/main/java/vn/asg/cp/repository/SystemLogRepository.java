package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.SystemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, String> {
    Page<SystemLog> findByLevelAndModuleAndTimestampAfter(String level, String module, LocalDateTime afterDt,
            Pageable pageable);

    Page<SystemLog> findByTimestampAfter(LocalDateTime afterDt, Pageable pageable);

    Page<SystemLog> findByLevelContainingAndModuleContaining(String level, String module, Pageable pageable);
}
