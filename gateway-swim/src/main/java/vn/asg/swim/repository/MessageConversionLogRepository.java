package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.MessageConversionLog;

@Repository
public interface MessageConversionLogRepository extends JpaRepository<MessageConversionLog, Long> {
}
