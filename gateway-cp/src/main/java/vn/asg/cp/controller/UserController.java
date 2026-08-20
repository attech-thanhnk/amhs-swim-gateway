package vn.asg.cp.controller;

import vn.asg.cp.dto.ApiResponse;
import vn.asg.cp.dto.MarkAsReadRequest;
import vn.asg.cp.dto.PageData;
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
    public ResponseEntity<ApiResponse<PageData<User>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<User> usersPage = userService.getAllUsers(pageable);

        return ResponseEntity.ok(ApiResponse.ok(PageData.from(usersPage)));
    }

    /**
     * Lấy user theo ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(user -> ResponseEntity.ok(ApiResponse.ok(user)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found")));
    }

    /**
     * Tạo user mới
     */
    @PostMapping
    public ResponseEntity<ApiResponse<User>> createUser(@RequestBody User user) {
        user.setPassword(userService.encodePassword(user.getPassword()));
        user.setIsActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userService.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("User created successfully", savedUser));
    }

    /**
     * Cập nhật user
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        User updatedUser = userService.updateUser(user);
        return ResponseEntity.ok(ApiResponse.ok("User updated successfully", updatedUser));
    }

    /**
     * Xóa user
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.ok("User deleted successfully", Map.of("message", "User đã được xóa thành công")));
    }

    // ==================== Tìm kiếm và filter ====================

    /**
     * Tìm kiếm user theo username hoặc email
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<User>>> searchUsers(@RequestParam String keyword) {
        List<User> users = userService.searchUsers(keyword);
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    /**
     * Lấy user theo role
     */
    @GetMapping("/role/{role}")
    public ResponseEntity<ApiResponse<List<User>>> getUsersByRole(@PathVariable UserRole role) {
        List<User> users = userService.getUsersByRole(role);
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    /**
     * Lấy user đang active
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<User>>> getActiveUsers() {
        List<User> users = userService.getActiveUsers();
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    // ==================== Quản lý trạng thái ====================

    /**
     * Kích hoạt user
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<User>> activateUser(@PathVariable Long id) {
        User user = userService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.ok("User đã được kích hoạt", user));
    }

    /**
     * Vô hiệu hóa user
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<User>> deactivateUser(@PathVariable Long id) {
        User user = userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.ok("User đã bị vô hiệu hóa", user));
    }

    /**
     * Đổi mật khẩu (Hỗ trợ cả JSON body lẫn Query Parameters)
     */
    @PutMapping("/{id}/change-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @PathVariable Long id,
            @RequestParam(required = false) String oldPassword,
            @RequestParam(required = false) String newPassword,
            @RequestBody(required = false) Map<String, String> body) {

        String oldPwd = oldPassword != null ? oldPassword : (body != null ? body.get("oldPassword") : null);
        String newPwd = newPassword != null ? newPassword : (body != null ? body.get("newPassword") : null);

        if (oldPwd == null || newPwd == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Thiếu thông tin mật khẩu"));
        }

        boolean changed = userService.changePassword(id, oldPwd, newPwd);
        if (changed) {
            return ResponseEntity.ok(ApiResponse.ok("Đổi mật khẩu thành công", Map.of("message", "Đổi mật khẩu thành công")));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Mật khẩu cũ không đúng"));
        }
    }

    /**
     * Cập nhật thông tin đăng nhập lần cuối
     */
    @PutMapping("/{id}/update-last-login")
    public ResponseEntity<ApiResponse<Void>> updateLastLogin(@PathVariable Long id, @RequestParam String ip) {
        userService.updateLastLogin(id, ip);
        return ResponseEntity.ok(ApiResponse.ok("Last login updated", null));
    }

    // ==================== User + System History tích hợp ====================

    /**
     * Lấy danh sách system history kèm trạng thái đọc của user cụ thể
     */
    @GetMapping("/{userId}/system-histories")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserSystemHistories(@PathVariable Long userId) {
        if (!userService.existsById(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
        }

        List<SystemHistoryWithReadStatusDTO> histories = userSystemHistoryService.getHistoriesWithReadStatus(userId);
        long unreadCount = userSystemHistoryService.countUnread(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("histories", histories);
        response.put("unreadCount", unreadCount);
        response.put("totalCount", histories.size());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Lấy danh sách system history CHƯA ĐỌC của user
     */
    @GetMapping("/{userId}/system-histories/unread")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserUnreadHistories(@PathVariable Long userId) {
        if (!userService.existsById(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
        }

        List<SystemHistoryWithReadStatusDTO> unreadHistories = userSystemHistoryService.getUnreadHistories(userId);
        long unreadCount = userSystemHistoryService.countUnread(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("unreadHistories", unreadHistories);
        response.put("unreadCount", unreadCount);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Đánh dấu history là đã đọc cho user
     */
    @PutMapping("/{userId}/system-histories/{historyId}/read")
    public ResponseEntity<ApiResponse<Map<String, String>>> markHistoryAsRead(
            @PathVariable Long userId,
            @PathVariable Long historyId) {

        if (!userService.existsById(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
        }

        userSystemHistoryService.markAsRead(userId, historyId);
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu lịch sử là đã đọc", Map.of("message", "Đã đánh dấu lịch sử là đã đọc", "status", "success")));
    }

    /**
     * Đánh dấu nhiều history là đã đọc
     */
    @PutMapping("/{userId}/system-histories/read-multiple")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markMultipleHistoriesAsRead(
            @PathVariable Long userId,
            @RequestBody MarkAsReadRequest request) {

        if (!userService.existsById(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
        }

        userSystemHistoryService.markMultipleAsRead(userId, request.getHistoryIds());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Đã đánh dấu các lịch sử là đã đọc");
        response.put("status", "success");
        response.put("markedCount", request.getHistoryIds() != null ? request.getHistoryIds().size() : 0);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Đếm số lượng history chưa đọc của user (cho badge thông báo)
     */
    @GetMapping("/{userId}/system-histories/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadHistoryCount(@PathVariable Long userId) {
        if (!userService.existsById(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
        }

        long unreadCount = userSystemHistoryService.countUnread(userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("userId", userId, "unreadCount", unreadCount)));
    }

    /**
     * Kiểm tra user đã đọc history cụ thể chưa
     */
    @GetMapping("/{userId}/system-histories/{historyId}/is-read")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHistoryReadStatus(
            @PathVariable Long userId,
            @PathVariable Long historyId) {

        if (!userService.existsById(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
        }

        boolean isRead = userSystemHistoryService.isRead(userId, historyId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("userId", userId, "historyId", historyId, "isRead", isRead)));
    }

    /**
     * Lấy thông tin tổng quan của user (bao gồm cả unread count)
     */
    @GetMapping("/{userId}/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserDashboard(@PathVariable Long userId) {
        User user = userService.getUserById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("User not found"));
        }

        long unreadHistoryCount = userSystemHistoryService.countUnread(userId);
        Map<String, Object> response = Map.of(
                "user", user,
                "unreadHistoryCount", unreadHistoryCount,
                "isActive", user.getIsActive(),
                "role", user.getRole());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}