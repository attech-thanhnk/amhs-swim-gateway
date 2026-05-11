/*
 */
package vn.asg.converter.reverter.entity;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import vn.asg.converter.utils.CustomDateFormat;

/**
 *
 * @author ThanhNk
 */
public class TAF extends EntityBase implements Serializable {

    private static final long serialVersionUID = 1L;

    protected SimpleDateFormat timePeriodFormat = new SimpleDateFormat("ddHH");
    protected SimpleDateFormat fmFormat = new SimpleDateFormat("'FM'ddHHmm");
    protected SimpleDateFormat normalPeriodTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    protected SimpleDateFormat cancelPeriodTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private DateFormat dateFormater = new CustomDateFormat();

        
    private String status;
    private String validPeriod;
    private String surfaceWind;
    private String visibility;
    private String weather;
    private String locationIndicator;
    private String cloud;
    private String temperature;
    private String issuedDate;
    private String changeForecast;
    private boolean cavok = false;
    private boolean nil = false;
    private boolean cancel = false;

    @Deprecated
    private String cancelledTAFID = "";
    @Deprecated
    private String DateofObservation = "";
    @Deprecated
    private String ValidPeriod = "";
    @Deprecated
    private String HeardLine = "";
    @Deprecated
    private String PresentWeather = "";
    @Deprecated
    private String RAWW = "";
    @Deprecated
    private String TimeFrom = "";
    @Deprecated
    private String Wind = "";
    @Deprecated
    private String TimeTo = "";

    public TAF() {
    }

    @Override
    public String getTypeofReport() {
        return "TAF";
    }

    //<editor-fold defaultstate="collapsed" desc=" Class properties ">
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocationIndicator() {
        return locationIndicator;
    }

    public void setLocationIndicator(String Airport) {
        this.locationIndicator = Airport;
    }

    public String getCloud() {
        return cloud;
    }

    public void setCloud(String Cloud) {
        this.cloud = Cloud;
    }

    public String getIssuedDate() {
        return issuedDate;
    }

    public void setIssuedDate(String CreateDate) {
        this.issuedDate = CreateDate;
    }

    @Deprecated
    public String getDateofObservation() {
        return DateofObservation;
    }

    @Deprecated
    public void setDateofObservation(String DateofObservation) {
        this.DateofObservation = DateofObservation;
    }

    @Deprecated
    public String getHeardLine() {
        return HeardLine;
    }

    @Deprecated
    public void setHeardLine(String HeardLine) {
        this.HeardLine = HeardLine;
    }

    @Deprecated
    public String getPresentWeather() {
        return PresentWeather;
    }

    @Deprecated
    public void setPresentWeather(String PresentWeather) {
        this.PresentWeather = PresentWeather;
    }

    @Deprecated
    public String getRAWW() {
        return RAWW;
    }

    @Deprecated
    public void setRAWW(String RAWW) {
        this.RAWW = RAWW;
    }

    @Deprecated
    public String getTimeFrom() {
        return TimeFrom;
    }

    @Deprecated
    public void setTimeFrom(String TimeFrom) {
        this.TimeFrom = TimeFrom;
    }

    public String getChangeForecast() {
        return changeForecast;
    }

    public void setChangeForecast(String changeForecast) {
        this.changeForecast = changeForecast;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String Visibility) {
        this.visibility = Visibility;
    }

    @Deprecated
    public String getWind() {
        return Wind;
    }

    @Deprecated
    public void setWind(String Wind) {
        this.Wind = Wind;
    }

    @Deprecated
    public String getTimeTo() {
        return TimeTo;
    }

    @Deprecated
    public void setTimeTo(String TimeTo) {
        this.TimeTo = TimeTo;
    }

    public boolean isCavok() {
        return cavok;
    }

    public void setCavok(boolean cavok) {
        this.cavok = cavok;
    }

//    public String getDayAndPeriod() {
//        return validPeriod;
//    }
//
//    public void setDayAndPeriod(String DayAndPeriod) {
//        this.validPeriod = DayAndPeriod;
//    }
    @Deprecated
    public String getIdentificationOfAancelled() {
        return cancelledTAFID;
    }

    @Deprecated
    public void setIdentificationOfAancelled(String IdentificationOfAancelled) {
        this.cancelledTAFID = IdentificationOfAancelled;
    }

    public String getSurfaceWind() {
        return surfaceWind;
    }

    public void setSurfaceWind(String SurfaceWind) {
        this.surfaceWind = SurfaceWind;
    }

    public String getWeather() {
        return weather;
    }

    public void setWeather(String Weather) {
        this.weather = Weather;
    }

    public String getTemperature() {
        return temperature;
    }

    public void setTemperature(String Temperature) {
        this.temperature = Temperature;
    }

    public String getValidPeriod() {
        return validPeriod;
    }

    public void setValidPeriod(String validPeriod) {
        this.validPeriod = validPeriod;
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
     * @return the cancel
     */
    public boolean isCancel() {
        return cancel;
    }

    /**
     * @param cancel the cancel to set
     */
    public void setCancel(boolean cancel) {
        this.cancel = cancel;
    }

    //</editor-fold>
    
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
        AFTNMessage message = new AFTNMessage("=");
        message.append(this.getTypeofReport());
        message.append(this.status);
        message.append(this.locationIndicator);
        message.append(this.issuedDate);

        if (this.nil) {
            message.append("NIL");
            return message.flush();
        }

        message.append(this.validPeriod);
        if (this.cancel) {
            message.append("CNL");
            return message.flush();
        }

        // message.append(this.getDayAndPeriod());
        // message.append(this.getIdentificationOfAancelled());
        // Base forecast
        message.append(this.surfaceWind);
        message.append(this.visibility);
        message.append(this.weather);
        message.append(this.cloud);
        message.append(this.temperature);

        // Change forecast
        message.append(this.changeForecast);

        // Build message
        return message.flush().trim();
    }
}


