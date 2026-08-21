package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.ApiResponse;
import vn.asg.cp.entity.User;
import vn.asg.cp.repository.UserRepository;
import vn.asg.cp.security.JwtTokenProvider;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * POST /api/auth/login — đăng nhập, trả về JWT
 * POST /api/auth/logout — đăng xuất (stateless, client tự xóa token)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public static class LoginRequest {
        public String username;
        public String password;
    }

    public static class VerifyPasswordRequest {
        public Long userId;
        public String password;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody LoginRequest body) {
        String username = body.username;
        String password = body.password;

        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            String token = tokenProvider.generateToken(user.getUsername(), user.getRole().toString());
            Map<String, Object> data = Map.of(
                    "token", token,
                    "expiresIn", tokenProvider.getExpirationMs() / 1000,
                    "username", user.getUsername(),
                    "role", user.getRole(),
                    "userId", user.getId());
            return ResponseEntity.ok(ApiResponse.ok("Login successful", data));
        } else {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid username or password"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout() {
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully", Map.of("message", "Logged out successfully")));
    }

    /**
     * Refresh token — cấp lại token mới dựa trên token cũ còn hiệu lực.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authorization header missing or invalid"));
        }
        String token = authHeader.substring(7);
        if (!tokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid or expired token"));
        }

        String username = tokenProvider.getUsernameFromToken(token);
        return userRepository.findByUsername(username).map(user -> {
            String newToken = tokenProvider.generateToken(user.getUsername(), user.getRole().toString());
            Map<String, Object> data = Map.of(
                    "token", newToken,
                    "expiresIn", tokenProvider.getExpirationMs() / 1000,
                    "username", user.getUsername(),
                    "role", user.getRole(),
                    "userId", user.getId());
            return ResponseEntity.ok(ApiResponse.ok("Token refreshed successfully", data));
        }).orElse(ResponseEntity.status(401).body(ApiResponse.error("User not found")));
    }

    /**
     * Đổi mật khẩu cho người dùng hiện tại.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authorization header missing or invalid"));
        }

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing passwords"));
        }

        String token = authHeader.substring(7);
        String username = tokenProvider.getUsernameFromToken(token);

        return userRepository.findByUsername(username).map(user -> {
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                return ResponseEntity.status(400).body(ApiResponse.<Map<String, String>>error("Incorrect old password"));
            }
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", Map.of("message", "Password changed successfully")));
        }).orElse(ResponseEntity.status(404).body(ApiResponse.error("User not found")));
    }

    @PostMapping("/verify-password")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyPassword(@RequestBody VerifyPasswordRequest body) {
        Long userId = body.userId;
        String password = body.password;

        User user = userRepository.findById(userId).orElse(null);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.ok(ApiResponse.ok("Password is correct", Map.of(
                    "success", true,
                    "message", "Password is correct")));
        } else {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid username or password", Map.of(
                    "success", false,
                    "message", "Invalid username or password")));
        }
    }
}

