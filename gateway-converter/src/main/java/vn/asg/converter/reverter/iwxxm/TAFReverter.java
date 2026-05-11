/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.AerodromeAirTemperatureForecastPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeAirTemperatureForecastType;
import _int.icao.iwxxm._2023_1.AerodromeCloudForecastPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeCloudForecastType;
import _int.icao.iwxxm._2023_1.AerodromeForecastChangeIndicatorType;
import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindForecastPropertyType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeForecastPropertyType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeForecastType;
import _int.icao.iwxxm._2023_1.TAFType;
import vn.asg.converter.reverter.entity.TAF;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.xml.bind.JAXBException;
import net.opengis.gml.v_3_2_1.TimePeriodPropertyType;
import net.opengis.gml.v_3_2_1.TimePeriodType;
import net.opengis.gml.v_3_2_1.TimePositionType;
import vn.asg.converter.utils.Builder;

/**
 *
 * @author ThanhNk
 */
public class TAFReverter extends Reverter<TAFType, TAF> {

    protected SimpleDateFormat timePeriodFormat = new SimpleDateFormat("ddHH");
    protected SimpleDateFormat fmFormat = new SimpleDateFormat("'FM'ddHHmm");
    protected SimpleDateFormat tacDateFormat = new SimpleDateFormat("ddHHmm'Z'");//dd/MM/yyyy         

    @Override
    public TAF convert(String content) throws IWXXMParsingException {
        TAFType taf = null;
        try {
            taf = revert(content, TAFType.class);
        } catch (JAXBException ex) {
            throw new IWXXMParsingException("Không đọc được định dạng điện văn.", ex);
        }

        return convert(taf);
    }

    @Override
    public TAF convert(TAFType taf) throws IWXXMParsingException {

        if (taf == null) {
            return null;
        }

        TAF tafEnt = new TAF();

        // Report status
        // this.status = Common.convertReportStatus(taf.getReportStatus());
        tafEnt.setStatus(Common.convertReportStatus(taf.getReportStatus()));

        // Location indicator
        // this.locationIndicator = getStrAirport(taf.getAerodrome());
        tafEnt.setLocationIndicator(common.getStrAirport(taf.getAerodrome()));

        // IssueTime
        Date issiuedDate = common.getDate(taf.getIssueTime());
        // this.issuedDate = this.tacDateFormat.format(issiuedDate);
        tafEnt.setIssuedDate(tacDateFormat.format(issiuedDate));

        // Cancel
        // this.cancel = taf.isSetCancelledReportValidPeriod() && taf.isIsCancelReport();
        tafEnt.setCancel(taf.isSetCancelledReportValidPeriod() && taf.isIsCancelReport());
        if (taf.isSetCancelledReportValidPeriod()) {
            // this.validPeriod = this.getTimePeriod(taf.getCancelledReportValidPeriod());
            tafEnt.setValidPeriod(this.getTimePeriod(taf.getCancelledReportValidPeriod()));
            return tafEnt;
        }

        // Normal Report
        // validPeriod
        // this.validPeriod = this.getTimePeriod(taf.getValidPeriod());
        tafEnt.setValidPeriod(this.getTimePeriod(taf.getValidPeriod()));

        if (taf.isSetBaseForecast()) {

            // Base forecast
            MeteorologicalAerodromeForecastPropertyType baseForecastPropertyType = taf.getBaseForecast();
            if (baseForecastPropertyType.isSetNilReason()) {
                // this.nil = true;
                tafEnt.setNil(true);
                return tafEnt;
            }
            parseBaseForecast(baseForecastPropertyType, tafEnt);
        }

        tafEnt.setChangeForecast(parseChangeForecast(taf.getChangeForecast()));
        return tafEnt;
    }

