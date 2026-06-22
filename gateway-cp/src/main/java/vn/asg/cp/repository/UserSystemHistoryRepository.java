package vn.asg.cp.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.UserSystemHistory;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSystemHistoryRepository extends JpaRepository<UserSystemHistory, Long> {

    Optional<UserSystemHistory> findByUserIdAndSystemHistoryId(Long userId, Long systemHistoryId);

    List<UserSystemHistory> findByUserId(Long userId);

    List<UserSystemHistory> findByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE UserSystemHistory ush SET ush.isRead = true WHERE ush.userId = :userId AND ush.systemHistoryId = :historyId")
    int markAsRead(@Param("userId") Long userId, @Param("historyId") Long historyId);

    @Modifying
    @Transactional
    @Query("UPDATE UserSystemHistory ush SET ush.isRead = true WHERE ush.userId = :userId AND ush.systemHistoryId IN :historyIds")
    int markMultipleAsRead(@Param("userId") Long userId, @Param("historyIds") List<Long> historyIds);

    long countByUserIdAndIsReadFalse(Long userId);

    @Query("SELECT ush.systemHistoryId FROM UserSystemHistory ush WHERE ush.userId = :userId AND ush.isRead = false")
    List<Long> findUnreadHistoryIdsByUserId(@Param("userId") Long userId);

    @Modifying // Bắt buộc phải có với câu lệnh UPDATE/DELETE
    @Transactional // Đảm bảo tính đóng gói dữ liệu (Rollback nếu lỗi)
    @Query("UPDATE UserSystemHistory u SET u.isRead = true WHERE u.userId = :userId AND u.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);
}
