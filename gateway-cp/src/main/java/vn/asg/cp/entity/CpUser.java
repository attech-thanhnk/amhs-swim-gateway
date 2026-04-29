package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "cp_users")
@Data
@NoArgsConstructor
public class CpUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "username", length = 50, unique = true, nullable = false)
    private String username;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "role", length = 20)
    private String role = "USER";

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
