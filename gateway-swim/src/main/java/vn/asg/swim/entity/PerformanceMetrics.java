package vn.asg.swim.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * PerformanceMetrics entity — Periodically recorded by the SWIM Component for
 * system monitoring.
 */
@Entity
@Table(name = "performance_metrics")
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

    public PerformanceMetrics() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Float getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(Float cpuUsage) { this.cpuUsage = cpuUsage; }
    public Float getHeapMemory() { return heapMemory; }
    public void setHeapMemory(Float heapMemory) { this.heapMemory = heapMemory; }
    public Integer getMsgInCount() { return msgInCount; }
    public void setMsgInCount(Integer msgInCount) { this.msgInCount = msgInCount; }
    public Integer getMsgOutCount() { return msgOutCount; }
    public void setMsgOutCount(Integer msgOutCount) { this.msgOutCount = msgOutCount; }
    public Integer getActiveThreads() { return activeThreads; }
    public void setActiveThreads(Integer activeThreads) { this.activeThreads = activeThreads; }
}
