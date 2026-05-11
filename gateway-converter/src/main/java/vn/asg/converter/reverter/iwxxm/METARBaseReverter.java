/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.AbstractTimeObjectPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeCloudForecastPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeCloudForecastType;
import _int.icao.iwxxm._2023_1.AerodromeCloudType;
import _int.icao.iwxxm._2023_1.AerodromeForecastWeatherType;
import _int.icao.iwxxm._2023_1.AerodromeHorizontalVisibilityType;
import _int.icao.iwxxm._2023_1.AerodromeRunwayStateType;
import _int.icao.iwxxm._2023_1.AerodromeRunwayVisualRangeType;
import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindTrendForecastPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindTrendForecastType;
import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindType;
import _int.icao.iwxxm._2023_1.AerodromeWindShearType;
import _int.icao.iwxxm._2023_1.AngleWithNilReasonType;
import _int.icao.iwxxm._2023_1.CloudLayerPropertyType;
import _int.icao.iwxxm._2023_1.CloudLayerType;
import _int.icao.iwxxm._2023_1.DistanceWithNilReasonType;
import _int.icao.iwxxm._2023_1.MeasureWithNilReasonType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationPropertyType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationReportType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationType.Cloud;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationType.RunwayState;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationType.Rvr;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationType.SeaCondition;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationType.SurfaceWind;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationType.Visibility;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeTrendForecastPropertyType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeTrendForecastType;
import _int.icao.iwxxm._2023_1.RunwayContaminationType;
import _int.icao.iwxxm._2023_1.RunwayDepositsType;
import _int.icao.iwxxm._2023_1.RunwayDirectionPropertyType;
import _int.icao.iwxxm._2023_1.RunwayFrictionCoefficientType;
import _int.icao.iwxxm._2023_1.TrendForecastTimeIndicatorType;
import _int.icao.iwxxm._2023_1.VelocityWithNilReasonType;
import aero.aixm.schema._5_1.RunwayDirectionTimeSlicePropertyType;
import aero.aixm.schema._5_1.RunwayDirectionTimeSliceType;
import aero.aixm.schema._5_1.RunwayDirectionType;
import aero.aixm.schema._5_1.TextDesignatorType;
import vn.asg.converter.reverter.entity.Keys;
import vn.asg.converter.reverter.entity.METAR;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.xml.bind.JAXBElement;
import net.opengis.gml.v_3_2_1.AbstractTimeObjectType;
import net.opengis.gml.v_3_2_1.LengthType;
import net.opengis.gml.v_3_2_1.MeasureType;
import net.opengis.gml.v_3_2_1.ReferenceType;
import net.opengis.gml.v_3_2_1.TimeInstantPropertyType;
import net.opengis.gml.v_3_2_1.TimeInstantType;
import net.opengis.gml.v_3_2_1.TimePeriodType;
import net.opengis.gml.v_3_2_1.TimePositionType;
import vn.asg.converter.utils.Builder;
import vn.asg.converter.utils.CustomDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ThanhNk
 * @param <TType>
 * @param <TEntity>
 */
public abstract class METARBaseReverter<TType extends MeteorologicalAerodromeObservationReportType, TEntity extends METAR> extends Reverter<TType, TEntity> {

    private static final Logger log = LoggerFactory.getLogger(METARBaseReverter.class);

    private final Common common = new Common();
    private final SimpleDateFormat issuedDateFormat = new SimpleDateFormat("ddHHmm'Z'");//hhmm
    protected DateFormat dateFormater = new CustomDateFormat();
    private final SimpleDateFormat trendForecastTimeFormat = new SimpleDateFormat("HHmm");//hhmm

