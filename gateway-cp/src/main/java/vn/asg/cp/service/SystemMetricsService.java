package vn.asg.cp.service;

import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.asg.cp.dto.SystemLoadResponse;
import vn.asg.cp.dto.MySqlLoadResponse;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.asg.cp.dto.SystemOverviewResponse;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.RuntimeMXBean;

import javax.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMetricsService {

    private final JdbcTemplate jdbc;
    private SystemInfo systemInfo;
    private HardwareAbstractionLayer hal;
    private OperatingSystem os;

    @PostConstruct
    public void init() {
        try {
            // Khởi tạo OSHI một lần duy nhất
            systemInfo = new SystemInfo();
            hal = systemInfo.getHardware();
            os = systemInfo.getOperatingSystem();
            log.info("OSHI initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize OSHI", e);
        }
    }

    public SystemOverviewResponse getSystemLoad() {
        SystemLoadResponse gatewayCp = getGatewayload();
        MySqlLoadResponse mysql = getMySqlLoad();
        Date timecheck = new Date();

        return new SystemOverviewResponse(
                gatewayCp,
                mysql,
                timecheck
        );
    }

    public SystemLoadResponse getGatewayload() {
        try {
            // ========== 1. CPU INFO ==========
            CentralProcessor cpu = hal.getProcessor();
            
            long[] ticks = cpu.getSystemCpuLoadTicks();
            double systemCpuLoad = cpu.getSystemCpuLoadBetweenTicks(ticks) * 100;
            
            // Lấy process CPU load từ JMX (cách này luôn hoạt động)
            com.sun.management.OperatingSystemMXBean osBean = 
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double processCpuLoad = osBean.getProcessCpuLoad() * 100;
            
            // Xử lý NaN
            if (Double.isNaN(systemCpuLoad)) systemCpuLoad = 0;
            if (Double.isNaN(processCpuLoad)) processCpuLoad = 0;
            
            // Giới hạn 0-100%
            systemCpuLoad = Math.min(100, Math.max(0, systemCpuLoad));
            processCpuLoad = Math.min(100, Math.max(0, processCpuLoad));
            
            // Lấy thông tin chi tiết CPU
            CentralProcessor.ProcessorIdentifier cpuId = cpu.getProcessorIdentifier();
            String cpuName = cpuId.getName();
            int physicalCores = cpu.getPhysicalProcessorCount();
            int logicalCores = cpu.getLogicalProcessorCount();
            long cpuMaxFreqHz = cpu.getMaxFreq();
            
            // ========== 2. RAM INFO ==========
            GlobalMemory memory = hal.getMemory();
            long totalRamBytes = memory.getTotal();
            long availableRamBytes = memory.getAvailable();
            long usedRamBytes = totalRamBytes - availableRamBytes;
            double ramUsedPercent = (usedRamBytes * 100.0) / totalRamBytes;
            
            // Tính MB (làm tròn)
            long totalPhysicalMemoryMb = totalRamBytes / (1024 * 1024);
            long usedPhysicalMemoryMb = usedRamBytes / (1024 * 1024);

            // Lấy chi tiết từng thanh RAM (nếu có)
            List<SystemLoadResponse.PhysicalMemoryInfo> physicalMemoryModules = new ArrayList<>();
            try {
                for (PhysicalMemory pm : memory.getPhysicalMemory()) {
                    SystemLoadResponse.PhysicalMemoryInfo pmInfo = new SystemLoadResponse.PhysicalMemoryInfo();
                    pmInfo.setBankLabel(pm.getBankLabel());
                    pmInfo.setCapacityBytes(pm.getCapacity());
                    pmInfo.setClockSpeedHz(pm.getClockSpeed());
                    pmInfo.setManufacturer(pm.getManufacturer());
                    pmInfo.setMemoryType(pm.getMemoryType());
                    physicalMemoryModules.add(pmInfo);
                }
            } catch (Exception e) {
                log.warn("Cannot get physical memory details: {}", e.getMessage());
            }
            
            // ========== 3. DISK INFO ==========
            List<SystemLoadResponse.DiskInfo> disks = new ArrayList<>();
            try {
                // Lấy thông tin physical disks
                for (HWDiskStore diskStore : hal.getDiskStores()) {
                    SystemLoadResponse.DiskInfo diskInfo = new SystemLoadResponse.DiskInfo();
                    diskInfo.setName(diskStore.getName());
                    diskInfo.setModel(diskStore.getModel());
                    diskInfo.setSerial(diskStore.getSerial());
                    diskInfo.setSizeBytes(diskStore.getSize());
                    diskInfo.setReads(diskStore.getReads());
                    diskInfo.setWrites(diskStore.getWrites());
                    
                    // Lấy thông tin partitions cho disk này
                    List<SystemLoadResponse.PartitionInfo> partitions = new ArrayList<>();
                    for (HWPartition partition : diskStore.getPartitions()) {
                        SystemLoadResponse.PartitionInfo partInfo = new SystemLoadResponse.PartitionInfo();
                        partInfo.setMountPoint(partition.getMountPoint());
                        partInfo.setTotalBytes(partition.getSize());
                        
                        // Tìm OSFileStore tương ứng để lấy free space
                        for (OSFileStore fs : os.getFileSystem().getFileStores()) {
                            if (fs.getMount().equals(partition.getMountPoint()) || 
                                (partition.getMountPoint().isEmpty() && fs.getName().equals(partition.getIdentification()))) {
                                long freeBytes = fs.getUsableSpace();
                                partInfo.setFreeBytes(freeBytes);
                                long usedBytes = fs.getTotalSpace() - freeBytes;
                                double usedPercent = (usedBytes * 100.0) / fs.getTotalSpace();
                                partInfo.setUsedPercent(Math.min(100, Math.max(0, usedPercent)));
                                break;
                            }
                        }
                        partitions.add(partInfo);
                    }
                    diskInfo.setPartitions(partitions);
                    disks.add(diskInfo);
                }
            } catch (Exception e) {
                log.warn("Cannot get disk info: {}", e.getMessage());
            }
            
            // ========== 4. JVM INFO ==========
            MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long heapUsedMb = heapUsage.getUsed() / (1024 * 1024);
            long jvmUptimeSeconds = getJvmUptimeSeconds();
            String serviceStartTime = getServiceStartTime().toString();
            
            // ========== 5. Build Response ==========
            return new SystemLoadResponse(
                processCpuLoad,           // processCpuLoad
                systemCpuLoad,            // systemCpuLoad
                cpuName,                  // cpuName
                physicalCores,            // physicalCores
                logicalCores,             // logicalCores
                cpuMaxFreqHz,             // cpuMaxFreqHz
                totalRamBytes,            // totalRamBytes
                availableRamBytes,        // availableRamBytes
                ramUsedPercent,           // ramUsedPercent
                physicalMemoryModules,    // physicalMemoryModules
                usedPhysicalMemoryMb,     // THÊM: RAM đã dùng (MB)
                totalPhysicalMemoryMb,    // THÊM: Tổng RAM (MB)
                disks,                    // disks
                heapUsedMb,               // heapUsedMb
                jvmUptimeSeconds,         // jvmUptimeSeconds
                serviceStartTime          // serviceStartTime
            );
            
        } catch (Exception e) {
            log.error("Failed to get system load, returning fallback response", e);
            // return getFallbackResponse();
            return null;
        }
    }
    
    /**
     * Fallback response khi OSHI không hoạt động
     */
    // private SystemLoadResponse getFallbackResponse() {
    //     return new SystemLoadResponse(
    //         0.0,    // processCpuLoad
    //         0.0,    // systemCpuLoad
    //         "Unknown",  // cpuName
    //         0,      // physicalCores
    //         0,      // logicalCores
    //         0L,     // cpuMaxFreqHz
    //         0L,     // totalRamBytes
    //         0L,     // availableRamBytes
    //         0.0,    // ramUsedPercent
    //         new ArrayList<>(),  // physicalMemoryModules
    //         0L,     // usedPhysicalMemoryMb
    //         0L,     // totalPhysicalMemoryMb
    //         new ArrayList<>(),  // disks
    //         0L,     // heapUsedMb
    //         0L,     // jvmUptimeSeconds
    //         new Date().toString()  // serviceStartTime
    //     );
    // }

    public MySqlLoadResponse getMySqlLoad() {
        try {
            Integer connections = jdbc.queryForObject(
                    "SHOW GLOBAL STATUS LIKE 'Threads_connected'",
                    (rs, rowNum) -> rs.getInt("Value")
            );

            Integer maxConnections = jdbc.queryForObject(
                    "SHOW VARIABLES LIKE 'max_connections'",
                    (rs, rowNum) -> rs.getInt("Value")
            );

            if (connections == null) connections = 0;
            if (maxConnections == null) maxConnections = 100;

            double percent = ((double) connections / maxConnections) * 100;

            double cpu = 0;
            double ram = 0;

            try {
                String pid = getMysqlPid();
                double[] mysqlUsage = getMysqlCpuRam(pid);
                cpu = mysqlUsage[0];
                ram = mysqlUsage[1];
            } catch (Exception e) {
                log.warn("Cannot get MySQL CPU/RAM: {}", e.getMessage());
            }
            
            return new MySqlLoadResponse(
                    connections,
                    maxConnections,
                    percent,
                    cpu,
                    ram
            );
        } catch (Exception e) {
            log.error("Failed to get MySQL load", e);
            return new MySqlLoadResponse(0, 0, 0, 0, 0);
        }
    }

    private String getMysqlPid() throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pgrep mysqld"});
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String pid = reader.readLine();
        if (pid == null || pid.isEmpty()) {
            throw new RuntimeException("MySQL process not found");
        }
        return pid;
    }

    private double[] getMysqlCpuRam(String pid) throws Exception {
        Process p = Runtime.getRuntime()
                .exec(new String[]{"sh", "-c", "ps -p " + pid + " -o %cpu,%mem --no-headers"});

        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = reader.readLine();
        if (line == null || line.trim().isEmpty()) {
            throw new RuntimeException("No data from ps command");
        }
        
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) {
            throw new RuntimeException("Invalid ps output format");
        }

        double cpu = Double.parseDouble(parts[0]);
        double ram = Double.parseDouble(parts[1]);

        return new double[]{cpu, ram};
    }

    public long getJvmUptimeSeconds() {
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = rb.getUptime();
        return uptimeMs / 1000;
    }

    public Date getServiceStartTime() {
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        long startTime = rb.getStartTime();
        return new Date(startTime);
    }
}