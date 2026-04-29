package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.CreateUserRequest;
import vn.asg.cp.dto.UpdateUserRequest;
import vn.asg.cp.entity.CpUser;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.exception.ValidationException;
import vn.asg.cp.repository.CpUserRepository;

import java.util.List;

/**
 * Controller cho quản trị người dùng (Admin only).
 * URL: /api/admin/users
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final CpUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<CpUser>> list() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<CpUser> getOne(@PathVariable("uuid") String uuid) {
        CpUser user = userRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("User", uuid));
        return ResponseEntity.ok(user);
    }

    @PostMapping
    public ResponseEntity<CpUser> create(@RequestBody CreateUserRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new ValidationException("Username already exists");
        }

        CpUser user = new CpUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());

        return ResponseEntity.status(HttpStatus.CREATED).body(userRepository.save(user));
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<CpUser> update(@PathVariable("uuid") String uuid, @RequestBody UpdateUserRequest request) {
        CpUser user = userRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("User", uuid));

        if (request.getUsername() != null)
            user.setUsername(request.getUsername());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRole() != null)
            user.setRole(request.getRole());

        return ResponseEntity.ok(userRepository.save(user));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable("uuid") String uuid) {
        if (!userRepository.existsById(uuid)) {
            throw new ResourceNotFoundException("User", uuid);
        }
        userRepository.deleteById(uuid);
        return ResponseEntity.noContent().build();
    }
}
