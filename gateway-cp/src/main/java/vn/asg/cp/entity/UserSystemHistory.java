package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bảng user_system_history — SWIM Component ghi khi có lỗi/sự kiện quan trọng, CP đọc.
 */
@Entity
@Table(name = "user_system_history")
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UserSystemHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "system_history_id")
    private Long systemHistoryId;

    @Column(name = "is_read")
    private Boolean isRead;
}
