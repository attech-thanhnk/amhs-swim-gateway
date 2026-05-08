package vn.asg.cp.dto;

import java.util.Date;

public class SystemLoadResponse {
    public double processCpu; // % CPU user process
    public double systemCpu; // % CPU Server
    public long heapUsedMb; // % Ram user process
    public Long totalRamMb; // Total Ram Server
    public double ramPercent; // % Ram Server

    public SystemLoadResponse(
        double processCpu, 
        double systemCpu, 
        long heapUsedMb, 
        Long totalRamMb,
        double ramPercent
    ) {
        this.processCpu = processCpu;
        this.systemCpu = systemCpu;
        this.heapUsedMb = heapUsedMb;
        this.totalRamMb = totalRamMb;
        this.ramPercent = ramPercent;
    }
}