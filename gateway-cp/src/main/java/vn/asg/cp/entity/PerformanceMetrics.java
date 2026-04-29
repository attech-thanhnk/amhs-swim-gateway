package vn.asg.cp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Bảng performance_metrics — SWIM Component ghi định kỳ.
 */
@Entity
@Table(name = "performance_metrics")
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PerformanceMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp")
    private Instant timestamp;

    @Column(name = "cpu_usage")
    private Float cpuUsage;

    @Column(name = "heap_memory")
    private Float heapMemory;

    @Column(name = "msg_in_count")
    private Integer msgInCount;

    @Column(name = "msg_out_count")
    private Integer msgOutCount;

    @Column(name = "active_threads")
    private Integer activeThreads;
}
