package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bảng server_info — Thông tin máy chủ, CP đọc.
 */
@Entity
@Table(name = "server_info")
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ServerInfo {
    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    /** Server name */
    @Column(name = "serverName")
    private String serverName;

    /** IP address */
    @Column(name = "ipAddress")
    private String ipAddress;

    /** Server version */
    @Column(name = "version")
    private String version;

    /** History version description */
    @Column(name = "description")
    private String description;
}