    protected void parse(MeteorologicalAerodromeObservationReportType objectType, TEntity metar) throws IWXXMParsingException {

        if (objectType == null) {
            return;
        }

        //REPORT TYPE 
        //COR 
        metar.setStatus(common.getCOR(objectType.getReportStatus()));
        metar.setLocationIndicator(common.getStrAirport(objectType.getAerodrome()));

        //IssueTime
        metar.setIssuedDate(common.getIssuedTime(objectType.getIssueTime()));
//        metar.setStrRAWW(getRAWWs(objectType.getDescription()));

        // Observation Time
        String date = getDateofobservation(objectType.getObservationTime());
        metar.setObservationDate(date);
//        metar.setStrTimeoftheobservation(date);

        // OBSERVATION SECTION
        MeteorologicalAerodromeObservationPropertyType meteoLogicalAerodromeObservationType = common.getObject(objectType.getObservation());
        
        

        if (meteoLogicalAerodromeObservationType != null) {
            
            if (meteoLogicalAerodromeObservationType.isSetNilReason()) {
                metar.setNil(true);
                return;
            }
            
            if (meteoLogicalAerodromeObservationType.isSetMeteorologicalAerodromeObservation()) {
                
                
                MeteorologicalAerodromeObservationType aerodromObservationType = meteoLogicalAerodromeObservationType.getMeteorologicalAerodromeObservation();

                // Observation
                metar.setSurfaceWind(this.getSurfaceWind(aerodromObservationType.getSurfaceWind()));

                //Visibility
                metar.setVisibility(getVisibility(common.getObject(aerodromObservationType.getVisibility())));

                //strRunWay 
                metar.setRunwayVisualRange(this.getRunway(aerodromObservationType.getRvr()));

                //Present weather
                metar.setPresentweather(common.getWeathers(aerodromObservationType.getPresentWeather()));

                // could
                MeteorologicalAerodromeObservationType.Cloud cloud = common.getObject(aerodromObservationType.getCloud());
                metar.setCloud(this.getClould(cloud));

                // Set temperature
                metar.setAirTemperature(this.getAirTemperature(aerodromObservationType));

                //Cavok
                metar.setCavok(aerodromObservationType.isCloudAndVisibilityOK());

                //QNL
                // this.strQNH = getQNH(aerodromObservationType.getQnh());
                metar.setPressure(this.getPressure(aerodromObservationType.getQnh()));

                // SUPPLEMENT INFO
                // Recent Weather
                if (aerodromObservationType.isSetRecentWeather()) {
                    metar.setRecentWeather(this.getRecentWeathers(aerodromObservationType.getRecentWeather()));
                }
                
                // Wind Shear
                metar.setWindShear(this.getWindShear(common.getObject(aerodromObservationType.getWindShear())));
                
                // Sea-surface temperature and state of the sea
                metar.setSeaTempAndState(this.getSeaCondition(common.getObject(aerodromObservationType.getSeaCondition())));
                
                
                // State of the runway
                if (aerodromObservationType.isSetRunwayState()) {
                    metar.setRunwayState(this.getRunwayState(aerodromObservationType.getRunwayState()));
                }
            }
        }

        // TREND
        metar.setTrend(this.getTrend(objectType.getTrendForecast()));
    }

    private String getTrend(List<MeteorologicalAerodromeTrendForecastPropertyType> list) {
        StringBuilder builder = new StringBuilder();
        for (MeteorologicalAerodromeTrendForecastPropertyType item : list) {
            if (item.getNilReason() != null && item.getNilReason().size() > 0) {
                String nilReason = item.getNilReason().get(0);
                builder.append(" ").append(Keys.getInstance().get(nilReason));
                continue;
            }

            builder.append(getTrend(item));
        }
        return builder.toString();
    }