    @Override
    public String convertToString(String content) throws IWXXMParsingException {
        TAF taf = convert(content);
        return taf == null ? null : taf.toString();
    }
    private void parseBaseForecast(MeteorologicalAerodromeForecastPropertyType baseForecastPropertyType, TAF tafEnt) {

        if (baseForecastPropertyType == null || !baseForecastPropertyType.isSetMeteorologicalAerodromeForecast()) {
            return;
        }

        // TODO: Checking nil reason
        MeteorologicalAerodromeForecastType forecastType = baseForecastPropertyType.getMeteorologicalAerodromeForecast();

        // SurfaceWind
        if (forecastType.isSetSurfaceWind()) {
            AerodromeSurfaceWindForecastPropertyType surfaceWindForecast = forecastType.getSurfaceWind();
            // this.surfaceWind = super.getSurfaceWind(surfaceWindForecast.getAerodromeSurfaceWindForecast());
            tafEnt.setSurfaceWind(common.getSurfaceWind(surfaceWindForecast.getAerodromeSurfaceWindForecast()));
        }

        // Visibility
        if (forecastType.isSetPrevailingVisibility()) {
            // this.visibility = super.getPrevailVisibility(forecastType.getPrevailingVisibility());
            tafEnt.setVisibility(common.getPrevailVisibility(forecastType.getPrevailingVisibility()));
        }

        // Weather
        if (forecastType.isSetWeather()) {
            // this.weather = getWeathers(forecastType.getWeather());
            tafEnt.setWeather(common.getWeathers(forecastType.getWeather()));
        }

        // Cloud
        if (forecastType.isSetCloud()) {
            // this.cloud = this.getCloud(forecastType.getCloud());
            tafEnt.setCloud(this.getCloud(forecastType.getCloud()));
        }

        // Temperature
        // this.temperature = getTemperature(forecastType.getTemperature());
        tafEnt.setTemperature(getTemperature(forecastType.getTemperature()));

    }

    private String parseChangeForecast(List<MeteorologicalAerodromeForecastPropertyType> forecastPropertyTypeList) {
        if (forecastPropertyTypeList == null || forecastPropertyTypeList.isEmpty()) {
            return null;
        }

        Builder builder = new Builder();
        for (MeteorologicalAerodromeForecastPropertyType forecastProperty : forecastPropertyTypeList) {

            if (!forecastProperty.isSetMeteorologicalAerodromeForecast()) {
                continue;
            }

            MeteorologicalAerodromeForecastType forecast = forecastProperty.getMeteorologicalAerodromeForecast();

            // Indicator
            // builder.append(Common.getChangeIndicatorType(forecast.getChangeIndicator()));
            // Indicator + Time
            builder.append(getTimePeriod(forecast));

            // Surface Wind
            if (forecast.isSetSurfaceWind()) {
                AerodromeSurfaceWindForecastPropertyType surfaceWindForecast = forecast.getSurfaceWind();
                builder.append(common.getSurfaceWind(surfaceWindForecast.getAerodromeSurfaceWindForecast()));
            }

            // Visibility
            if (forecast.isSetPrevailingVisibility()) {
                builder.append(common.getPrevailVisibility(forecast.getPrevailingVisibility()));
            }

            // Weather
            if (forecast.isSetWeather()) {
                builder.append(common.getWeathers(forecast.getWeather()));
            }

            // Cloud
            if (forecast.isSetCloud()) {
                builder.append(this.getCloud(forecast.getCloud()));
            }

            // Temperature
            builder.append(getTemperature(forecast.getTemperature()));
        }

        // this.changeForecast = builder.toString();
        return builder.toString();
    }

    private String getCloud(AerodromeCloudForecastPropertyType cloudForecastType) {

        if (cloudForecastType == null || !cloudForecastType.isSetAerodromeCloudForecast()) {
            return "";
        }

        AerodromeCloudForecastType cloudForecast = cloudForecastType.getAerodromeCloudForecast();
        if (!cloudForecast.isSetLayer()) {
            return "";
        }

        return common.getCloud(cloudForecast.getLayer());
    }

