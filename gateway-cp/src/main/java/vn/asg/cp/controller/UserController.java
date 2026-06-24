package vn.asg.cp.controller;

import vn.asg.cp.dto.MarkAsReadRequest;
import vn.asg.cp.dto.SystemHistoryWithReadStatusDTO;
import vn.asg.cp.entity.User;
import vn.asg.cp.entity.UserRole;
import vn.asg.cp.service.UserService;
import vn.asg.cp.service.UserSystemHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserSystemHistoryService userSystemHistoryService;

    // ==================== CRUD cơ bản ====================

    /**
     * Lấy danh sách tất cả users (phân trang)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<User> usersPage = userService.getAllUsers(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("users", usersPage.getContent());
        response.put("currentPage", usersPage.getNumber());
        response.put("totalItems", usersPage.getTotalElements());
        response.put("totalPages", usersPage.getTotalPages());
        response.put("pageSize", usersPage.getSize());

        return ResponseEntity.ok(response);
    }

    /**
     * Lấy user theo ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Tạo user mới
     */
    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        // Mã hóa password trước khi lưu
        user.setPassword(userService.encodePassword(user.getPassword()));
        user.setIsActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userService.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    /**
     * Cập nhật user
     */
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        User updatedUser = userService.updateUser(user);
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * Xóa user
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "User đã được xóa thành công");
        return ResponseEntity.ok(response);
    }

    // ==================== Tìm kiếm và filter ====================

    /**
     * Tìm kiếm user theo username hoặc email
     */
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam String keyword) {
        List<User> users = userService.searchUsers(keyword);
        return ResponseEntity.ok(users);
    }

    /**
     * Lấy user theo role
     */
    @GetMapping("/role/{role}")
    public ResponseEntity<List<User>> getUsersByRole(@PathVariable UserRole role) {
        List<User> users = userService.getUsersByRole(role);
        return ResponseEntity.ok(users);
    }

    /**
     * Lấy user đang active
     */
    @GetMapping("/active")
    public ResponseEntity<List<User>> getActiveUsers() {
        List<User> users = userService.getActiveUsers();
        return ResponseEntity.ok(users);
    }

    // ==================== Quản lý trạng thái ====================

    /**
     * Kích hoạt user
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<Map<String, Object>> activateUser(@PathVariable Long id) {
        User user = userService.activateUser(id);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User đã được kích hoạt");
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    /**
     * Vô hiệu hóa user
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateUser(@PathVariable Long id) {
        User user = userService.deactivateUser(id);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User đã bị vô hiệu hóa");
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    /**
     * Đổi mật khẩu
     */
    @PutMapping("/{id}/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable Long id,
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {

        boolean changed = userService.changePassword(id, oldPassword, newPassword);
        if (changed) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Đổi mật khẩu thành công");
            return ResponseEntity.ok(response);
        } else {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Mật khẩu cũ không đúng");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Cập nhật thông tin đăng nhập lần cuối
     */
    @PutMapping("/{id}/update-last-login")
    public ResponseEntity<Void> updateLastLogin(@PathVariable Long id, @RequestParam String ip) {
        userService.updateLastLogin(id, ip);
        return ResponseEntity.ok().build();
    }

    // ==================== User + System History tích hợp ====================

    /**
     * Lấy danh sách system history kèm trạng thái đọc của user cụ thể
     */
    @GetMapping("/{userId}/system-histories")
    public ResponseEntity<Map<String, Object>> getUserSystemHistories(@PathVariable Long userId) {
        // Kiểm tra user tồn tại
        if (!userService.existsById(userId)) {
            return ResponseEntity.notFound().build();
        }

        List<SystemHistoryWithReadStatusDTO> histories = userSystemHistoryService.getHistoriesWithReadStatus(userId);
        long unreadCount = userSystemHistoryService.countUnread(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("histories", histories);
        response.put("unreadCount", unreadCount);
        response.put("totalCount", histories.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh sách system history CHƯA ĐỌC của user
     */
    @GetMapping("/{userId}/system-histories/unread")
    public ResponseEntity<Map<String, Object>> getUserUnreadHistories(@PathVariable Long userId) {
        if (!userService.existsById(userId)) {
            return ResponseEntity.notFound().build();
        }

        List<SystemHistoryWithReadStatusDTO> unreadHistories = userSystemHistoryService.getUnreadHistories(userId);
        long unreadCount = userSystemHistoryService.countUnread(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("unreadHistories", unreadHistories);
        response.put("unreadCount", unreadCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Đánh dấu history là đã đọc cho user
     */
    @PutMapping("/{userId}/system-histories/{historyId}/read")
    public ResponseEntity<Map<String, String>> markHistoryAsRead(
            @PathVariable Long userId,
            @PathVariable Long historyId) {

        if (!userService.existsById(userId)) {
            return ResponseEntity.notFound().build();
        }

        userSystemHistoryService.markAsRead(userId, historyId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Đã đánh dấu lịch sử là đã đọc");
        response.put("status", "success");

        return ResponseEntity.ok(response);
    }

    /**
     * Đánh dấu nhiều history là đã đọc
     */
    @PutMapping("/{userId}/system-histories/read-multiple")
    public ResponseEntity<Map<String, Object>> markMultipleHistoriesAsRead(
            @PathVariable Long userId,
            @RequestBody MarkAsReadRequest request) {

        if (!userService.existsById(userId)) {
            return ResponseEntity.notFound().build();
        }

        userSystemHistoryService.markMultipleAsRead(userId, request.getHistoryIds());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Đã đánh dấu các lịch sử là đã đọc");
        response.put("status", "success");
        response.put("markedCount", request.getHistoryIds() != null ? request.getHistoryIds().size() : 0);

        return ResponseEntity.ok(response);
    }

    /**
     * Đếm số lượng history chưa đọc của user (cho badge thông báo)
     */
    @GetMapping("/{userId}/system-histories/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadHistoryCount(@PathVariable Long userId) {
        if (!userService.existsById(userId)) {
            return ResponseEntity.notFound().build();
        }

        long unreadCount = userSystemHistoryService.countUnread(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("unreadCount", unreadCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Kiểm tra user đã đọc history cụ thể chưa
     */
    @GetMapping("/{userId}/system-histories/{historyId}/is-read")
    public ResponseEntity<Map<String, Object>> checkHistoryReadStatus(
            @PathVariable Long userId,
            @PathVariable Long historyId) {

        if (!userService.existsById(userId)) {
            return ResponseEntity.notFound().build();
        }

        boolean isRead = userSystemHistoryService.isRead(userId, historyId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("historyId", historyId);
        response.put("isRead", isRead);

        return ResponseEntity.ok(response);
    }

    /**
     * Lấy thông tin tổng quan của user (bao gồm cả unread count)
     */
    @GetMapping("/{userId}/dashboard")
    public ResponseEntity<Map<String, Object>> getUserDashboard(@PathVariable Long userId) {
        User user = userService.getUserById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        long unreadHistoryCount = userSystemHistoryService.countUnread(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("user", user);
        response.put("unreadHistoryCount", unreadHistoryCount);
        response.put("isActive", user.getIsActive());
        response.put("role", user.getRole());

        return ResponseEntity.ok(response);
    }
}