    private String getTrend(MeteorologicalAerodromeTrendForecastPropertyType item) {

        if (item == null || !item.isSetMeteorologicalAerodromeTrendForecast()) {
            return "";
        }

        MeteorologicalAerodromeTrendForecastType trendForcastType = item.getMeteorologicalAerodromeTrendForecast();

        if (!trendForcastType.isSetChangeIndicator()) {
            return "";
        }

        Builder builder = new Builder();
        builder.append(common.getBeginTrend(trendForcastType.getChangeIndicator().name()));
        builder.append(this.getTimePeriod(trendForcastType));
        builder.append(this.getSurfaceWind(trendForcastType.getSurfaceWind()));
        if (trendForcastType.isCloudAndVisibilityOK()) {
            builder.append("CAVOK");
        }
        
        builder.append(this.getPervalilingVisibi(trendForcastType.getPrevailingVisibility()));
        builder.append(this.getPrevailingWeather(trendForcastType.getWeather()));
        builder.append(this.getCloud(common.getObject(trendForcastType.getCloud())));
        return builder.toString();
    }

    private String getPrevailingWeather(List<AerodromeForecastWeatherType> list) {

        if (list == null || list.isEmpty()) {
            return "";
        }

        Builder builder = new Builder();
        for (AerodromeForecastWeatherType item : list) {
            if (item.isSetNilReason()) {
                builder.append(Keys.getInstance().get(item.getNilReason().get(0)));
                continue;
            }

            builder.append(common.split(item.getHref()));
        }
        return builder.toString();
    }

    private String getPervalilingVisibi(LengthType object) {

        if (object == null || !object.isSetValue()) {
            return "";
        }

        double value = object.getValue();
        if (value >= 10000) {
            value = 9999;
        }

        if (value <= 50) {
            value = 0;
        }

        return String.format("%04d", (int) value);
    }

    private String getSurfaceWind(AerodromeSurfaceWindTrendForecastPropertyType object) {

        if (object == null || !object.isSetAerodromeSurfaceWindTrendForecast()) {
            return "";
        }

        AerodromeSurfaceWindTrendForecastType surfaceWindTrendForecase = common.getObject(object.getAerodromeSurfaceWindTrendForecast());
        if (surfaceWindTrendForecase == null) {
            return "";
        }

        return common.getSurfaceWind(surfaceWindTrendForecase);
    }

    private String getTimePeriod(MeteorologicalAerodromeTrendForecastType meteoLogicalAerodromeTrendForcastPropertyType) {

        if (meteoLogicalAerodromeTrendForcastPropertyType == null || !meteoLogicalAerodromeTrendForcastPropertyType.isSetPhenomenonTime()) {
            return "";
        }

        AbstractTimeObjectPropertyType timeObjectPropertyType = meteoLogicalAerodromeTrendForcastPropertyType.getPhenomenonTime();
        if (!timeObjectPropertyType.isSetAbstractTimeObject()) {
            return "";
        }

        JAXBElement<? extends AbstractTimeObjectType> jaxbElement = timeObjectPropertyType.getAbstractTimeObject();
        TimePeriodType timeType = (TimePeriodType) jaxbElement.getValue();
        if (timeType == null) {
            return "";
        }

        TrendForecastTimeIndicatorType timeIndicator = meteoLogicalAerodromeTrendForcastPropertyType.getTimeIndicator();
        
        if (timeIndicator == null) {
            return "";
        }
        
        switch (timeIndicator) {
            case AT:
                return String.format("AT%s", trendForecastTimeFormat.format(common.parseDatetime(timeType.getBeginPosition())));
            case FROM:
                return String.format("FM%s", trendForecastTimeFormat.format(common.parseDatetime(timeType.getBeginPosition())));
            case UNTIL:
                return String.format("TL%s", trendForecastTimeFormat.format(common.parseDatetime(timeType.getEndPosition())));
            case FROM_UNTIL:
                return String.format("FM%s TL%s",
                        trendForecastTimeFormat.format(common.parseDatetime(timeType.getBeginPosition())),
                        trendForecastTimeFormat.format(common.parseDatetime(timeType.getEndPosition())));
            default:
                return "";
        }
    }