    private String getTimePeriod(TimePeriodPropertyType timePeriodType) {
        if (timePeriodType == null || !timePeriodType.isSetTimePeriod()) {
            return "";
        }

        TimePeriodType timePeriod = timePeriodType.getTimePeriod();
        if (!timePeriod.isSetBeginPosition() || !timePeriod.isSetEndPosition()) {
            return "";
        }

        TimePositionType timePosition = timePeriod.getBeginPosition();
        if (!timePosition.isSetValue()) {
            return "";
        }

        Date date = null;
        String begin = null;
        String end = null;

        try {

            date = common.parse(timePosition.getValue().get(0));
            begin = timePeriodFormat.format(date);

        } catch (ParseException ex) {
            // Logger.getLogger(TAF.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }

        timePosition = timePeriod.getEndPosition();
        if (!timePosition.isSetValue()) {
            return "";
        }

        try {

            date = common.parse(timePosition.getValue().get(0));
            end = timePeriodFormat.format(date);

        } catch (ParseException ex) {
            // Logger.getLogger(TAF.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }

        return String.format("%s/%s", begin, end);

    }

    private String getTemperature(List<AerodromeAirTemperatureForecastPropertyType> list) {

        Builder builder = new Builder();
        for (AerodromeAirTemperatureForecastPropertyType item : list) {
            String maxV = "";
            String maxT = "";

            if (!item.isSetAerodromeAirTemperatureForecast()) {
                continue;
            }

            AerodromeAirTemperatureForecastType airTemperatureForecastType = item.getAerodromeAirTemperatureForecast();
            if (airTemperatureForecastType.isSetMaximumAirTemperature() && airTemperatureForecastType.isSetMaximumAirTemperatureTime()) {
                int temperature = (int) airTemperatureForecastType.getMaximumAirTemperature().getValue();
                Date temperatureTime = common.getDate(airTemperatureForecastType.getMaximumAirTemperatureTime());
                builder.append("TX%02d/%s'Z'", temperature, timePeriodFormat.format(temperatureTime));
            }

            if (airTemperatureForecastType.isSetMinimumAirTemperature() && airTemperatureForecastType.isSetMinimumAirTemperatureTime()) {
                int temperature = (int) airTemperatureForecastType.getMinimumAirTemperature().getValue();
                Date temperatureTime = common.getDate(airTemperatureForecastType.getMinimumAirTemperatureTime());
                builder.append("TN%02d/%s'Z'", temperature, timePeriodFormat.format(temperatureTime));
            }
        }

        return builder.toString();
    }

    private String getTimePeriod(MeteorologicalAerodromeForecastType forecastType) {

        if (forecastType == null || !forecastType.isSetPhenomenonTime()) {
            return "";
        }

        TimePeriodPropertyType timeObjectPropertyType = forecastType.getPhenomenonTime();

        AerodromeForecastChangeIndicatorType timeIndicator = forecastType.getChangeIndicator();

        switch (timeIndicator) {
            case PROBABILITY_30:
                return String.format("PROB30 %s", getTimePeriod(timeObjectPropertyType));
            case PROBABILITY_30_TEMPORARY_FLUCTUATIONS:
                return String.format("PROB30 TEMPO %s", getTimePeriod(timeObjectPropertyType));
            case PROBABILITY_40:
                return String.format("PROB30 %s", getTimePeriod(timeObjectPropertyType));
            case PROBABILITY_40_TEMPORARY_FLUCTUATIONS:
                return String.format("PROB40 TEMPO %s", getTimePeriod(timeObjectPropertyType));
            case TEMPORARY_FLUCTUATIONS:
                return String.format("TEMPO %s", getTimePeriod(timeObjectPropertyType));
            case BECOMING:
                return String.format("BECMG %s", getTimePeriod(timeObjectPropertyType));

            case FROM:
                if (!timeObjectPropertyType.isSetTimePeriod()) {
                    return "";
                }

                TimePeriodType timePeriod = timeObjectPropertyType.getTimePeriod();
                if (!timePeriod.isSetBeginPosition()) {
                    return "";
                }

                TimePositionType timePosition = timePeriod.getBeginPosition();

                if (!timePosition.isSetValue()) {
                    return "";
                }

                try {

                    Date date = common.parse(timePosition.getValue().get(0));
                    return fmFormat.format(date);

                } catch (ParseException ex) {
                    return "";
                }

            default:
                return "";
        }
    }
}


