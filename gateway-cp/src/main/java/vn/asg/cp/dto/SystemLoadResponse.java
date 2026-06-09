package vn.asg.cp.dto;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SystemLoadResponse {
    // CPU Info
    private double processCpuLoad;      // CPU % của process này
    private double systemCpuLoad;       // CPU % toàn hệ thống
    private String cpuName;             // Tên CPU
    private int physicalCores;          // Số nhân vật lý
    private int logicalCores;           // Số luồng (cores ảo)
    private long cpuMaxFreqHz;          // Tần số tối đa (Hz)

    // RAM Info
    private long totalRamBytes;         // Tổng RAM (bytes)
    private long availableRamBytes;     // RAM khả dụng (bytes)
    private double ramUsedPercent;      // % RAM đã dùng
    private List<PhysicalMemoryInfo> physicalMemoryModules; // Chi tiết từng thanh RAM
    
    private long usedPhysicalMemoryMb;  // RAM đã dùng (MB) - 3072
    private long totalPhysicalMemoryMb; 
    // Disk Info
    private List<DiskInfo> disks;       // Danh sách ổ cứng
    
    // JVM Info
    private long heapUsedMb;            // Heap JVM đang dùng (MB)
    private long jvmUptimeSeconds;      // Thời gian JVM đã chạy (giây)
    private String serviceStartTime;    // Thời gian start service
    
    // ========== INNER CLASS DEFINITIONS ==========
    
    /**
     * Thông tin chi tiết từng thanh RAM
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PhysicalMemoryInfo {
        private String bankLabel;        // Vị trí khe cắm (Bank)
        private long capacityBytes;      // Dung lượng (bytes)
        private long clockSpeedHz;       // Tốc độ (Hz)
        private String manufacturer;     // Nhà sản xuất
        private String memoryType;       // Loại (DDR3, DDR4, DDR5)
    }
    
    /**
     * Thông tin ổ cứng vật lý
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DiskInfo {
        private String name;             // Tên/đường dẫn ổ cứng (vd: /dev/sda)
        private String model;            // Model ổ cứng (vd: Samsung SSD 970 EVO)
        private String serial;           // Serial number
        private long sizeBytes;          // Tổng dung lượng (bytes)
        private long reads;              // Số lần đọc (IO)
        private long writes;             // Số lần ghi (IO)
        private List<PartitionInfo> partitions; // Các phân vùng bên trong
    }
    
    /**
     * Thông tin phân vùng (partition)
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PartitionInfo {
        private String mountPoint;       // Điểm mount (C:\, /, /home, ...)
        private long totalBytes;         // Tổng dung lượng partition (bytes)
        private long freeBytes;          // Dung lượng trống (bytes)
        private double usedPercent;      // % đã sử dụng
    }
}