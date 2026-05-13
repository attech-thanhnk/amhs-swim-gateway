package vn.asg.converter.model.flight;

import vn.asg.converter.model.BaseMessage;

/**
 * Model chứa dữ liệu của một bản tin Kế hoạch bay (Flight Plan - FPL) chuẩn
 * ICAO.
 * Ví dụ: (FPL-VNA123-IS-A320/M-S/C-VVNB0100-N0450F330 DCT...-VVTS0200-0)
 */
public class FplMessage extends BaseMessage {

    private String aircraftId; // VNA123 (Trường 7)
    private String flightRules; // I (IFR), V (VFR), Y, Z (Trường 8)
    private String flightType; // S (Scheduled), N (Non-scheduled), G, M, X

    private String numberOfAircraft; // Thường là 1 hoặc null (Trường 9)
    private String aircraftType; // A320
    private String wakeTurbulence; // L (Light), M (Medium), H (Heavy), J (Super)

    private String equipment; // S/C (Trường 10)

    private String departureIcao; // VVNB (Trường 13)
    private String eobt; // Giờ dự kiến cất cánh (Trường 13 - EOBT)
    private String actualDepartureTime; // Giờ cất cánh thực tế (cho bản tin DEP)

    private String cruisingSpeed; // N0450 (Trường 15)
    private String cruisingLevel; // F330
    private String route; // DCT ...

    private String destinationIcao; // VVTS (Trường 16)
    private String totalEet; // 0200 (Total Estimated Elapsed Time)
    private String altDestination1; // Sân bay dự bị 1
    private String altDestination2; // Sân bay dự bị 2
    private String actualArrivalTime; // Giờ hạ cánh thực tế (cho bản tin ARR)

    private String otherInfo; // 0 hoặc PBN/A1B1... (Trường 18) - RAW

    // ── Field 18 Parsed Items ──
    private String dof; // DOF/260507 → Date of Flight (YYMMDD)
    private String registration; // REG/VN-A123 → Aircraft registration
    private String pbn; // PBN/A1B1C1D1O1S2 → Performance-Based Navigation capabilities
    private String eet; // EET/VVTS0010 VVDN0105 → Estimated Elapsed Times
    private String selcal; // SEL/ABCD → SELCAL code
    private String operator; // OPR/VIETJET → Operator name
    private String sts; // STS/ALTRV → Special handling reason
    private String remarks; // RMK/... → Remarks
    private String comCapabilities; // COM/... → Communication capabilities
    private String navCapabilities; // NAV/... → Navigation capabilities
    private String datApplications; // DAT/... → Data applications

    // --- Coordination Fields (for EST, CDN, ACP, CPL) ---
    private String boundaryPoint; // PANTO
    private String boundaryTime; // 1630
    private String clearedLevel; // F350

    public String getAircraftId() {
        return aircraftId;
    }

    public void setAircraftId(String aircraftId) {
        this.aircraftId = aircraftId;
    }

    public String getFlightRules() {
        return flightRules;
    }

    public void setFlightRules(String flightRules) {
        this.flightRules = flightRules;
    }

    public String getFlightType() {
        return flightType;
    }

    public void setFlightType(String flightType) {
        this.flightType = flightType;
    }

    public String getNumberOfAircraft() {
        return numberOfAircraft;
    }