    private String getCloud(AerodromeCloudForecastPropertyType object) {

        if (object == null) {
            return "";
        }

        if (object.isSetNilReason()) {
            return cloudCode.getValue(object.getNilReason().get(0));
        }

        if (!object.isSetAerodromeCloudForecast()) {
            return "";
        }

        AerodromeCloudForecastType cloudForecastType = object.getAerodromeCloudForecast();
        if (!cloudForecastType.isSetLayer()) {
            return "";
        }

        Builder builder = new Builder();
        List<CloudLayerPropertyType> layers = cloudForecastType.getLayer();
        for (CloudLayerPropertyType layer : layers) {

            if (!layer.isSetCloudLayer()) {
                continue;
            }

            CloudLayerType cloudLayer = layer.getCloudLayer();
            if (!cloudLayer.isSetAmount() || !cloudLayer.isSetBase()) {
                continue;
            }

            String cloudType = Common.split(cloudLayer.getAmount().getHref());
            // String cloudType = cloudCode.getValue(cloudLayer.getAmount().getHref());
            int cloudHeight = (int) cloudLayer.getBase().getValue() / 100;
            builder.append("%s%03d", cloudType, cloudHeight);
        }

        if (object.isSetNilReason()) {
            String nilReason = object.getNilReason().get(0);
            builder.append("%s", cloudCode.getValue(nilReason));
        }

        return builder.toString();

    }

    private String getWindShear(MeteorologicalAerodromeObservationType.WindShear objectType) {
        if (objectType == null || !objectType.isSetAerodromeWindShear()) {
            return "";
        }

        AerodromeWindShearType winshearType = objectType.getAerodromeWindShear();

        if (winshearType.isSetAllRunways() && winshearType.isAllRunways()) {
            return "WS ALL RWY";
        }

        if (!winshearType.isSetRunway()) {
            return "";
        }
        
        Builder builder = new Builder();
        List<RunwayDirectionPropertyType> runwayDirectionPropertyList =  winshearType.getRunway();
        for (RunwayDirectionPropertyType runwayDirection : runwayDirectionPropertyList) {
            String rwName = getRunwayName(runwayDirection);
            if (!rwName.isEmpty()) {
                builder.append("WS RWY" + rwName);
            }
        }
        return builder.toString();
    }

    private String getPressure(MeasureWithNilReasonType objectType) {

        if (objectType == null || !objectType.isSetValue()) {
            return "";
        }

        return String.format("Q%04d", (int) objectType.getValue());

    }

    private String getAirTemperature(MeteorologicalAerodromeObservationType objectType) {

        if (!objectType.isSetAirTemperature() || !objectType.isSetDewpointTemperature()) {
            return "";
        }

        double airTempe = objectType.getAirTemperature().getValue();
        double dewTempe = objectType.getDewpointTemperature().getValue();
        Builder builder = new Builder("/");
        builder.append(airTempe < 0 ? "M%02d" : "%02d", (int) airTempe);
        builder.append(dewTempe < 0 ? "M%02d" : "%02d", (int) dewTempe);

        return builder.toString();
    }

    private String getDateofobservation(TimeInstantPropertyType timeInstanct) {

        if (timeInstanct == null || !timeInstanct.isSetTimeInstant()) {
            return "";
        }

        TimeInstantType timeInstanceType = timeInstanct.getTimeInstant();
        if (!timeInstanceType.isSetTimePosition()) {
            return "";
        }

        TimePositionType timePosition = timeInstanceType.getTimePosition();
        if (!timePosition.isSetValue()) {
            return "";
        }

        List<String> strList = timePosition.getValue();
        String time = strList.get(0);
        Date issuedDate;
        try {
            issuedDate = dateFormater.parse(time);
            return issuedDateFormat.format(issuedDate);
        } catch (ParseException ex) {
            log.error("Convert date time {} fail: {}", time, ex.getMessage());
            return issuedDateFormat.format(new Date());
        }

    }

