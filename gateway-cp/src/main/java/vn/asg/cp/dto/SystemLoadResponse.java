package vn.asg.cp.dto;

import java.util.Date;

public class SystemLoadResponse {
    public double processCpu; // % CPU user process
    public double systemCpu; // % CPU Server
    public Long usedPhysicalMemoryMb;
    public Long totalPhysicalMemoryMb; // Total Ram Server
    public double totalRamPercent; // % Ram Server
    public Long heapUsedMb; // % Ram user process
    public Long serviceUptimeSec;
    public Date serviceStartTime;

    public SystemLoadResponse(
        double processCpu, 
        double systemCpu, 
        Long usedPhysicalMemoryMb,
        Long totalPhysicalMemoryMb,
        double totalRamPercent,
        Long heapUsedMb,
        Long serviceUptimeSec,
        Date serviceStartTime
    ) {
        this.processCpu = processCpu;
        this.systemCpu = systemCpu;
        this.usedPhysicalMemoryMb = usedPhysicalMemoryMb;
        this.totalPhysicalMemoryMb = totalPhysicalMemoryMb;
        this.totalRamPercent = totalRamPercent;
        this.heapUsedMb = heapUsedMb;
        this.serviceUptimeSec = serviceUptimeSec;
        this.serviceStartTime = serviceStartTime;
    }
}