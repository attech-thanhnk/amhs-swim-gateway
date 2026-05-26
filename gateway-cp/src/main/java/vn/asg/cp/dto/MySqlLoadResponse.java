package vn.asg.cp.dto;

public class MySqlLoadResponse {
    public int connections;
    public int maxConnections;
    public double connectionPercent;
    public double cpuPercent;
    public double ramPercent;

    public MySqlLoadResponse(int connections, int maxConnections, double connectionPercent, double cpuPercent, double ramPercent) {
        this.connections = connections;
        this.maxConnections = maxConnections;
        this.connectionPercent = connectionPercent;
        this.cpuPercent = cpuPercent;
        this.ramPercent = ramPercent;
    }
}
