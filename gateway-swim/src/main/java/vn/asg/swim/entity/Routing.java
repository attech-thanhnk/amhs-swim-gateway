package vn.asg.swim.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Routing Table — Defines Simple Routing Rules.
 * direction=OUT: AMHS → SWIM, direction=IN: SWIM → AMHS.
 */
@Entity
@Table(name = "routing")
@Data
@NoArgsConstructor
public class Routing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "direction", length = 3, nullable = false)
    private String direction; // 'IN' or 'OUT'

    // ========== INBOUND DIRECTION (SWIM → AMHS) ==========
    @Column(name = "receive_topic", length = 100)
    private String receiveTopic;

    @Column(name = "message_filter", length = 100)
    private String messageFilter;

    @Column(name = "recipients", length = 500)
    private String recipients;

    @Column(name = "originator", length = 8)
    private String originator;

    // ========== OUTBOUND DIRECTION (AMHS → SWIM) ==========
    @Column(name = "message_type", length = 50)
    private String messageType;

    @Column(name = "send_topic", length = 100)
    private String sendTopic;

    // ========== COMMON PROPERTIES ==========
    @Column(name = "priority")
    private Integer priority = 100;

    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
