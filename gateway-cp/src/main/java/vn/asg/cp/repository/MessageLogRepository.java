// ================================
// MessageLogRepository.java
// ================================

package vn.asg.cp.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.MessageLog;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {

    List<MessageLog> findTop20ByOrderByCreatedAtDesc();

    Page<MessageLog> findByProcessingStatus(
            String processingStatus,
            Pageable pageable);

    Page<MessageLog> findByDirection(
            String direction,
            Pageable pageable);

    Page<MessageLog> findByAmhsMessageType(
            String amhsMessageType,
            Pageable pageable);

    Page<MessageLog> findByCreatedAtBetween(
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable);
}
