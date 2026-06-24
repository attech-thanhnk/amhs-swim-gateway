package vn.asg.cp.service;

import vn.asg.cp.dto.SystemHistoryWithReadStatusDTO;
import vn.asg.cp.entity.SystemHistory;
import vn.asg.cp.entity.User;
import vn.asg.cp.entity.UserSystemHistory;
import vn.asg.cp.repository.SystemHistoryRepository;
import vn.asg.cp.repository.UserRepository;
import vn.asg.cp.repository.UserSystemHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserSystemHistoryService {

    private final UserSystemHistoryRepository userSystemHistoryRepository;
    private final UserRepository userRepository;
    private final SystemHistoryRepository systemHistoryRepository;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Tạo bản ghi user_system_history cho một user cụ thể
     */
    public UserSystemHistory createForUser(Long userId, Long systemHistoryId) {
        UserSystemHistory userSystemHistory = UserSystemHistory.builder()
                .userId(userId)
                .systemHistoryId(systemHistoryId)
                .isRead(false)
                .build();
        return userSystemHistoryRepository.save(userSystemHistory);
    }

    /**
     * Tạo bản ghi cho tất cả users khi có system history mới
     */
    @Transactional
    public void createForAllUsers(SystemHistory systemHistory) {
        List<User> allUsers = userRepository.findAll();
        List<UserSystemHistory> userHistories = allUsers.stream()
                .map(user -> UserSystemHistory.builder()
                        .userId(user.getId())
                        .systemHistoryId(systemHistory.getId())
                        .isRead(false)
                        .build())
                .collect(Collectors.toList());

        userSystemHistoryRepository.saveAll(userHistories);
        log.info("Created user_system_history for {} users for history ID: {}", allUsers.size(), systemHistory.getId());
    }

    /**
     * Đánh dấu đã đọc cho một history
     */
    @Transactional
    public void markAsRead(Long userId, Long historyId) {
        int updated = userSystemHistoryRepository.markAsRead(userId, historyId);
        if (updated == 0) {
            log.warn("No record found for userId: {} and historyId: {}", userId, historyId);
            throw new RuntimeException("Không tìm thấy bản ghi user_system_history");
        }
    }

    /**
     * Đánh dấu đã đọc cho nhiều history
     */
    @Transactional
    public void markMultipleAsRead(Long userId, List<Long> historyIds) {
        if (historyIds == null || historyIds.isEmpty()) {
            return;
        }
        int updated = userSystemHistoryRepository.markMultipleAsRead(userId, historyIds);
    }

    /**
     * Đánh dấu đã đọc cho nhiều history
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        return userSystemHistoryRepository.markAllAsReadByUserId(userId);
    }

    /**
     * Lấy danh sách system history kèm trạng thái đọc của user
     */
    @Transactional(readOnly = true)
    public List<SystemHistoryWithReadStatusDTO> getHistoriesWithReadStatus(Long userId) {
        // Lấy tất cả user_system_history của user
        List<UserSystemHistory> userHistories = userSystemHistoryRepository.findByUserId(userId);

        // Tạo map để tra cứu nhanh trạng thái đọc
        java.util.Map<Long, Boolean> readStatusMap = userHistories.stream()
                .collect(Collectors.toMap(
                        UserSystemHistory::getSystemHistoryId,
                        UserSystemHistory::getIsRead
                ));

        // Lấy tất cả system history (có thể thêm phân trang)
        List<SystemHistory> allHistories = systemHistoryRepository.findAllByOrderByEventTimeDesc();

        return allHistories.stream()
                .map(history -> SystemHistoryWithReadStatusDTO.builder()
                        .id(history.getId())
                        .title(history.getTitle())
                        .description(history.getDescription())
                        .eventType(history.getEventType())
                        .severity(history.getSeverity())
                        .eventTime(history.getEventTime() != null ?
                                history.getEventTime().format(DATE_TIME_FORMATTER) : null)
                        .createdBy(history.getCreatedBy())
                        .isRead(readStatusMap.getOrDefault(history.getId(), false))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách system history CHƯA ĐỌC của user
     */
    @Transactional(readOnly = true)
    public List<SystemHistoryWithReadStatusDTO> getUnreadHistories(Long userId) {
        List<Long> unreadHistoryIds = userSystemHistoryRepository.findUnreadHistoryIdsByUserId(userId);

        if (unreadHistoryIds.isEmpty()) {
            return List.of();
        }

        List<SystemHistory> unreadHistories = systemHistoryRepository.findAllById(unreadHistoryIds);

        return unreadHistories.stream()
                .map(history -> SystemHistoryWithReadStatusDTO.builder()
                        .id(history.getId())
                        .title(history.getTitle())
                        .description(history.getDescription())
                        .eventType(history.getEventType())
                        .severity(history.getSeverity())
                        .eventTime(history.getEventTime() != null ?
                                history.getEventTime().format(DATE_TIME_FORMATTER) : null)
                        .createdBy(history.getCreatedBy())
                        .isRead(false)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Đếm số lượng history chưa đọc của user
     */
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return userSystemHistoryRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * Kiểm tra user đã đọc history chưa
     */
    @Transactional(readOnly = true)
    public boolean isRead(Long userId, Long historyId) {
        return userSystemHistoryRepository.findByUserIdAndSystemHistoryId(userId, historyId)
                .map(UserSystemHistory::getIsRead)
                .orElse(false);
    }
}
