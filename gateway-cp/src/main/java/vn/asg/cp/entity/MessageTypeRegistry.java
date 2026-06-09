package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bảng message_type_registry — Danh mục các loại điện văn ATS.
 */
@Entity
@Table(name = "message_type_registry")
@Data
@NoArgsConstructor
public class MessageTypeRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_type", length = 50, nullable = false, unique = true)
    private String messageType;

    @Column(name = "detect_pattern", length = 255, nullable = false)
    private String detectPattern;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "note", length = 500)
    private String note;
}
