package vn.asg.converter.model;

/**
 * Model chứa dữ liệu của một bản tin Kế hoạch bay (Flight Plan - FPL) chuẩn ICAO.
 * Ví dụ: (FPL-VNA123-IS-A320/M-S/C-VVNB0100-N0450F330 DCT...-VVTS0200-0)
 */
public class FplMessage {
    
    private String messageType;      // FPL
    private String aircraftId;       // VNA123 (Trường 7)
    private String flightRules;      // I (IFR), V (VFR), Y, Z (Trường 8)
    private String flightType;       // S (Scheduled), N (Non-scheduled), G, M, X
    
    private String numberOfAircraft; // Thường là 1 hoặc null (Trường 9)
    private String aircraftType;     // A320
    private String wakeTurbulence;   // L (Light), M (Medium), H (Heavy), J (Super)
    
    private String equipment;        // S/C (Trường 10)
    
    private String departureIcao;    // VVNB (Trường 13)
    private String departureTime;    // 0100 (Thời gian dự kiến cất cánh - EOBT)
    
    private String cruisingSpeed;    // N0450 (Trường 15)
    private String cruisingLevel;    // F330
    private String route;            // DCT ...
    
    private String destinationIcao;  // VVTS (Trường 16)
    private String totalEet;         // 0200 (Total Estimated Elapsed Time)
    private String altDestination1;  // Sân bay dự bị 1
    private String altDestination2;  // Sân bay dự bị 2
    
    private String otherInfo;        // 0 hoặc PBN/A1B1... (Trường 18) - RAW

    // ── Field 18 Parsed Items ──
    private String dof;              // DOF/260507 → Date of Flight (YYMMDD)
    private String registration;     // REG/VN-A123 → Aircraft registration
    private String pbn;              // PBN/A1B1C1D1O1S2 → Performance-Based Navigation capabilities
    private String eet;              // EET/VVTS0010 VVDN0105 → Estimated Elapsed Times
    private String selcal;           // SEL/ABCD → SELCAL code
    private String operator;         // OPR/VIETJET → Operator name
    private String sts;              // STS/ALTRV → Special handling reason
    private String remarks;          // RMK/... → Remarks
    private String comCapabilities;  // COM/... → Communication capabilities
    private String navCapabilities;  // NAV/... → Navigation capabilities
    private String datApplications;  // DAT/... → Data applications

    // Getters and Setters

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getAircraftId() { return aircraftId; }
    public void setAircraftId(String aircraftId) { this.aircraftId = aircraftId; }

    public String getFlightRules() { return flightRules; }
    public void setFlightRules(String flightRules) { this.flightRules = flightRules; }

    public String getFlightType() { return flightType; }
    public void setFlightType(String flightType) { this.flightType = flightType; }

    public String getNumberOfAircraft() { return numberOfAircraft; }
    public void setNumberOfAircraft(String numberOfAircraft) { this.numberOfAircraft = numberOfAircraft; }

    public String getAircraftType() { return aircraftType; }
    public void setAircraftType(String aircraftType) { this.aircraftType = aircraftType; }

    public String getWakeTurbulence() { return wakeTurbulence; }
    public void setWakeTurbulence(String wakeTurbulence) { this.wakeTurbulence = wakeTurbulence; }

    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }

    public String getDepartureIcao() { return departureIcao; }
    public void setDepartureIcao(String departureIcao) { this.departureIcao = departureIcao; }

    public String getDepartureTime() { return departureTime; }
    public void setDepartureTime(String departureTime) { this.departureTime = departureTime; }

    public String getCruisingSpeed() { return cruisingSpeed; }
    public void setCruisingSpeed(String cruisingSpeed) { this.cruisingSpeed = cruisingSpeed; }

    public String getCruisingLevel() { return cruisingLevel; }
    public void setCruisingLevel(String cruisingLevel) { this.cruisingLevel = cruisingLevel; }

    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }

    public String getDestinationIcao() { return destinationIcao; }
    public void setDestinationIcao(String destinationIcao) { this.destinationIcao = destinationIcao; }

    public String getTotalEet() { return totalEet; }
    public void setTotalEet(String totalEet) { this.totalEet = totalEet; }

    public String getAltDestination1() { return altDestination1; }
    public void setAltDestination1(String altDestination1) { this.altDestination1 = altDestination1; }

    public String getAltDestination2() { return altDestination2; }
    public void setAltDestination2(String altDestination2) { this.altDestination2 = altDestination2; }

    public String getOtherInfo() { return otherInfo; }
    public void setOtherInfo(String otherInfo) { this.otherInfo = otherInfo; }

    // Field 18 Getters/Setters
    public String getDof() { return dof; }
    public void setDof(String dof) { this.dof = dof; }

    public String getRegistration() { return registration; }
    public void setRegistration(String registration) { this.registration = registration; }

    public String getPbn() { return pbn; }
    public void setPbn(String pbn) { this.pbn = pbn; }

    public String getEet() { return eet; }
    public void setEet(String eet) { this.eet = eet; }

    public String getSelcal() { return selcal; }
    public void setSelcal(String selcal) { this.selcal = selcal; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getSts() { return sts; }
    public void setSts(String sts) { this.sts = sts; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getComCapabilities() { return comCapabilities; }
    public void setComCapabilities(String comCapabilities) { this.comCapabilities = comCapabilities; }

    public String getNavCapabilities() { return navCapabilities; }
    public void setNavCapabilities(String navCapabilities) { this.navCapabilities = navCapabilities; }

    public String getDatApplications() { return datApplications; }
    public void setDatApplications(String datApplications) { this.datApplications = datApplications; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(FPL");
        sb.append("-").append(aircraftId != null && !aircraftId.isEmpty() ? aircraftId : "UNKNOWN");
        sb.append("-").append(flightRules != null && !flightRules.isEmpty() ? flightRules : "I");
        sb.append(flightType != null && !flightType.isEmpty() ? flightType : "S");
        
        sb.append("-");
        if (numberOfAircraft != null && !numberOfAircraft.isEmpty()) {
            sb.append(numberOfAircraft);
        }
        sb.append(aircraftType != null && !aircraftType.isEmpty() ? aircraftType : "ZZZZ");
        sb.append("/");
        sb.append(wakeTurbulence != null && !wakeTurbulence.isEmpty() ? wakeTurbulence : "M");
        
        sb.append("-");
        sb.append(equipment != null && !equipment.isEmpty() ? equipment : "S/C");
        
        sb.append("-");
        sb.append(departureIcao != null && !departureIcao.isEmpty() ? departureIcao : "ZZZZ");
        sb.append(departureTime != null && !departureTime.isEmpty() ? departureTime : "0000");
        
        sb.append("-");
        if (cruisingSpeed != null && !cruisingSpeed.isEmpty()) {
            sb.append(cruisingSpeed);
        } else {
            sb.append("N0450");
        }
        if (cruisingLevel != null && !cruisingLevel.isEmpty()) {
            sb.append(cruisingLevel);
        } else {
            sb.append("F330");
        }
        sb.append(" ").append(route != null && !route.isEmpty() ? route : "DCT");
        
        sb.append("-");
        sb.append(destinationIcao != null && !destinationIcao.isEmpty() ? destinationIcao : "ZZZZ");
        sb.append(totalEet != null && !totalEet.isEmpty() ? totalEet : "0000");
        if (altDestination1 != null && !altDestination1.isEmpty()) {
            sb.append(" ").append(altDestination1);
        }
        
        sb.append("-");
        sb.append(otherInfo != null && !otherInfo.isEmpty() ? otherInfo : "0");
        
        sb.append(")");
        return sb.toString();
    }
}
