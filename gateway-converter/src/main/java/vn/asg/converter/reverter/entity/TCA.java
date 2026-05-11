/*
 */
package vn.asg.converter.reverter.entity;

import java.io.Serializable;
import vn.asg.converter.reverter.iwxxm.Common;

/**
 *
 * @author ThanhNk
 */
public class TCA extends EntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    private String typeOfReport = "";
    private String timeOfOrigin = "";
    private String nameOfTCAC = "";
    private String advisoryNumber = "";
    private String nameOfTropicalCyclone = "";
    private String positionOfTheCenter = "";
    private String movement = "";
    private String movementSpeed = "";
    private String circleByCenterPointSpeed = "";
    private String circleByCenterPointDirection = "";
    private String centerPressure = "";
    private String maximumSurfaceWind = "";
    private String forecastOfThePhenomena6 = "";
    private String forecastOfThePhenomena12 = "";
    private String forecastOfThePhenomena18 = "";
    private String forecastOfThePhenomena24 = "";
    private String forecastOfThePhenomenaWin6 = "";
    private String forecastOfThePhenomenaWin12 = "";
    private String forecastOfThePhenomenaWin18 = "";
    private String forecastOfThePhenomenaWin24 = "";
    private String remarks = "";
    private String nextAdvisory = "";
    private final Common common = new Common();

    public TCA() {
    }

    //<editor-fold defaultstate="collapsed" desc=" Class properties ">
    public String getTypeOfReport() {
        return typeOfReport;
    }

    public void setTypeOfReport(String typeOfReport) {
        this.typeOfReport = typeOfReport;
    }

    public String getTimeOfOrigin() {
        return timeOfOrigin;
    }

    public void setTimeOfOrigin(String timeOfOrigin) {
        this.timeOfOrigin = timeOfOrigin;
    }

    public String getNameOfTCAC() {
        return nameOfTCAC;
    }

    public void setNameOfTCAC(String nameOfTCAC) {
        this.nameOfTCAC = nameOfTCAC;
    }

    public String getAdvisoryNumber() {
        return advisoryNumber;
    }

    public void setAdvisoryNumber(String advisoryNumber) {
        this.advisoryNumber = advisoryNumber;
    }

    public String getNameOfTropicalCyclone() {
        return nameOfTropicalCyclone;
    }

    public void setNameOfTropicalCyclone(String nameOfTropicalCyclone) {
        this.nameOfTropicalCyclone = nameOfTropicalCyclone;
    }

    public String getPositionOfTheCenter() {
        return positionOfTheCenter;
    }

    public void setPositionOfTheCenter(String positionOfTheCenter) {
        this.positionOfTheCenter = positionOfTheCenter;
    }

    public String getMovement() {
        return movement;
    }

    public void setMovement(String movement) {
        this.movement = movement;
    }

    public String getMovementSpeed() {
        return movementSpeed;
    }

    public void setMovementSpeed(String movementSpeed) {
        this.movementSpeed = movementSpeed;
    }

    public String getCircleByCenterPointSpeed() {
        return circleByCenterPointSpeed;
    }

    public void setCircleByCenterPointSpeed(String circleByCenterPointSpeed) {
        this.circleByCenterPointSpeed = circleByCenterPointSpeed;
    }

    public String getCircleByCenterPointDirection() {
        return circleByCenterPointDirection;
    }

    public void setCircleByCenterPointDirection(String circleByCenterPointDirection) {
        this.circleByCenterPointDirection = circleByCenterPointDirection;
    }

    public String getCenterPressure() {
        return centerPressure;
    }

    public void setCenterPressure(String centerPressure) {
        this.centerPressure = centerPressure;
    }

    public String getMaximumSurfaceWind() {
        return maximumSurfaceWind;
    }

    public void setMaximumSurfaceWind(String maximumSurfaceWind) {
        this.maximumSurfaceWind = maximumSurfaceWind;
    }

    public String getForecastOfThePhenomena6() {
        return forecastOfThePhenomena6;
    }

    public void setForecastOfThePhenomena6(String forecastOfThePhenomena6) {
        this.forecastOfThePhenomena6 = forecastOfThePhenomena6;
    }

    public String getForecastOfThePhenomena12() {
        return forecastOfThePhenomena12;
    }

    public void setForecastOfThePhenomena12(String forecastOfThePhenomena12) {
        this.forecastOfThePhenomena12 = forecastOfThePhenomena12;
    }

    public String getForecastOfThePhenomena18() {
        return forecastOfThePhenomena18;
    }

    public void setForecastOfThePhenomena18(String forecastOfThePhenomena18) {
        this.forecastOfThePhenomena18 = forecastOfThePhenomena18;
    }

    public String getForecastOfThePhenomena24() {
        return forecastOfThePhenomena24;
    }

    public void setForecastOfThePhenomena24(String forecastOfThePhenomena24) {
        this.forecastOfThePhenomena24 = forecastOfThePhenomena24;
    }

    public String getForecastOfThePhenomenaWin6() {
        return forecastOfThePhenomenaWin6;
    }

    public void setForecastOfThePhenomenaWin6(String forecastOfThePhenomenaWin6) {
        this.forecastOfThePhenomenaWin6 = forecastOfThePhenomenaWin6;
    }

    public String getForecastOfThePhenomenaWin12() {
        return forecastOfThePhenomenaWin12;
    }

    public void setForecastOfThePhenomenaWin12(String forecastOfThePhenomenaWin12) {
        this.forecastOfThePhenomenaWin12 = forecastOfThePhenomenaWin12;
    }

    public String getForecastOfThePhenomenaWin18() {
        return forecastOfThePhenomenaWin18;
    }

    public void setForecastOfThePhenomenaWin18(String forecastOfThePhenomenaWin18) {
        this.forecastOfThePhenomenaWin18 = forecastOfThePhenomenaWin18;
    }

    public String getForecastOfThePhenomenaWin24() {
        return forecastOfThePhenomenaWin24;
    }

    public void setForecastOfThePhenomenaWin24(String forecastOfThePhenomenaWin24) {
        this.forecastOfThePhenomenaWin24 = forecastOfThePhenomenaWin24;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getNextAdvisory() {
        return nextAdvisory;
    }

    public void setNextAdvisory(String nextAdvisory) {
        this.nextAdvisory = nextAdvisory;
    }

    //</editor-fold>
    @Override
    public String getTypeofReport() {
        return "TC ADVISORY";
    }

    //<editor-fold defaultstate="collapsed" desc="Override">
//    @Override
//    public int hashCode() {
//        // return this.identify.hashCode();
//        // TODO: implements
//        return 0;
//    }
//
//    @Override
//    public boolean equals(Object object) {
//        if (!(object instanceof METAR)) {
//            return false;
//        }
//        METARBulletin other = (METARBulletin) object;
//
//        return this.getIdentify().equalsIgnoreCase(other.getIdentify());
//    }
    //</editor-fold>
    @Override
    public String toString() {
        AFTNMessage message = new AFTNMessage("");

        // TODO: Implement
        message.append(this.getTypeofReport()).append("\n", true);
        message.append("DTG: ");
        message.append(this.getTimeOfOrigin()).append("\n", true);
        message.append("TCAC: ");
        message.append(this.getNameOfTCAC()).append("\n", true);
        message.append("TC: ");
        message.append(this.getNameOfTropicalCyclone()).append("\n", true);
        message.append("ADVISORY NR: ");
        message.append(this.getAdvisoryNumber()).append("\n", true); 
        message.append("OBS PSN: ");
        message.append(this.getPositionOfTheCenter()).append("\n", true);
        message.append("CB: WI");
        message.append(this.getCircleByCenterPointSpeed()).append(" ");
        message.append(this.getCircleByCenterPointDirection()).append("\n", true);
        message.append("MOV:");
        message.append(this.getMovement());
        message.append(this.getMovementSpeed()).append("\n", true);
        message.append("C:");
        message.append(this.getCenterPressure()).append("\n", true);
        message.append("MAX WIND:");
        message.append(this.getMaximumSurfaceWind()).append("\n", true);
        message.append("FCST PSN +6 HR:");
        message.append(this.getForecastOfThePhenomena6()).append("\n", true);
        message.append("FCST MAX WIND +6 HR:");
        message.append(this.getForecastOfThePhenomenaWin6()).append("\n", true);
        message.append("FCST PSN +12 HR:");
        message.append(this.getForecastOfThePhenomena12()).append("\n", true);
        message.append("FCST MAX WIND +12 HR:");
        message.append(this.getForecastOfThePhenomenaWin12()).append("\n", true);
        message.append("FCST PSN +18 HR:");
        message.append(this.getForecastOfThePhenomena18()).append("\n", true);
        message.append("FCST MAX WIND +18 HR:");
        message.append(this.getForecastOfThePhenomenaWin18()).append("\n", true);
        message.append("FCST PSN +24 HR:");
        message.append(this.getForecastOfThePhenomena24()).append("\n", true);
        message.append("FCST MAX WIND +24 HR:");
        message.append(this.getForecastOfThePhenomenaWin24()).append("\n", true);
        message.append("RMK:");
        message.append(this.getRemarks()).append("\n", true);
        message.append("NXT MSG:");
        message.append(this.getNextAdvisory()).append("\n", true);
        return message.flush().replace("\r", "").replace("\n\n", "\n").trim();
    }
}

