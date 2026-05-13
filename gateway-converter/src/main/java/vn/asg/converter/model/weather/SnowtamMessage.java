package vn.asg.converter.model.weather;

import vn.asg.converter.model.BaseMessage;

/**
 * Model cho bản tin SNOWTAM (Tình trạng tuyết tại sân bay).
 */
public class SnowtamMessage extends BaseMessage {
    private String aerodrome; // A)
    private String observationTime; // B)
    private String runway; // C)
    private String clearedWidth; // D)
    private String clearedLength; // E)
    private String deposit; // F)
    private String depth; // G)
    private String friction; // H)
    private String criticalBanks; // J)
    private String lights; // K)
    private String contamination; // L)
    private String taxiway; // M)
    private String taxiwayContamination; // N)
    private String apron; // P)
    private String nextPlanned; // R)
    private String plainText; // T)

    public String getAerodrome() { return aerodrome; }
    public void setAerodrome(String aerodrome) { this.aerodrome = aerodrome; }

    public String getObservationTime() { return observationTime; }
    public void setObservationTime(String observationTime) { this.observationTime = observationTime; }

    public String getRunway() { return runway; }
    public void setRunway(String runway) { this.runway = runway; }

    public String getClearedWidth() { return clearedWidth; }
    public void setClearedWidth(String clearedWidth) { this.clearedWidth = clearedWidth; }

    public String getClearedLength() { return clearedLength; }
    public void setClearedLength(String clearedLength) { this.clearedLength = clearedLength; }

    public String getDeposit() { return deposit; }
    public void setDeposit(String deposit) { this.deposit = deposit; }

    public String getDepth() { return depth; }
    public void setDepth(String depth) { this.depth = depth; }

    public String getFriction() { return friction; }
    public void setFriction(String friction) { this.friction = friction; }

    public String getCriticalBanks() { return criticalBanks; }
    public void setCriticalBanks(String criticalBanks) { this.criticalBanks = criticalBanks; }

    public String getLights() { return lights; }
    public void setLights(String lights) { this.lights = lights; }

    public String getContamination() { return contamination; }
    public void setContamination(String contamination) { this.contamination = contamination; }

    public String getTaxiway() { return taxiway; }
    public void setTaxiway(String taxiway) { this.taxiway = taxiway; }

    public String getTaxiwayContamination() { return taxiwayContamination; }
    public void setTaxiwayContamination(String taxiwayContamination) { this.taxiwayContamination = taxiwayContamination; }

    public String getApron() { return apron; }
    public void setApron(String apron) { this.apron = apron; }

    public String getNextPlanned() { return nextPlanned; }
    public void setNextPlanned(String nextPlanned) { this.nextPlanned = nextPlanned; }

    public String getPlainText() { return plainText; }
    public void setPlainText(String plainText) { this.plainText = plainText; }

    @Override
    public String toString() {
        if (getOriginalTac() != null && !getOriginalTac().isEmpty()) return getOriginalTac();
        StringBuilder sb = new StringBuilder();
        // ICAO SNOWTAM syntax: (SNOWTAM [serial]
        sb.append("(SNOWTAM ").append(getMessageId() != null ? getMessageId() : "0001").append("\n");
        if (aerodrome != null) sb.append("A) ").append(aerodrome).append(" ");
        if (observationTime != null) sb.append("B) ").append(observationTime).append("\n");
        if (runway != null) sb.append("C) ").append(runway).append(" ");
        if (clearedWidth != null) sb.append("D) ").append(clearedWidth).append(" ");
        if (clearedLength != null) sb.append("E) ").append(clearedLength).append("\n");
        if (deposit != null) sb.append("F) ").append(deposit).append(" ");
        if (depth != null) sb.append("G) ").append(depth).append(" ");
        if (friction != null) sb.append("H) ").append(friction).append("\n");
        if (criticalBanks != null) sb.append("J) ").append(criticalBanks).append(" ");
        if (lights != null) sb.append("K) ").append(lights).append(" ");
        if (contamination != null) sb.append("L) ").append(contamination).append("\n");
        if (taxiway != null) sb.append("M) ").append(taxiway).append(" ");
        if (taxiwayContamination != null) sb.append("N) ").append(taxiwayContamination).append(" ");
        if (apron != null) sb.append("P) ").append(apron).append("\n");
        if (nextPlanned != null) sb.append("R) ").append(nextPlanned).append("\n");
        if (plainText != null) sb.append("T) ").append(plainText).append("\n");
        sb.append(")");
        return sb.toString().trim();
    }

    @Override
    public void validate() throws Exception {
        if (observationTime == null) throw new Exception("Missing Observation Time (Field B)");
    }
}
