package vn.asg.cp.controller;

import vn.asg.cp.dto.MarkAsReadRequest;
import vn.asg.cp.dto.SystemHistoryWithReadStatusDTO;
import vn.asg.cp.service.UserSystemHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user-system-history")
@RequiredArgsConstructor
public class UserSystemHistoryController {

    private final UserSystemHistoryService userSystemHistoryService;

    /**
     * Lấy danh sách system history kèm trạng thái đọc của user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getHistoriesWithReadStatus(@PathVariable Long userId) {
        List<SystemHistoryWithReadStatusDTO> histories = userSystemHistoryService.getHistoriesWithReadStatus(userId);
        long unreadCount = userSystemHistoryService.countUnread(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("histories", histories);
        response.put("unreadCount", unreadCount);
        response.put("totalCount", histories.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh sách system history CHƯA ĐỌC của user
     */
    @GetMapping("/user/{userId}/unread")
    public ResponseEntity<Map<String, Object>> getUnreadHistories(@PathVariable Long userId) {
        List<SystemHistoryWithReadStatusDTO> unreadHistories = userSystemHistoryService.getUnreadHistories(userId);
        long unreadCount = userSystemHistoryService.countUnread(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("unreadHistories", unreadHistories);
        response.put("unreadCount", unreadCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Đánh dấu một history là đã đọc
     */
    @PutMapping("/user/{userId}/history/{historyId}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable Long userId,
            @PathVariable Long historyId) {

        userSystemHistoryService.markAsRead(userId, historyId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Đã đánh dấu là đã đọc");
        response.put("status", "success");

        return ResponseEntity.ok(response);
    }

    /**
     * Đánh dấu nhiều history là đã đọc
     */
    @PutMapping("/user/{userId}/read-multiple")
    public ResponseEntity<Map<String, Object>> markMultipleAsRead(
            @PathVariable Long userId,
            @RequestBody MarkAsReadRequest request) {

        userSystemHistoryService.markMultipleAsRead(userId, request.getHistoryIds());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Đã đánh dấu các history là đã đọc");
        response.put("status", "success");
        response.put("markedCount", request.getHistoryIds() != null ? request.getHistoryIds().size() : 0);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/user/{userId}/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(@PathVariable Long userId) {
        int markedCount = userSystemHistoryService.markAllAsRead(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Đã đánh dấu đọc toàn bộ thông báo");
        response.put("markedCount", markedCount);
        return ResponseEntity.ok(response);
    }

    /**
     * Đếm số lượng history chưa đọc (dùng cho badge notification)
     */
    @GetMapping("/user/{userId}/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(@PathVariable Long userId) {
        long unreadCount = userSystemHistoryService.countUnread(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("unreadCount", unreadCount);
        response.put("userId", userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Kiểm tra user đã đọc history cụ thể chưa
     */
    @GetMapping("/user/{userId}/history/{historyId}/is-read")
    public ResponseEntity<Map<String, Object>> checkIsRead(
            @PathVariable Long userId,
            @PathVariable Long historyId) {

        boolean isRead = userSystemHistoryService.isRead(userId, historyId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("historyId", historyId);
        response.put("isRead", isRead);

        return ResponseEntity.ok(response);
    }
}
