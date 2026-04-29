package vn.asg.swim.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * SystemLog entity — Critical events and errors recorded by the SWIM Component
 * for Control Position monitoring.
 */
@Entity
@Table(name = "system_log")
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class SystemLog {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    /** INFO / WARN / ERROR */
    @Column(name = "level", length = 10)
    private String level;

    /** AMHS_COMPONENT / SWIM_COMPONENT / ITCU / CP */
    @Column(name = "module", length = 30)
    private String module;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** READ / UNREAD */
    @Column(name = "status", length = 10)
    private String status;
}
