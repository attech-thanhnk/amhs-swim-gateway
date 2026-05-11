/*
 */
package vn.asg.converter.reverter.entity;

import java.io.Serializable;

/**
 *
 * @author ThanhNk
 */
public class SIGMET extends EntityBase implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private String createDate = "";
    private String locationIndicatorOfFir = "";
    private String numberIdentification = "";
    private String validityPeriod = "";
    private String validFrom = "";
    private String validTo = "";
    private String fir = "";
    
    private String dateofObs = "";
    private String heardLine = "";
    private String intensity = "";
    private String levell = "";
    private String localtion = "";
    private String movement = "";
    private String obsfcst = "";
    private String phenomenon = "";
    private String raw = "";

//    private String typeofReport = "";
    private String visibility = "";
    private String locationIndicatorMWO = "";
    
    private String cancellation = "";
    
    @Override
    public String getTypeofReport() {
        return "SIGMET";
    }

//<editor-fold defaultstate="collapsed" desc=" Class properties ">
    public String getCreateDate() {
        return createDate;
    }
    
    public void setCreateDate(String createDate) {
        this.createDate = createDate;
    }
    
    public String getValidityPeriod() {
        return validityPeriod;
    }
    
    public void setValidityPeriod(String validityPeriod) {
        this.validityPeriod = validityPeriod;
    }
    
    public String getDateofOBS() {
        return dateofObs;
    }
    
    public void setDateofOBS(String dateofOBS) {
        this.dateofObs = dateofOBS;
    }
    
    public String getFir() {
        return fir;
    }
    
    public void setFir(String fir) {
        this.fir = fir;
    }
    
    public String getHeardLine() {
        return heardLine;
    }
    
    public String getNumberIdentification() {
        return numberIdentification;
    }
    
    public void setNumberIdentification(String numberIdentification) {
        this.numberIdentification = numberIdentification;
    }
    
    public String getDateofObs() {
        return dateofObs;
    }
    
    public void setDateofObs(String dateofObs) {
        this.dateofObs = dateofObs;
    }
    
    public void setHeardLine(String heardLine) {
        this.heardLine = heardLine;
    }
    
    public String getIntensity() {
        return intensity;
    }
    
    public void setIntensity(String intensity) {
        this.intensity = intensity;
    }
    
    public String getLevell() {
        return levell;
    }
    
    public void setLevell(String levell) {
        this.levell = levell;
    }
    
    public String getLocaltion() {
        return localtion;
    }
    
    public void setLocaltion(String localtion) {
        this.localtion = localtion;
    }
    
    public String getMovement() {
        return movement;
    }
    
    public void setMovement(String movement) {
        this.movement = movement;
    }
    
    public String getObsfcst() {
        return obsfcst;
    }
    
    public void setObsfcst(String obsfcst) {
        this.obsfcst = obsfcst;
    }
    
    public String getPhenomenon() {
        return phenomenon;
    }
    
    public void setPhenomenon(String phenomenon) {
        this.phenomenon = phenomenon;
    }
    
    public String getRaw() {
        return raw;
    }
    
    public void setRaw(String raw) {
        this.raw = raw;
    }
    
    public String getValidFrom() {
        return validFrom;
    }
    
    public void setValidFrom(String validFrom) {
        this.validFrom = validFrom;
    }
    
    public String getValidTo() {
        return validTo;
    }
    
    public void setValidTo(String validTo) {
        this.validTo = validTo;
    }

//    public String getTypeofReport() {
//        return typeofReport;
//    }
//    public void setTypeofReport(String typeofReport) {
//        this.typeofReport = typeofReport;
//    }
    public String getVisibility() {
        return visibility;
    }
    
    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }
    
    public String getLocationIndicatorMWO() {
        return locationIndicatorMWO;
    }
    
    public void setLocationIndicatorMWO(String locationIndicatorMWO) {
        this.locationIndicatorMWO = locationIndicatorMWO;
    }
    
    public String getLocationIndicatorOfFir() {
        return locationIndicatorOfFir;
    }
    
    public void setLocationIndicatorOfFir(String locationIndicatorOfFir) {
        this.locationIndicatorOfFir = locationIndicatorOfFir;
    }
    
    public String getCancellation() {
        return cancellation;
    }
    
    public void setCancellation(String cancellation) {
        this.cancellation = cancellation;
    }

//</editor-fold>
    public SIGMET() {
    }
//    private Common common = new Common();

//    public SIGMET(SIGMETType objectType) throws IWXXMParsingException {
//        parse(objectType);
//    }
    @Override
    public String toString() {
        AFTNMessage aftn = new AFTNMessage("=");
        aftn.append(this.locationIndicatorOfFir);
        aftn.append(this.getTypeofReport());
        aftn.append(this.numberIdentification);
        aftn.append(this.validityPeriod);
        aftn.append(this.locationIndicatorMWO).append("\n", true);
        aftn.append(this.fir);
        aftn.append(this.phenomenon);
        aftn.append(this.dateofObs);
        aftn.append(this.localtion);
        aftn.append(this.levell);
        aftn.append(this.movement);
        aftn.append(this.intensity);
        aftn.append(this.obsfcst);
        aftn.append(this.cancellation);
        return aftn.flush().replace("\r", "").replace("\n\n", "\n").trim();
    }
    
}

