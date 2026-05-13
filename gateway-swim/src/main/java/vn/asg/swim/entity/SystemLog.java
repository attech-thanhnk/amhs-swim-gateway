package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * SystemLog entity — Critical events and errors recorded by the SWIM Component
 * for Control Position monitoring.
 */
@Entity
@Table(name = "system_log")
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

    public SystemLog() {}

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
