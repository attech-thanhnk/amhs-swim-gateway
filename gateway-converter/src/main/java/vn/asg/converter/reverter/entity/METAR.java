/*
 */
package vn.asg.converter.reverter.entity;

import _int.icao.iwxxm._2023_1.METARType;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import javax.xml.bind.JAXBElement;

/**
 *
 * @author ThanhNk
 */
public class METAR extends EntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    private final SimpleDateFormat issuedDateFormat = new SimpleDateFormat("ddHHmm'Z'");//hhmm
    private final SimpleDateFormat trendForecastTimeFormat = new SimpleDateFormat("HHmm");//hhmm

//    private DecimalFormat format
    private String locationIndicator = "";
    private String issuedDate = "";

//    private String strHeardLine = "";
//     private String strTypeOfReport = "METAR";
//     private String strRAWW = "";
    private String status = "";
    private String observationDate = "";

    private String strTimeoftheobservation = "";
    private boolean booleanIdentificationofanautomated = false;
    private String runwayVisualRange = "";
    private String visibility = "";
    private String surfaceWind = "";
    private String presentWeather = "";
    private String cloud = "";
    private String airTemperature = "";
    private String dewpointTemperature = "";

    private String pressure = "";
//    private String wind;

    // Supplement
    private String recentWeather = "";
    private String windShear = "";
    private String seaTempAndState = "";
    private String runwayState;

    // Trend
    private String trend = "";
    private boolean cavok = false;
    private boolean nil;

//    private JAXBElement<METARType> root;
    public METAR() {
    }

    @Override
    public String getTypeofReport() {
        return "METAR";
    }

    //<editor-fold defaultstate="collapsed" desc="Class properties">
    public String getLocationIndicator() {
        return locationIndicator;
    }

    public void setLocationIndicator(String strAirport) {
        this.locationIndicator = strAirport;
    }

    public String getIssuedDate() {
        return issuedDate;
    }

    public void setIssuedDate(String strCreateDate) {
        this.issuedDate = strCreateDate;
    }

//    public String getStrHeardLine() {
//        return strHeardLine;
//    }
//
//    public void setStrHeardLine(String strHeardLine) {
//        this.strHeardLine = strHeardLine;
//    }
//    public String getStrTypeOfReport() {
//        return strTypeOfReport;
//    }
//
//    public void setStrTypeOfReport(String strTypeOfReport) {
//        this.strTypeOfReport = strTypeOfReport;
//    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getObservationDate() {
        return observationDate;
    }

    public void setObservationDate(String observationDate) {
        this.observationDate = observationDate;
    }

//    public String getStrRAWW() {
//        return strRAWW;
//    }
//
//    public void setStrRAWW(String strRAWW) {
//        this.strRAWW = strRAWW;
//    }
    public String getStrTimeoftheobservation() {
        return strTimeoftheobservation;
    }

    public void setStrTimeoftheobservation(String strTimeoftheobservation) {
        this.strTimeoftheobservation = strTimeoftheobservation;
    }

    public boolean isBooleanIdentificationofanautomated() {
        return booleanIdentificationofanautomated;
    }

    public void setBooleanIdentificationofanautomated(boolean booleanIdentificationofanautomated) {
        this.booleanIdentificationofanautomated = booleanIdentificationofanautomated;
    }

    public String getRunwayVisualRange() {
        return runwayVisualRange;
    }

    public void setRunwayVisualRange(String strRunwayvisualrange) {
        this.runwayVisualRange = strRunwayvisualrange;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String strVisibility) {
        this.visibility = strVisibility;
    }

    public String getPresentweather() {
        return presentWeather;
    }

    public void setPresentweather(String strPresentweather) {
        this.presentWeather = strPresentweather;
    }

    public String getCloud() {
        return cloud;
    }

    public void setCloud(String strCloud) {
        this.cloud = strCloud;
    }

    public String getAirTemperature() {
        return airTemperature;
    }

    public void setAirTemperature(String strAirTemperature) {
        this.airTemperature = strAirTemperature;
    }

    public String getDewpointTemperature() {
        return dewpointTemperature;
    }

    public void setDewpointTemperature(String strDewpointTemperature) {
        this.dewpointTemperature = strDewpointTemperature;
    }

    public boolean isCavok() {
        return cavok;
    }

    public void setCavok(boolean booleanckbCavok) {
        this.cavok = booleanckbCavok;
    }

    public String getPressure() {
        return pressure;
    }

    public void setPressure(String strQNH) {
        this.pressure = strQNH;
    }

//    public String getWind() {
//        return wind;
//    }
//
//    public void setWind(String strWind) {
//        this.wind = strWind;
//    }
    public String getWindShear() {
        return windShear;
    }

    public void setWindShear(String strWindShear) {
        this.windShear = strWindShear;
    }

    public String getTrend() {
        return trend;
    }

    public void setTrend(String strTrend) {
        this.trend = strTrend;
    }

    public String getSurfaceWind() {
        return surfaceWind;
    }

    public void setSurfaceWind(String strSurfaceWind) {
        this.surfaceWind = strSurfaceWind;
    }

    /**
     * @return the nil
     */
    public boolean isNil() {
        return nil;
    }

    /**
     * @param nil the nil to set
     */
    public void setNil(boolean nil) {
        this.nil = nil;
    }

    /**
     * @return the recentWeather
     */
    public String getRecentWeather() {
        return recentWeather;
    }

    /**
     * @param recentWeather the recentWeather to set
     */
    public void setRecentWeather(String recentWeather) {
        this.recentWeather = recentWeather;
    }

    /**
     * @return the seaTempAndState
     */
    public String getSeaTempAndState() {
        return seaTempAndState;
    }

    /**
     * @param seaTempAndState the seaTempAndState to set
     */
    public void setSeaTempAndState(String seaTempAndState) {
        this.seaTempAndState = seaTempAndState;
    }

    /**
     * @return the runwayState
     */
    public String getRunwayState() {
        return runwayState;
    }

    /**
     * @param runwayState the runwayState to set
     */
    public void setRunwayState(String runwayState) {
        this.runwayState = runwayState;
    }

    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="Override">
    @Override
    public int hashCode() {
        return locationIndicator.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof METAR)) {
            return false;
        }
        METAR other = (METAR) object;

        return this.getLocationIndicator().equalsIgnoreCase(other.getLocationIndicator());
    }

    @Override
    public String toString() {
        AFTNMessage aftn = new AFTNMessage("=");
        aftn.append(this.getTypeofReport());
        aftn.append(this.locationIndicator);
        aftn.append(this.observationDate);

        if (this.nil) {
            aftn.append("NIL");
            return aftn.flush();
        }

        aftn.append(this.surfaceWind);

        if (this.cavok) {
            aftn.append("CAVOK");
        } else {
            aftn.append(this.visibility);
            aftn.append(this.runwayVisualRange);
            aftn.append(this.presentWeather);
            aftn.append(this.cloud);
        }

        if (!this.airTemperature.isEmpty()) {
            String temp = this.airTemperature;
            if (!this.dewpointTemperature.isEmpty()) {
                temp += "/" + this.dewpointTemperature;
            }
            aftn.append(temp);
        }
        aftn.append(this.pressure);

        // Supplement info
        aftn.append(this.recentWeather);
        aftn.appendElement(this.windShear);
        aftn.append(this.seaTempAndState);
        aftn.append(this.runwayState);

        // Trend
        aftn.append(this.trend);
        return aftn.flush();
//        return aftn.toString();
    }

    //</editor-fold>
}

