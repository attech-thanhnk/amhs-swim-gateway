package vn.asg.cp.repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.cp.dto.SystemHistoryResponseDto;
import vn.asg.cp.entity.SystemHistory;

import java.util.List;

@Repository
public interface SystemHistoryRepository
        extends JpaRepository<SystemHistory, Long> {

    Page<SystemHistory> findAllByOrderByEventTimeDesc(Pageable pageable);

    @Query("SELECT new vn.asg.cp.dto.SystemHistoryResponseDto(" +
            "sh.id, sh.eventTime, sh.eventType, sh.severity, sh.title, sh.description, " +
            "COALESCE(ush.isRead, false)) " +
            "FROM SystemHistory sh " +
            "LEFT JOIN UserSystemHistory ush ON ush.systemHistoryId = sh.id AND ush.userId = :userId " +
            "ORDER BY sh.eventTime DESC")
    Page<SystemHistoryResponseDto> findSystemHistoryByUserId(
            @Param("userId") Long userId,
            Pageable pageable
    );

    List<SystemHistory> findAllByOrderByEventTimeDesc();
}
