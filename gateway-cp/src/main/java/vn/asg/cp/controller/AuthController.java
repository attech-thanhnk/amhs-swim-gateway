package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.ApiResponse;
import vn.asg.cp.entity.User;
import vn.asg.cp.repository.UserRepository;
import vn.asg.cp.security.JwtTokenProvider;

import java.time.Duration;
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

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_TIME_MINUTES = 15;

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

        if (username == null || username.isBlank() || password == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username and password must not be empty"));
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid username or password"));
        }

        // Kiểm tra tài khoản có bị vô hiệu hóa không
        if (Boolean.FALSE.equals(user.getIsActive())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Account has been deactivated. Please contact the administrator."));
        }

        // Kiểm tra tài khoản có đang bị khóa tạm thời do nhập sai quá nhiều lần không
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
                long minutesRemaining = Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1;
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.error("Account is temporarily locked due to excessive failed attempts. Please try again after " 
                                + minutesRemaining + " minutes."));
            } else {
                // Hết thời gian khóa, tự động mở khóa
                user.setLockedUntil(null);
                user.setFailedAttempts(0);
            }
        }

        if (passwordEncoder.matches(password, user.getPassword())) {
            // Đăng nhập thành công: reset số lần sai và thời gian khóa
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
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
            // Nhập sai mật khẩu: tăng số lần sai
            int currentAttempts = (user.getFailedAttempts() != null ? user.getFailedAttempts() : 0) + 1;
            user.setFailedAttempts(currentAttempts);

            if (currentAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_TIME_MINUTES));
                userRepository.save(user);
                log.warn("User '{}' has been temporarily locked for {} minutes due to {} consecutive failed login attempts.", 
                        username, LOCK_TIME_MINUTES, currentAttempts);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.error("Account has been temporarily locked for " + LOCK_TIME_MINUTES 
                                + " minutes due to " + MAX_FAILED_ATTEMPTS + " consecutive failed login attempts."));
            } else {
                userRepository.save(user);
                int remaining = MAX_FAILED_ATTEMPTS - currentAttempts;
                log.info("User '{}' failed login attempt {}/{}.", username, currentAttempts, MAX_FAILED_ATTEMPTS);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid password. You have " + remaining 
                                + " attempts remaining before temporary lockout."));
            }
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

