package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.MessageConversionLog;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Repository
public interface MessageConversionLogRepository extends JpaRepository<MessageConversionLog, Long> {
    @Modifying
    @Transactional
    @Query("DELETE FROM MessageConversionLog m WHERE m.convertedTime < :cutoffDate")
    int deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);
}
