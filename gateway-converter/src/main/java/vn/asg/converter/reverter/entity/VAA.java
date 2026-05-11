/*
 */
package vn.asg.converter.reverter.entity;

import java.io.Serializable;

/**
 *
 * @author ThanhNk
 */
public class VAA extends EntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    String strTypeOfReport = "";
    String strTimeOrigin = "";
    String strNameOfTCAC = "";
    String strNameOfVolcano = "";
    String strLocationOfVolcan = "";
    String strStateOrRegion = "";
    String strSummitElevation = "";
    String strAdvisoryNumber = "";
    String strInformationSource = "";
    String strColourCode = "";
    String strEruptionDetails = "";
    String strTimeOfObservation = "";
    String strObservedOrEstimated = "";
    String strForecastHeightAndPosition6 = "";
    String strForecastHeightAndPosition12 = "";
    String strForecastHeightAndPosition18 = "";
    String strRemark = "";
    String strNextAdvisory = "";

    public VAA() {
    }

    //<editor-fold defaultstate="collapsed" desc=" Class properties ">
    public String getStrTypeOfReport() {
        return strTypeOfReport;
    }

    public void setStrTypeOfReport(String strTypeOfReport) {
        this.strTypeOfReport = strTypeOfReport;
    }

    public String getStrTimeOrigin() {
        return strTimeOrigin;
    }

    public void setStrTimeOrigin(String strTimeOrigin) {
        this.strTimeOrigin = strTimeOrigin;
    }

    public String getStrNameOfTCAC() {
        return strNameOfTCAC;
    }

    public void setStrNameOfTCAC(String strNameOfTCAC) {
        this.strNameOfTCAC = strNameOfTCAC;
    }

    public String getStrNameOfVolcano() {
        return strNameOfVolcano;
    }

    public void setStrNameOfVolcano(String strNameOfVolcano) {
        this.strNameOfVolcano = strNameOfVolcano;
    }

    public String getStrLocationOfVolcan() {
        return strLocationOfVolcan;
    }

    public void setStrLocationOfVolcan(String strLocationOfVolcan) {
        this.strLocationOfVolcan = strLocationOfVolcan;
    }

    public String getStrStateOrRegion() {
        return strStateOrRegion;
    }

    public void setStrStateOrRegion(String strStateOrRegion) {
        this.strStateOrRegion = strStateOrRegion;
    }

    public String getStrSummitElevation() {
        return strSummitElevation;
    }

    public void setStrSummitElevation(String strSummitElevation) {
        this.strSummitElevation = strSummitElevation;
    }

    public String getStrAdvisoryNumber() {
        return strAdvisoryNumber;
    }

    public void setStrAdvisoryNumber(String strAdvisoryNumber) {
        this.strAdvisoryNumber = strAdvisoryNumber;
    }

    public String getStrInformationSource() {
        return strInformationSource;
    }

    public void setStrInformationSource(String strInformationSource) {
        this.strInformationSource = strInformationSource;
    }

    public String getStrColourCode() {
        return strColourCode;
    }

    public void setStrColourCode(String strColourCode) {
        this.strColourCode = strColourCode;
    }

    public String getStrEruptionDetails() {
        return strEruptionDetails;
    }

    public void setStrEruptionDetails(String strEruptionDetails) {
        this.strEruptionDetails = strEruptionDetails;
    }

    public String getStrTimeOfObservation() {
        return strTimeOfObservation;
    }

    public void setStrTimeOfObservation(String strTimeOfObservation) {
        this.strTimeOfObservation = strTimeOfObservation;
    }

    public String getStrObservedOrEstimated() {
        return strObservedOrEstimated;
    }

    public void setStrObservedOrEstimated(String strObservedOrEstimated) {
        this.strObservedOrEstimated = strObservedOrEstimated;
    }

    public String getStrForecastHeightAndPosition6() {
        return strForecastHeightAndPosition6;
    }

    public void setStrForecastHeightAndPosition6(String strForecastHeightAndPosition6) {
        this.strForecastHeightAndPosition6 = strForecastHeightAndPosition6;
    }

    public String getStrForecastHeightAndPosition12() {
        return strForecastHeightAndPosition12;
    }

    public void setStrForecastHeightAndPosition12(String strForecastHeightAndPosition12) {
        this.strForecastHeightAndPosition12 = strForecastHeightAndPosition12;
    }

    public String getStrForecastHeightAndPosition18() {
        return strForecastHeightAndPosition18;
    }

    public void setStrForecastHeightAndPosition18(String strForecastHeightAndPosition18) {
        this.strForecastHeightAndPosition18 = strForecastHeightAndPosition18;
    }

    public String getStrRemark() {
        return strRemark;
    }

    public void setStrRemark(String strRemark) {
        this.strRemark = strRemark;
    }

    public String getStrNextAdvisory() {
        return strNextAdvisory;
    }

    public void setStrNextAdvisory(String strNextAdvisory) {
        this.strNextAdvisory = strNextAdvisory;
    }

    //</editor-fold>
    @Override
    public String getTypeofReport() {
        return "VA ADVISORY";
    }

    @Override
    public String toString() {
        AFTNMessage message = new AFTNMessage("");
        // TODO: Implement 
        message.append(this.getTypeofReport()).append("\n", true);
        message.append("DTG: ");
        message.append(this.getStrTimeOrigin()).append("\n", true);
        message.append("VAAC: ");
        message.append(this.getStrNameOfTCAC()).append("\n", true);
        message.append("VOLCANO: ");
        message.append(this.getStrNameOfVolcano()).append("\n", true);
        message.append("PSN: ");
        message.append(this.getStrLocationOfVolcan()).append("\n", true); 
        message.append("AREA: ");
        message.append(this.getStrStateOrRegion()).append("\n", true);
        message.append("SUMMIT ELEV: ");
        message.append(this.getStrSummitElevation()).append("\n", true);
        message.append("ADVISORY NR: ");
        message.append(this.getStrAdvisoryNumber()).append("\n", true);
        message.append("INFO SOURCE: ");
        message.append(this.getStrInformationSource()).append("\n", true);
        message.append("AVIATION COLOUR CODE: ");
        message.append(this.getStrColourCode()).append("\n", true);
        message.append("ERUPTION DETAILS: ");
        message.append(this.getStrEruptionDetails()).append("\n", true);
        message.append("OBS VA DTG: ");
        message.append(this.getStrTimeOfObservation()).append("\n", true);
        message.append("OBS VA CLD: ");
        message.append(this.getStrObservedOrEstimated()).append("\n", true);
        message.append("FCST VA CLD +6 HR: ");
        message.append(this.getStrForecastHeightAndPosition6()).append("\n", true);
        message.append("FCST VA CLD +12 HR: ");
        message.append(this.getStrForecastHeightAndPosition12()).append("\n", true);
        message.append("FCST VA CLD +18 HR: ");
        message.append(this.getStrForecastHeightAndPosition18()).append("\n", true);
        message.append("RMK:");
        message.append(this.getStrRemark()).append("\n", true);
        message.append("NXT ADVISORY:");
        message.append(this.getStrNextAdvisory()).append("\n", true);
        return message.flush().replace("\r", "").replace("\n\n", "\n").trim();
    }
}