    private String getRunway(List<Rvr> strRvr) {
        StringBuilder builder = new StringBuilder();
        for (MeteorologicalAerodromeObservationType.Rvr rvr : strRvr) {
            String rvrStr = getRunway(rvr);
            if (rvrStr.isEmpty()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(rvrStr);
        }
        return builder.toString();
    }

    private String getRunway(Rvr rvrs) {

        if (!rvrs.isSetAerodromeRunwayVisualRange()) {
            return "";
        }

        AerodromeRunwayVisualRangeType visualRange = rvrs.getAerodromeRunwayVisualRange();
        if (!visualRange.isSetRunway()) {
            return "";
        }

        
        String runwayName = this.getRunwayName(visualRange.getRunway());
        
        /*
        RunwayDirectionPropertyType runwayDirection = visualRange.getRunway();
        if (!runwayDirection.isSetRunwayDirection()) {
            return "";
        }

        RunwayDirectionType runwayDirectionType = runwayDirection.getRunwayDirection();
        if (!runwayDirectionType.isSetTimeSlice()) {
            return "";
        }

        RunwayDirectionTimeSlicePropertyType runwayDirectionTimeSlice = runwayDirectionType.getTimeSlice().get(0);
        if (!runwayDirectionTimeSlice.isSetRunwayDirectionTimeSlice()) {
            return "";
        }

        RunwayDirectionTimeSliceType runwayDirectionSliceType = runwayDirectionTimeSlice.getRunwayDirectionTimeSlice();
        if (!runwayDirectionSliceType.isSetDesignator()) {
            return "";
        }
        */

        StringBuilder builder = new StringBuilder();
       // builder.append("R").append(common.getObject(runwayDirectionSliceType.getDesignator()).getValue()).append("/");
        String pastTendency = common.getPastTendency(visualRange.getPastTendency());
        if (!visualRange.isSetMeanRVR()) {
            // builder.append(String.format("0000%s", pastTendency));
            return String.format("R%s/0000%s", runwayName, pastTendency);
        } else {
            // builder.append(String.format("%04d%s", (int) visualRange.getMeanRVR().getValue(), pastTendency));
            return String.format("R%s/%04d%s", runwayName, (int) visualRange.getMeanRVR().getValue(), pastTendency);
        }

//        return builder.toString();
    }

    private <T extends MeasureType> T getMeasureType(JAXBElement<T> element) {
        if (element == null) {
            return null;
        }
        return element.getValue();
    }

    private String getMeanWind(AerodromeSurfaceWindType obj) {

        if (obj == null) {
            return "";
        }

        String meanWindDirection = "";
        AngleWithNilReasonType angle = getMeasureType(obj.getMeanWindDirection());
        if (angle != null) {
            meanWindDirection = String.format("%03d", (int) angle.getValue());
        }

        String meanWindSpeed = "";
        String meanWindSpee_oum = "";
        VelocityWithNilReasonType velo = obj.getMeanWindSpeed();
        if (velo != null) {
            meanWindSpeed = String.format("%02d", (int) velo.getValue());
            meanWindSpee_oum = common.getSpeedUnlts(velo.getUom());
        }

        String windGustSpeed = "";
        velo = getMeasureType(obj.getWindGustSpeed());
        if (velo != null) {
            windGustSpeed = String.format("G%02d", (int) velo.getValue());
        }
        return String.format("%s%s%s%s", meanWindDirection, meanWindSpeed, windGustSpeed, meanWindSpee_oum);

    }

    private String getExtremeClock(AerodromeSurfaceWindType obj) {

        StringBuilder windDirection = new StringBuilder();
        AngleWithNilReasonType angle = getMeasureType(obj.getExtremeClockwiseWindDirection());
        if (angle != null) {
            windDirection.append(String.format("%03d", (int) angle.getValue()));
        }

        angle = getMeasureType(obj.getExtremeCounterClockwiseWindDirection());
        if (angle != null) {
            windDirection.append(String.format("V%03d", (int) angle.getValue()));
        }
        return windDirection.toString();

    }

    private String getSurfaceWind(SurfaceWind surfaceWind) {
        if (surfaceWind == null) {
            return "";
        }

        AerodromeSurfaceWindType aerodromwSurfaceWindType = surfaceWind.getAerodromeSurfaceWind();
        if (aerodromwSurfaceWindType == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder(getMeanWind(aerodromwSurfaceWindType));

        String strExtreme = getExtremeClock(aerodromwSurfaceWindType);
        if (!strExtreme.isEmpty()) {
            // out = out + " " + strExtreme;
            builder.append(" ").append(strExtreme);
        }

        return builder.toString();

    }

    private String getVisibility(Visibility visibility) {
        if (visibility == null) {
            return "";
        }

        AerodromeHorizontalVisibilityType aerodromeHorizontalVisibilityType = visibility.getAerodromeHorizontalVisibility();
        if (aerodromeHorizontalVisibilityType == null) {
            return "";
        }

        DistanceWithNilReasonType distanceWithNillReasonType = aerodromeHorizontalVisibilityType.getPrevailingVisibility();
        if (distanceWithNillReasonType == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int value = (int) distanceWithNillReasonType.getValue();
        if (value > 9999) {
            value = 9999;
        }
        
        builder.append(String.format("%04d", value));

        String out = "";
        distanceWithNillReasonType = common.getObject(aerodromeHorizontalVisibilityType.getMinimumVisibility());
        if (distanceWithNillReasonType == null) {
            return builder.toString();
        }
        
        
        builder.append(" ").append(String.format("%04d", (int) distanceWithNillReasonType.getValue()));

        AngleWithNilReasonType angleWithNilReason = getMeasureType(aerodromeHorizontalVisibilityType.getMinimumVisibilityDirection());
        if (angleWithNilReason == null) {
            return builder.toString();
        }

        String direction = Common.convertDirectionFromDecimalToString(angleWithNilReason.getValue());
        builder.append(direction);

        return builder.toString();
    }

    private String getClould(Cloud cloud) {

        if (cloud == null || !cloud.isSetAerodromeCloud()) {
            return "";
        }

        AerodromeCloudType cloudType = cloud.getAerodromeCloud();
        if (!cloudType.isSetLayer()) {
            return "";
        }
        
        return common.getCloud(cloudType.getLayer());
    }

    private String getRunwayState(List<RunwayState> runwayStates) {
        if (runwayStates == null || runwayStates.isEmpty()){
            return "";
        }
        
        Builder builder = new Builder();
        for (RunwayState runwayState : runwayStates) {
            if (!runwayState.isSetAerodromeRunwayState()) {
                continue;
            }
            
            AerodromeRunwayStateType runwayStateType = runwayState.getAerodromeRunwayState();
            String runwayStateCode = this.getRunwayState(runwayStateType);
            if (runwayStateCode == null) {
                continue;
            }
            builder.append(runwayStateCode);
        }
        
        return  builder.toString();
    }
    
    private String getRunwayState(AerodromeRunwayStateType runwayStateType) {
        
        if (runwayStateType == null) {
            return null;
        }
        
        boolean isApplyAllRunway = runwayStateType.isSetAllRunways() && runwayStateType.isAllRunways();
        boolean isFromPreviousReport = runwayStateType.isSetFromPreviousReport() && runwayStateType.isFromPreviousReport();

        RunwayDirectionPropertyType directionType = common.getObject(runwayStateType.getRunway());
        if (directionType == null) {
            return null;
        }

        
        Builder builder = new Builder("");
        
        if (directionType.isSetNilReason()) {
            String nilReason = directionType.getNilReason().get(0);
            if (nilReason.equalsIgnoreCase("http://codes.wmo.int/bufr4/codeflag/0-20-085/1")) {
                return "SNOCLO";
//                builder.append("SNOCLO");
//                continue;
            } else if (nilReason.equalsIgnoreCase("http://codes.wmo.int/common/nil/inapplicable")) {
                builder.append("R99");
            }
        } else {
            builder.append("R%s", getRunwayName(directionType));
        }

        boolean isClear = runwayStateType.isSetCleared() && runwayStateType.isCleared();
        if (isClear) {
            builder.append("/CLRD//");
            return builder.toString();
        }

        builder.append("/");

        RunwayDepositsType deposit = common.getObject(runwayStateType.getDepositType());
        if (deposit == null || deposit.isSetNilReason()) {
            builder.append("/");

        } else {
            builder.append(common.split(deposit.getHref()));
        }

        RunwayContaminationType contamination = common.getObject(runwayStateType.getContamination());
        if (contamination == null || contamination.isSetNilReason()) {
            builder.append("/");
        } else {
            builder.append(common.split(contamination.getHref()));
        }

        DistanceWithNilReasonType depOfDeposit = common.getObject(runwayStateType.getDepthOfDeposit());
        if (depOfDeposit == null || depOfDeposit.isSetNilReason()) {
            builder.append("//");
        } else {
            builder.append("%02d", (int) depOfDeposit.getValue());
        }

        RunwayFrictionCoefficientType friction = common.getObject(runwayStateType.getEstimatedSurfaceFrictionOrBrakingAction());
        if (friction == null || friction.isSetNilReason()) {
            builder.append("//");
        } else {
            builder.append(common.split(friction.getHref()));
        }
        return builder.toString();
    }
    
    private String getRunwayName(RunwayDirectionPropertyType runwayDirection) {
//        RunwayDirectionPropertyType runwayDirection = visualRange.getRunway();
        if (runwayDirection == null || !runwayDirection.isSetRunwayDirection()) {
            return "";
        }

        RunwayDirectionType runwayDirectionType = runwayDirection.getRunwayDirection();
        if (!runwayDirectionType.isSetTimeSlice()) {
            return "";
        }

        RunwayDirectionTimeSlicePropertyType runwayDirectionTimeSlice = runwayDirectionType.getTimeSlice().get(0);
        if (!runwayDirectionTimeSlice.isSetRunwayDirectionTimeSlice()) {
            return "";
        }

        RunwayDirectionTimeSliceType runwayDirectionSliceType = runwayDirectionTimeSlice.getRunwayDirectionTimeSlice();
        if (!runwayDirectionSliceType.isSetDesignator()) {
            return "";
        }

        TextDesignatorType textDesignatorType = common.getObject(runwayDirectionSliceType.getDesignator());
        if (textDesignatorType == null) {
            return "";
        }

        return textDesignatorType.getValue();
    }
    
    private String getSeaCondition(SeaCondition seaCondition) {
        if (seaCondition == null || !seaCondition.isSetAerodromeSeaCondition()) {
            return "";
        }
        
        _int.icao.iwxxm._2023_1.AerodromeSeaConditionType sea = seaCondition.getAerodromeSeaCondition();
        Builder builder = new Builder("/");
        
        // Temperature (W[temp])
        if (sea.isSetSeaSurfaceTemperature()) {
             double temp = sea.getSeaSurfaceTemperature().getValue();
             builder.append("W%02d", (int)Math.round(temp));
        }
        
        // State (S[state]) or Height (H[height])
        if (sea.isSetSeaState() && sea.getSeaState().getValue() != null) {
             String stateHref = sea.getSeaState().getValue().getHref();
             String state = Common.split(stateHref); 
             builder.append("S%s", state);
        } else if (sea.isSetSignificantWaveHeight() && sea.getSignificantWaveHeight().getValue() != null) {
             double height = sea.getSignificantWaveHeight().getValue().getValue(); 
             builder.append("H%03d", (int)Math.round(height * 100)); 
        }
        
        return builder.toString();
    }
    
    public <T extends ReferenceType> String getRecentWeathers(List<T> presentWeatherTypeList) {
        Builder builder = new Builder();
        for (T item : presentWeatherTypeList) {
            String presentWeatherCharacter = Common.split(item.getHref());
            if (presentWeatherCharacter.isEmpty()) {
                continue;
            }
            builder.append("RE" + presentWeatherCharacter);
        }
        return builder.toString();

    }

}


