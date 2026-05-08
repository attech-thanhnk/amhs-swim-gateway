package vn.asg.cp.service;

import lombok.RequiredArgsConstructor;
import vn.asg.cp.dto.SystemLoadResponse;
import vn.asg.cp.dto.MySqlLoadResponse;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.asg.cp.dto.SystemOverviewResponse;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Date;

import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

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
        // CPU
        OperatingSystemMXBean osBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        double processCpuLoad = osBean.getProcessCpuLoad() * 100;
        double systemCpuLoad  = osBean.getSystemCpuLoad() * 100;

        // RAM Heap
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();

        long heapUsedMb = heapUsage.getUsed() / 1024 / 1024;
        
        // SYSTEM RAM
        long totalRamMb = osBean.getTotalPhysicalMemorySize() / 1024 / 1024;

        double ramPercent = (double) heapUsedMb / totalRamMb;

        return new SystemLoadResponse(
                processCpuLoad,
                systemCpuLoad,
                heapUsedMb,
                totalRamMb,
                ramPercent
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
}
