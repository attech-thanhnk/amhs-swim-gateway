/*
 */
package vn.asg.converter.reverter.entity;

import java.io.Serializable;

/**
 *
 * @author ThanhNk
 */
public class SPACEWX extends EntityBase implements Serializable {

    String TypeOfReport = "";
    String TimeOfOrigin = "";
    String NameOfSWXC = "";
    String AdvisoryNumber = "";
    String SpaceWeatherEffectIntensity = "";
    String NumberOfAdvisoryBeingReplaced = "";
    String ObservedOrExpectedSpaceWeatherPhenomena = "";
    String ForecastOfThePhenomena6 = "";
    String ForecastOfThePhenomena12 = "";
    String ForecastOfThePhenomena18 = "";
    String ForecastOfThePhenomena24 = "";
    String Remarks = "";
    String NextAdvisory = "";

    //<editor-fold defaultstate="collapsed" desc=" Class properties ">
    public String getTypeOfReport() {
        return TypeOfReport;
    }

    public void setTypeOfReport(String TypeOfReport) {
        this.TypeOfReport = TypeOfReport;
    }

    public String getTimeOfOrigin() {
        return TimeOfOrigin;
    }

    public void setTimeOfOrigin(String TimeOfOrigin) {
        this.TimeOfOrigin = TimeOfOrigin;
    }

    public String getNameOfSWXC() {
        return NameOfSWXC;
    }

    public void setNameOfSWXC(String NameOfSWXC) {
        this.NameOfSWXC = NameOfSWXC;
    }

    public String getAdvisoryNumber() {
        return AdvisoryNumber;
    }

    public void setAdvisoryNumber(String AdvisoryNumber) {
        this.AdvisoryNumber = AdvisoryNumber;
    }

    public String getSpaceWeatherEffectIntensity() {
        return SpaceWeatherEffectIntensity;
    }

    public void setSpaceWeatherEffectIntensity(String SpaceWeatherEffectIntensity) {
        this.SpaceWeatherEffectIntensity = SpaceWeatherEffectIntensity;
    }

    public String getNumberOfAdvisoryBeingReplaced() {
        return NumberOfAdvisoryBeingReplaced;
    }

    public void setNumberOfAdvisoryBeingReplaced(String NumberOfAdvisoryBeingReplaced) {
        this.NumberOfAdvisoryBeingReplaced = NumberOfAdvisoryBeingReplaced;
    }

    public String getObservedOrExpectedSpaceWeatherPhenomena() {
        return ObservedOrExpectedSpaceWeatherPhenomena;
    }

    public void setObservedOrExpectedSpaceWeatherPhenomena(String ObservedOrExpectedSpaceWeatherPhenomena) {
        this.ObservedOrExpectedSpaceWeatherPhenomena = ObservedOrExpectedSpaceWeatherPhenomena;
    }

    public String getForecastOfThePhenomena6() {
        return ForecastOfThePhenomena6;
    }

    public void setForecastOfThePhenomena6(String ForecastOfThePhenomena6) {
        this.ForecastOfThePhenomena6 = ForecastOfThePhenomena6;
    }

    public String getForecastOfThePhenomena12() {
        return ForecastOfThePhenomena12;
    }

    public void setForecastOfThePhenomena12(String ForecastOfThePhenomena12) {
        this.ForecastOfThePhenomena12 = ForecastOfThePhenomena12;
    }

    public String getForecastOfThePhenomena18() {
        return ForecastOfThePhenomena18;
    }

    public void setForecastOfThePhenomena18(String ForecastOfThePhenomena18) {
        this.ForecastOfThePhenomena18 = ForecastOfThePhenomena18;
    }

    public String getForecastOfThePhenomena24() {
        return ForecastOfThePhenomena24;
    }

    public void setForecastOfThePhenomena24(String ForecastOfThePhenomena24) {
        this.ForecastOfThePhenomena24 = ForecastOfThePhenomena24;
    }

    public String getRemarks() {
        return Remarks;
    }

    public void setRemarks(String Remarks) {
        this.Remarks = Remarks;
    }

    public String getNextAdvisory() {
        return NextAdvisory;
    }

    public void setNextAdvisory(String NextAdvisory) {
        this.NextAdvisory = NextAdvisory;
    }

    //</editor-fold>
    @Override
    public String getTypeofReport() {
        return "SWX ADVISORY";
    }

    @Override
    public String toString() {
        AFTNMessage message = new AFTNMessage("");
        message.append(this.getTypeofReport()).append("\n", true);
        message.append("DTG: ");
        message.append(this.getTimeOfOrigin()).append("\n", true);
        message.append("SWXC: ");
        message.append(this.getNameOfSWXC()).append("\n", true);
        message.append("ADVISORY NR: ");
        message.append(this.getAdvisoryNumber()).append("\n", true);
        message.append("SWX EFFECT: ");
        message.append(this.getSpaceWeatherEffectIntensity()).append("\n", true);
        message.append("NR RPLC: ");
        message.append(this.getNumberOfAdvisoryBeingReplaced()).append("\n", true);
        message.append("OBS SWX: ");
        message.append(this.getObservedOrExpectedSpaceWeatherPhenomena()).append("\n", true);
        message.append("FCST SWX +6 HR: ");
        message.append(this.getForecastOfThePhenomena6()).append("\n", true);
        message.append("FCST SWX +12 HR: ");
        message.append(this.getForecastOfThePhenomena12()).append("\n", true);
        message.append("FCST SWX +18 HR: ");
        message.append(this.getForecastOfThePhenomena18()).append("\n", true);
        message.append("FCST SWX +24 HR: ");
        message.append(this.getForecastOfThePhenomena24()).append("\n", true);
        message.append("RMK: ");
        message.append(this.getRemarks()).append("\n", true);
        message.append("NXT ADVISORY: ");
        message.append(this.getNextAdvisory()).append("\n", true);
        return message.flush().replace("\r", "").replace("\n\n", "\n").trim();
    }
}

