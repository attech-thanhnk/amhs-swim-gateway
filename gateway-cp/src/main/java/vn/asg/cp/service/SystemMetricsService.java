package vn.asg.cp.service;

import lombok.RequiredArgsConstructor;
import vn.asg.cp.dto.SystemLoadResponse;
import vn.asg.cp.dto.MySqlLoadResponse;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.asg.cp.dto.SystemOverviewResponse;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Date;

import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

@Service
@RequiredArgsConstructor
public class SystemMetricsService {

    private final JdbcTemplate jdbc;
    

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
        OperatingSystemMXBean osBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        int cores = osBean.getAvailableProcessors();

        // CPU load raw từ JVM
        double systemCpuLoadRaw = osBean.getSystemCpuLoad();
        double processCpuLoadRaw = osBean.getProcessCpuLoad();

        // Convert sang % giống Task Manager / Grafana
        double systemCpuLoad = (systemCpuLoadRaw * 100) / cores;
        double processCpuLoad = (processCpuLoadRaw * 100) / cores;

        if (systemCpuLoad >= 1)
            systemCpuLoad = 0.99;

        if (processCpuLoad >= 1)
            processCpuLoad = 0.99;

        // 2. RAM HỆ THỐNG (Physical Memory)
        long totalPhysicalMemory = osBean.getTotalPhysicalMemorySize();
        long freePhysicalMemory = osBean.getFreePhysicalMemorySize();
        long usedPhysicalMemoryMb = (totalPhysicalMemory - freePhysicalMemory) / 1024 / 1024;
        long totalPhysicalMemoryMb = totalPhysicalMemory / 1024 / 1024;
        double totalRamPercent = (double) usedPhysicalMemoryMb / totalPhysicalMemoryMb * 100;

        // 3. RAM HEAP (Phần Java đang dùng - giữ lại từ code cũ của bạn)
        MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long heapUsedMb = heapUsage.getUsed() / 1024 / 1024;

        return new SystemLoadResponse(
                processCpuLoad,      // CPU App Java
                systemCpuLoad,       // % CPU toàn hệ thống
                usedPhysicalMemoryMb,// RAM hệ thống đang dùng (MB)
                totalPhysicalMemoryMb, // Tổng RAM vật lý (MB)
                totalRamPercent,     // % RAM hệ thống
                heapUsedMb,
                this.getJvmUptimeSeconds(),
                this.getServiceStartTime()
        );
    }

    public MySqlLoadResponse getMySqlLoad() {
        int connections = jdbc.queryForObject(
                "SHOW GLOBAL STATUS LIKE 'Threads_connected'",
                (rs, rowNum) -> rs.getInt("Value")
        );

        int maxConnections = jdbc.queryForObject(
                "SHOW VARIABLES LIKE 'max_connections'",
                (rs, rowNum) -> rs.getInt("Value")
        );

        double percent = ((double) connections / maxConnections);

        double cpu = 0;
        double ram = 0;

        try {
            String pid = getMysqlPid();
            double[] mysqlUsage = getMysqlCpuRam(pid);
            cpu = mysqlUsage[0];
            ram = mysqlUsage[1];
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return new MySqlLoadResponse(
                connections,
                maxConnections,
                percent,
                cpu,
                ram
        );
    }

    private String getMysqlPid() throws Exception {
        Process p = Runtime.getRuntime().exec("pgrep mysqld");
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        return reader.readLine(); // PID
    }

    private double[] getMysqlCpuRam(String pid) throws Exception {
        Process p = Runtime.getRuntime()
                .exec("ps -p " + pid + " -o %cpu,%mem --no-headers");

        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = reader.readLine().trim();

        String[] parts = line.split("\\s+");

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

    // Set limit 99% load
    private double normalizeCpu(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0;
        }

        value = value * 100;

        if (value > 99) value = 99;

        return Math.round(value * 10.0) / 10.0;
    }
}
