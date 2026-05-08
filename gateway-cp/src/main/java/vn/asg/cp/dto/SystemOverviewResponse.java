package vn.asg.cp.dto;

import java.util.Date;

public class SystemOverviewResponse {
    public SystemLoadResponse gatewayCp;
    public MySqlLoadResponse mysql;
    Date timecheck;

    public SystemOverviewResponse(
        SystemLoadResponse gatewayCp,
        MySqlLoadResponse mysql,
        Date timecheck
    ) {
        this.gatewayCp = gatewayCp;
        this.mysql = mysql;
        this.timecheck = timecheck;
    }
}
