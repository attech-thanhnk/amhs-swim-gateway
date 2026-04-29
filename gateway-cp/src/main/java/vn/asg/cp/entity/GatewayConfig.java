package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Bảng gateway_config — tham số cấu hình hệ thống, SWIM Component đọc và cache.
 */
@Entity
@Table(name = "gateway_config")
@Data
@NoArgsConstructor
public class GatewayConfig {

    @Id
    @Column(name = "config_key", length = 100)
    private String configKey;

    @Column(name = "config_value", length = 500)
    private String configValue;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
