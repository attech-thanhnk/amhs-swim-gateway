package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.entity.CpUser;
import vn.asg.cp.repository.CpUserRepository;
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

    private final CpUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public static class LoginRequest {
        public String username;
        public String password;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body) {
        String username = body.username;
        String password = body.password;

        CpUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }

        // Cập nhật last_login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = tokenProvider.generateToken(user.getUsername(), user.getRole());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "expiresIn", tokenProvider.getExpirationMs() / 1000,
                "username", user.getUsername(),
                "role", user.getRole()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // Stateless JWT — client xóa token phía client
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Refresh token — cấp lại token mới dựa trên token cũ còn hiệu lực.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);
        if (!tokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired token"));
        }

        String username = tokenProvider.getUsernameFromToken(token);
        return userRepository.findByUsername(username).map(user -> {
            String newToken = tokenProvider.generateToken(user.getUsername(), user.getRole());
            return ResponseEntity.ok(Map.of(
                    "token", newToken,
                    "expiresIn", tokenProvider.getExpirationMs() / 1000,
                    "username", user.getUsername(),
                    "role", user.getRole()));
        }).orElse(ResponseEntity.status(401).build());
    }

    /**
     * Đổi mật khẩu cho người dùng hiện tại.
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing passwords"));
        }

        String token = authHeader.substring(7);
        String username = tokenProvider.getUsernameFromToken(token);

        return userRepository.findByUsername(username).map(user -> {
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                return ResponseEntity.status(400).body(Map.of("error", "Incorrect old password"));
            }
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        }).orElse(ResponseEntity.status(404).build());
    }
}