    public void setNumberOfAircraft(String numberOfAircraft) {
        this.numberOfAircraft = numberOfAircraft;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(String aircraftType) {
        this.aircraftType = aircraftType;
    }

    public String getWakeTurbulence() {
        return wakeTurbulence;
    }

    public void setWakeTurbulence(String wakeTurbulence) {
        this.wakeTurbulence = wakeTurbulence;
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public String getDepartureIcao() {
        return departureIcao;
    }

    public void setDepartureIcao(String departureIcao) {
        this.departureIcao = departureIcao;
    }

    public String getEobt() {
        return eobt;
    }

    public void setEobt(String eobt) {
        this.eobt = eobt;
    }

    public String getActualDepartureTime() {
        return actualDepartureTime;
    }

    public void setActualDepartureTime(String actualDepartureTime) {
        this.actualDepartureTime = actualDepartureTime;
    }

    public String getCruisingSpeed() {
        return cruisingSpeed;
    }

    public void setCruisingSpeed(String cruisingSpeed) {
        this.cruisingSpeed = cruisingSpeed;
    }

    public String getCruisingLevel() {
        return cruisingLevel;
    }

    public void setCruisingLevel(String cruisingLevel) {
        this.cruisingLevel = cruisingLevel;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getDestinationIcao() {
        return destinationIcao;
    }

    public void setDestinationIcao(String destinationIcao) {
        this.destinationIcao = destinationIcao;
    }

    public String getTotalEet() {
        return totalEet;
    }

    public void setTotalEet(String totalEet) {
        this.totalEet = totalEet;
    }

    public String getAltDestination1() {
        return altDestination1;
    }

    public void setAltDestination1(String altDestination1) {
        this.altDestination1 = altDestination1;
    }

    public String getAltDestination2() {
        return altDestination2;
    }

    public void setAltDestination2(String altDestination2) {
        this.altDestination2 = altDestination2;
    }

    public String getActualArrivalTime() {
        return actualArrivalTime;
    }

    public void setActualArrivalTime(String actualArrivalTime) {
        this.actualArrivalTime = actualArrivalTime;
    }

    public String getOtherInfo() {
        return otherInfo;
    }

    public void setOtherInfo(String otherInfo) {
        this.otherInfo = otherInfo;
    }

    // Field 18 Getters/Setters
    public String getDof() {
        return dof;
    }

    public void setDof(String dof) {
        this.dof = dof;
    }

    public String getRegistration() {
        return registration;
    }

    public void setRegistration(String registration) {
        this.registration = registration;
    }

    public String getPbn() {
        return pbn;
    }

    public void setPbn(String pbn) {
        this.pbn = pbn;
    }

    public String getEet() {
        return eet;
    }

    public void setEet(String eet) {
        this.eet = eet;
    }

    public String getSelcal() {
        return selcal;
    }

    public void setSelcal(String selcal) {
        this.selcal = selcal;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getSts() {
        return sts;
    }

    public void setSts(String sts) {
        this.sts = sts;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getComCapabilities() {
        return comCapabilities;
    }

    public void setComCapabilities(String comCapabilities) {
        this.comCapabilities = comCapabilities;
    }

    public String getNavCapabilities() {
        return navCapabilities;
    }

    public void setNavCapabilities(String navCapabilities) {
        this.navCapabilities = navCapabilities;
    }

    public String getDatApplications() {
        return datApplications;
    }

    public void setDatApplications(String datApplications) {
        this.datApplications = datApplications;
    }

    public String getBoundaryPoint() {
        return boundaryPoint;
    }

    public void setBoundaryPoint(String boundaryPoint) {
        this.boundaryPoint = boundaryPoint;
    }

    public String getBoundaryTime() {
        return boundaryTime;
    }

    public void setBoundaryTime(String boundaryTime) {
        this.boundaryTime = boundaryTime;
    }

    public String getClearedLevel() {
        return clearedLevel;
    }

    public void setClearedLevel(String clearedLevel) {
        this.clearedLevel = clearedLevel;
    }

    @Override
    public String toString() {
        String type = getMessageType() != null ? getMessageType() : "FPL";
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(type);

        switch (type) {
            case "FPL", "AFP":
                appendFplFields(sb);
                break;
            case "CHG":
                appendChgFields(sb);
                break;
            case "DEP":
                appendDepFields(sb);
                break;
            case "ARR":
                appendArrFields(sb);
                break;
            case "CNL":
                appendCnlFields(sb);
                break;
            case "DLA":
                appendDlaFields(sb);
                break;
            case "EST":
                appendEstFields(sb);
                break;
            case "CDN":
            case "ACP":
            case "CPL":
                appendCoordinationFields(sb);
                break;
            case "ALR":
                appendAlrFields(sb);
                break;
            case "SPL":
                appendSplFields(sb);
                break;
            case "RQP", "RQS":
                appendRequestFields(sb);
                break;
            case "RCF":
                appendRcfFields(sb);
                break;
            default:
                appendFplFields(sb);
                break;
        }

        sb.append(")");
        return sb.toString();
    }

    private void appendFplFields(StringBuilder sb) {
        sb.append("-").append(aircraftId);
        sb.append("-").append(flightRules != null ? flightRules : "I");
        sb.append(flightType != null ? flightType : "S");
        sb.append("-");
        if (numberOfAircraft != null && !numberOfAircraft.isEmpty()) sb.append(numberOfAircraft);
        sb.append(aircraftType != null ? aircraftType : "ZZZZ");
        sb.append("/").append(wakeTurbulence != null ? wakeTurbulence : "M");
        sb.append("-").append(equipment != null ? equipment : "S/C");
        sb.append("-").append(departureIcao).append(eobt != null ? eobt : "0000");
        sb.append("-");
        sb.append(cruisingSpeed != null ? cruisingSpeed : "N0450");
        sb.append(cruisingLevel != null ? cruisingLevel : "F330");
        
        // --- ICAO Strict: Clean Route (Remove Destination/EET from Field 15) ---
        String cleanRoute = route != null ? route : "DCT";
        if (destinationIcao != null) {
            // Remove " DCT XXXX1234", " XXXX1234", " DCT XXXX", or " XXXX" at the end
            cleanRoute = cleanRoute.replaceAll("\\s+(DCT\\s+)?" + destinationIcao + "(\\d{4})?$", "");
        }
        sb.append(" ").append(cleanRoute.trim());
        
        sb.append("-").append(destinationIcao).append(totalEet != null ? totalEet : "0000");
        if (altDestination1 != null && !altDestination1.isEmpty()) sb.append(" ").append(altDestination1);
        if (altDestination2 != null && !altDestination2.isEmpty()) sb.append(" ").append(altDestination2);
        
        // --- ICAO Field 18 Rendering ---
        sb.append("-").append(buildField18());
    }

    private String buildField18() {
        if (otherInfo != null && !otherInfo.isEmpty() && !otherInfo.equals("0")) return otherInfo;
        
        StringBuilder f18 = new StringBuilder();
        if (sts != null) f18.append("STS/").append(sts).append(" ");
        if (pbn != null) f18.append("PBN/").append(pbn).append(" ");
        if (navCapabilities != null) f18.append("NAV/").append(navCapabilities).append(" ");
        if (comCapabilities != null) f18.append("COM/").append(comCapabilities).append(" ");
        if (datApplications != null) f18.append("DAT/").append(datApplications).append(" ");
        if (registration != null) f18.append("REG/").append(registration).append(" ");
        if (eet != null) f18.append("EET/").append(eet).append(" ");
        if (selcal != null) f18.append("SEL/").append(selcal).append(" ");
        if (dof != null) f18.append("DOF/").append(dof).append(" ");
        if (operator != null) f18.append("OPR/").append(operator).append(" ");
        if (remarks != null) f18.append("RMK/").append(remarks).append(" ");
        
        String result = f18.toString().trim();
        return result.isEmpty() ? "0" : result;
    }

    private void appendChgFields(StringBuilder sb) {
        sb.append("-").append(aircraftId);
        sb.append("-").append(departureIcao).append(eobt != null ? eobt : "");
        sb.append("-").append(destinationIcao);
        sb.append("-").append(buildField18());
    }

    private void appendDepFields(StringBuilder sb) {
        sb.append("-").append(aircraftId);
        sb.append("-").append(departureIcao).append(actualDepartureTime != null ? actualDepartureTime : (eobt != null ? eobt : ""));
        sb.append("-").append(destinationIcao);
        String f18 = buildField18();
        if (!f18.equals("0")) sb.append("-").append(f18);
    }

    private void appendArrFields(StringBuilder sb) {
        sb.append("-").append(aircraftId);
        sb.append("-").append(departureIcao);
        sb.append("-").append(destinationIcao).append(actualArrivalTime != null ? actualArrivalTime : "");
        String f18 = buildField18();
        if (!f18.equals("0")) sb.append("-").append(f18);
    }

    private void appendCnlFields(StringBuilder sb) {
        sb.append("-").append(aircraftId);
        sb.append("-").append(departureIcao).append(eobt != null ? eobt : "");
        sb.append("-").append(destinationIcao);
    }

    private void appendDlaFields(StringBuilder sb) {
        sb.append("-").append(aircraftId);
        sb.append("-").append(departureIcao).append(eobt != null ? eobt : "");
        sb.append("-").append(destinationIcao);
        String f18 = buildField18();
        if (!f18.equals("0")) sb.append("-").append(f18);
    }

    private void appendEstFields(StringBuilder sb) {
        sb.append("-").append(aircraftId);
        sb.append("-").append(departureIcao).append(eobt != null ? eobt : "");
        
        // Coordination data: [Point]/[Time][Level]
        if (boundaryPoint != null) {
            sb.append("-").append(boundaryPoint);
            if (boundaryTime != null) sb.append("/").append(boundaryTime);
            if (clearedLevel != null) sb.append(clearedLevel);
        } else if (remarks != null && remarks.contains("Boundary: ")) {
            sb.append("-").append(remarks.replace("Boundary: ", ""));
        }
        
        sb.append("-").append(destinationIcao).append(totalEet != null ? totalEet : "");
    }

    private void appendCoordinationFields(StringBuilder sb) {
        sb.append("-").append(aircraftId);
        sb.append("-").append(departureIcao).append(eobt != null ? eobt : "");
        if (boundaryPoint != null) {
            sb.append("-").append(boundaryPoint);
            if (boundaryTime != null) sb.append("/").append(boundaryTime);
            if (clearedLevel != null) sb.append(clearedLevel);
        }
        sb.append("-").append(destinationIcao).append(totalEet != null ? totalEet : "");
    }

    private void appendAlrFields(StringBuilder sb) {
        if (remarks != null && remarks.startsWith("Alert: ")) {
            sb.append("-").append(remarks.replace("Alert: ", ""));
        }
        sb.append("-").append(aircraftId);
        sb.append("-").append(flightRules != null ? flightRules : "I").append(flightType != null ? flightType : "S");
        sb.append("-").append(aircraftType != null ? aircraftType : "ZZZZ").append("/").append(wakeTurbulence != null ? wakeTurbulence : "M");
        if (equipment != null) sb.append("-").append(equipment);
        sb.append("-").append(departureIcao).append(eobt != null ? eobt : "");
        sb.append("-").append(cruisingSpeed).append(cruisingLevel).append(" ").append(route);
        sb.append("-").append(destinationIcao).append(totalEet != null ? totalEet : "");
        String f18 = buildField18();
        if (!f18.equals("0")) sb.append("-").append(f18);
    }

    private void appendSplFields(StringBuilder sb) {
        sb.append("-").append(aircraftId);
        sb.append("-").append(departureIcao).append(eobt != null ? eobt : "");
        sb.append("-").append(destinationIcao).append(totalEet != null ? totalEet : "");
        String f18 = buildField18();
        if (!f18.equals("0")) sb.append("-").append(f18);
    }

    private void appendRequestFields(StringBuilder sb) {
        sb.append("-").append(aircraftId);
        sb.append("-").append(departureIcao).append(eobt != null ? eobt : "");
        sb.append("-").append(destinationIcao);
    }

    private void appendRcfFields(StringBuilder sb) {
        sb.append("-").append(aircraftId);
        String f18 = buildField18();
        if (!f18.equals("0")) sb.append("-").append(f18);
    }

    @Override
    public void validate() throws Exception {
        if (aircraftId == null || aircraftId.isEmpty()) throw new Exception("Missing Aircraft Identification (Field 7)");
        if (departureIcao == null || departureIcao.isEmpty()) throw new Exception("Missing Departure Aerodrome (Field 13)");
        if (destinationIcao == null || destinationIcao.isEmpty()) throw new Exception("Missing Destination Aerodrome (Field 16)");
        
        String type = getMessageType();
        if ("FPL".equals(type)) {
            if (aircraftType == null || aircraftType.isEmpty()) throw new Exception("Missing Aircraft Type (Field 9)");
            if (eobt == null || eobt.isEmpty()) throw new Exception("Missing EOBT (Field 13)");
        }
    }
}
