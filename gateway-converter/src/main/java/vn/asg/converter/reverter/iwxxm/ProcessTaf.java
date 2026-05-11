/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.*;
import _int.wmo.def.collect._2014.MeteorologicalBulletinType;
import _int.wmo.def.collect._2014.MeteorologicalInformationMemberPropertyType;
import aero.aixm.schema._5_1.CodeAirportHeliportDesignatorType;
import java.io.File;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.helpers.DefaultValidationEventHandler;
import javax.xml.transform.stream.StreamSource;
import net.opengis.gml.v_3_2_1.TimePeriodType;
import vn.asg.converter.reverter.entity.TAF;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ThanhNk
 */
public class ProcessTaf extends Common {

//    private tafs taf;
    private JAXBElement<TAFType> root;
    private JAXBContext context;
    private Unmarshaller unmarshaller;
    private TAF taf;

    boolean checkBulltentin = false;
    JAXBElement<MeteorologicalBulletinType> rootBull;
    List<TAF> listTaf;
    String bulletinIdentifier = "";

    public ProcessTaf(String patchFile, boolean check) throws JAXBException {
        File file = new File(patchFile);
        checkBulltentin = check;
        if (file != null) {
            if (checkBulltentin) {
                context = JAXBContext.newInstance(MeteorologicalBulletinType.class);
                unmarshaller = context.createUnmarshaller();
                unmarshaller.setEventHandler(new DefaultValidationEventHandler());
                rootBull = unmarshaller.unmarshal(new StreamSource(file), MeteorologicalBulletinType.class);
                taf = new TAF();
                listTaf = new ArrayList<>();
                listTaf = processBull(rootBull);
                bulletinIdentifier = getBulletinIdentifier(rootBull);
            } else {
                context = JAXBContext.newInstance(TAFType.class);
                unmarshaller = context.createUnmarshaller();
                unmarshaller.setEventHandler(new DefaultValidationEventHandler());
                root = unmarshaller.unmarshal(new StreamSource(file), TAFType.class);
                taf = new TAF();
                taf = processTaf(root);
            }
            file.delete();
        }
    }

    public TAF getTaf() {
        return taf;
    }

    /**
     * @param taf
     */
    public void setTaf(TAF taf) {
        this.taf = taf;
    }

    private TAF processTaf(JAXBElement<TAFType> root) {
        TAF obj = new TAF();
        if (root != null) {
            TAFType objectType = root.getValue();
            if (objectType != null) {
                obj = process(objectType);
            }
        }
        return obj;
    }

    private String getBulletinIdentifier(JAXBElement<MeteorologicalBulletinType> rootBull) {
        String out = "";
//        try {
        if (rootBull.getValue().getBulletinIdentifier() != null) {
            String str = rootBull.getValue().getBulletinIdentifier();
            if (str.length() > 18) {
                out = str.substring(2, 8) + " " + str.substring(8, 12) + " " + str.substring(12, 18);
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + "\n";
        }
        return out;
    }

    private List<TAF> processBull(JAXBElement<MeteorologicalBulletinType> rootBull) {
        List<TAF> list = new ArrayList<>();
        TAF taf;
        if (rootBull.getValue().getMeteorologicalInformation() != null) {
            List<MeteorologicalInformationMemberPropertyType> lsObj = rootBull.getValue().getMeteorologicalInformation();
            for (MeteorologicalInformationMemberPropertyType Obj : lsObj) {
                taf = null;
                TAFType aRType = (TAFType) Obj.getAbstractFeature().getValue();
                if (aRType != null) {
                    taf = process(aRType);
                    if (Obj.getAbstractFeature().getName() != null) {
                        if (Obj.getAbstractFeature().getName().getLocalPart() != null) {
                            String type = Obj.getAbstractFeature().getName().getLocalPart();
                            if (!type.isEmpty()) {
                                // taf.setTypeofReport(type + " ");
                            }
                        }
                    }
                    if (taf != null) {
                        list.add(taf);
                    }
                }
            }
        }
        return list;
    }

    public TAF process(TAFType objectType) {
        TAF taf = new TAF();
        String strIssueTime = objectType.getIssueTime().getTimeInstant().getTimePosition().getValue().size() > 0 ? objectType.getIssueTime().getTimeInstant().getTimePosition().getValue().get(0) : "";
        taf.setLocationIndicator(getStrAirport(objectType));
        //IssueTime
        taf.setIssuedDate(strIssueTime.replace("T", " "));
        //REPORT TYPE 
        // taf.setTypeofReport(getTypeofReport());
        taf.setDateofObservation(getTimeOfIssue(objectType));
//validPeriod
        taf.setValidPeriod(getValidPeriod(objectType));
        //AMD 
        taf.setStatus(getAMDs(objectType));
//Day and period of validity of forecast (M)
        taf.setValidPeriod(getDayAndPeriod(objectType));
//Identification of a cancelled forecast ©
        taf.setIdentificationOfAancelled(getIdentificationOfAancelled(objectType));
//SurfaceWind
        taf.setSurfaceWind(getSurfaceWind(objectType));
//Visibility
        taf.setVisibility(getVisibi(objectType));
//Weather (C)4, 5
        taf.setWeather(getWeather(objectType));
//Present weather
        taf.setPresentWeather(getWeather(objectType));//getPresentWeather(objectType));
// could
        taf.setCloud(getClould(objectType));
//Temperature
        taf.setTemperature(getTemperature(objectType));
//Time from
        taf.setTimeFrom(getTimeFroms(objectType));
//Time to
        taf.setTimeTo(getTimeTos(objectType));
//Cavok
        taf.setCavok(getCavok(objectType));
//Wind
        taf.setWind(getWind(objectType));
//getTrend
        taf.setChangeForecast(getTrend(objectType));
//RAWW
        taf.setRAWW(getRAWWs(objectType));
        return taf;
    }

    private String getContentTaf(TAF taf) {
        String out = "";
//        try {
        String content = taf.getTypeofReport() + taf.getLocationIndicator() + taf.getDateofObservation() + taf.getValidPeriod() + taf.getStatus() + taf.getValidPeriod()+ taf.getIdentificationOfAancelled()
                + taf.getSurfaceWind() + taf.getVisibility() + taf.getWeather() + taf.getCloud() + taf.getTemperature();
        String str2 = taf.getChangeForecast();
        if (content.isEmpty()) {
            out = "";
        } else {
            if (str2.isEmpty()) {
                out = content.trim() + "=";
            } else {
                out = (content + "\n" + str2).trim() + "=";
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        return out;
    }

    private String getContentTafBull() {
        String out = "";
//        try {
        if (listTaf.size() > 0) {
            for (TAF met : listTaf) {
                String content = getContentTaf(met);
                if (content.isEmpty()) {
                    out = "";
                } else {
                    if (out.isEmpty()) {
                        out = content;
                    } else {
                        out = out + "\n" + content;
                    }
                }
            }
            if (!out.isEmpty()) {
                out = bulletinIdentifier + out;//addValueToContent(
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        return out;
    }

    public String getContent() {
        String out = "";
        if (checkBulltentin) {
            out = getContentTafBull();
        } else {
            out = getContentTaf(taf);
        }
        return out;
    }

    private String getTypeofReport() {
        String out = "";
        try {
            if (!checkBulltentin) {
                out = root.getName().getLocalPart();
            }
        } catch (Exception e) {
            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    //getTemperature
    private String getTemperature(TAFType objectType) {
        String out = "";
//        try {
        if (objectType != null) {
            if (objectType.getBaseForecast() != null) {
                if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast() != null) {
                    if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getTemperature() != null) {
                        List<AerodromeAirTemperatureForecastPropertyType> list = objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getTemperature();
                        for (AerodromeAirTemperatureForecastPropertyType item : list) {
                            String maxV = "";
                            String maxT = "";
                            if (item.getAerodromeAirTemperatureForecast() != null) {
                                if (item.getAerodromeAirTemperatureForecast().getMaximumAirTemperature() != null) {
                                    maxV = formatDoubleToString(item.getAerodromeAirTemperatureForecast().getMaximumAirTemperature().getValue(), 2);
                                    if (!maxV.isEmpty()) {
                                        maxV = "TX" + maxV;
                                    }
                                }
                                if (item.getAerodromeAirTemperatureForecast().getMaximumAirTemperatureTime() != null) {
                                    if (item.getAerodromeAirTemperatureForecast().getMaximumAirTemperatureTime().getTimeInstant() != null) {
                                        if (item.getAerodromeAirTemperatureForecast().getMaximumAirTemperatureTime().getTimeInstant().getTimePosition() != null) {
                                            if (item.getAerodromeAirTemperatureForecast().getMaximumAirTemperatureTime().getTimeInstant().getTimePosition().getValue() != null) {
                                                if (item.getAerodromeAirTemperatureForecast().getMaximumAirTemperatureTime().getTimeInstant().getTimePosition().getValue().size() > 0) {
                                                    maxT = item.getAerodromeAirTemperatureForecast().getMaximumAirTemperatureTime().getTimeInstant().getTimePosition().getValue().get(0);
                                                }
                                            }
                                        }
                                    }
                                    if (!maxT.isEmpty()) {
                                        maxT = getTime(maxT, "ddHH");
                                        if (!maxT.isEmpty()) {
                                            maxT = maxT + "Z";
                                        }
                                    }
                                }
                                if (!maxV.isEmpty()) {
                                    out = maxV + "/" + maxT;
                                }
                                String maxVV = "";
                                String maxTT = "";
                                if (item.getAerodromeAirTemperatureForecast().getMinimumAirTemperature() != null) {
                                    maxVV = formatDoubleToString(item.getAerodromeAirTemperatureForecast().getMinimumAirTemperature().getValue(), 2);
                                    if (!maxVV.isEmpty()) {
                                        maxVV = "TN" + maxVV;
                                    }
                                }
                                if (item.getAerodromeAirTemperatureForecast().getMinimumAirTemperatureTime() != null) {
                                    if (item.getAerodromeAirTemperatureForecast().getMinimumAirTemperatureTime().getTimeInstant() != null) {
                                        if (item.getAerodromeAirTemperatureForecast().getMinimumAirTemperatureTime().getTimeInstant().getTimePosition() != null) {
                                            if (item.getAerodromeAirTemperatureForecast().getMinimumAirTemperatureTime().getTimeInstant().getTimePosition().getValue() != null) {
                                                if (item.getAerodromeAirTemperatureForecast().getMinimumAirTemperatureTime().getTimeInstant().getTimePosition().getValue().size() > 0) {
                                                    maxTT = item.getAerodromeAirTemperatureForecast().getMinimumAirTemperatureTime().getTimeInstant().getTimePosition().getValue().get(0);
                                                }
                                            }
                                        }
                                    }
                                    if (!maxTT.isEmpty()) {
                                        maxTT = getTime(maxTT, "ddHH");
                                        if (!maxTT.isEmpty()) {
                                            maxTT = maxTT + "Z";
                                        }
                                    }
                                }
                                if (!maxVV.isEmpty()) {
                                    maxVV = maxVV + "/" + maxTT;
                                }
                                if (out.isEmpty()) {
                                    out = maxVV;
                                } else {
                                    out = out + " " + maxVV;
                                }
                            }
                        }
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    //getWeather
    private String getWeather(TAFType objectType) {
        String out = "";
//        try {
        if (objectType != null) {
            if (objectType.getBaseForecast() != null) {
                if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast() != null) {
                    if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getWeather() != null) {
                        List<AerodromeForecastWeatherType> obj = objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getWeather();
                        if (obj.size() > 0) {
                            for (AerodromeForecastWeatherType item : obj) {
                                if (item.getHref() != null) {
                                    if (out.isEmpty()) {
                                        out = split(item.getHref());
                                    } else {
                                        out = out + " " + split(item.getHref());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }
////getSurfaceWind

    private String getSurfaceWind(TAFType objectType) {
        String out = "";
//        try {
        if (objectType.getBaseForecast() != null) {
            if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast() != null) {
                if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getSurfaceWind() != null) {
                    AerodromeSurfaceWindForecastType obj = objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast();
                    if (obj != null) {
                        String strmeanWind = getMeanWind(obj);
                        if (!strmeanWind.isEmpty()) {
                            out = strmeanWind;
                        }
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    private String getMeanWind(AerodromeSurfaceWindForecastType obj) {
        String out = "";
        //try {
        if (obj != null) {
            String meanWindDirection = "";
            if (obj.getMeanWindDirection() != null) {
                if (obj.getMeanWindDirection() != null) {
                    meanWindDirection = convertDouble2String(obj.getMeanWindDirection().getValue(), 3);// formatDoubleToString(obj.getMeanWindDirection().getValue(), 3);
                }
            }
            String meanWindSpeed = "";
            String meanWindSpee_oum = "";
            if (obj.getMeanWindSpeed() != null) {
                meanWindSpeed = convertDouble2String(obj.getMeanWindSpeed().getValue(), 2);//formatDoubleToString(obj.getMeanWindSpeed().getValue(), 2);
                String oum = obj.getMeanWindSpeed().getUom();
                meanWindSpee_oum = getSpeedUnlts(oum);
            }
            String windGustSpeed = "";
            if (obj.getWindGustSpeed() != null) {
                if (obj.getWindGustSpeed() != null) {
                    windGustSpeed = convertDouble2String(obj.getWindGustSpeed().getValue(), 2);//formatDoubleToString(obj.getWindGustSpeed().getValue(), 2);
                    if (!windGustSpeed.isEmpty()) {
                        windGustSpeed = "G" + windGustSpeed;
                    }
                }
            }
            out = meanWindDirection + meanWindSpeed + windGustSpeed + meanWindSpee_oum;
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        return out;
    }
//setDayAndPeriod()  

    public String getDayAndPeriod(TAFType objectType) {
        String out = "";
//        try {
        if (objectType.getCancelledReportValidPeriod() != null) {
            if (objectType.getCancelledReportValidPeriod().getTimePeriod() != null) {
                if (objectType.getCancelledReportValidPeriod().getTimePeriod().getBeginPosition() != null) {
                    if (objectType.getCancelledReportValidPeriod().getTimePeriod().getBeginPosition().getValue() != null) {
                        if (objectType.getCancelledReportValidPeriod().getTimePeriod().getBeginPosition().getValue().size() > 0) {
                            String a = objectType.getCancelledReportValidPeriod().getTimePeriod().getBeginPosition().getValue().get(0);
                            out = getTime(a, "ddHHmm");
                        }
                    }
                }
                if (objectType.getCancelledReportValidPeriod().getTimePeriod().getEndPosition() != null) {
                    if (objectType.getCancelledReportValidPeriod().getTimePeriod().getEndPosition().getValue() != null) {
                        if (objectType.getCancelledReportValidPeriod().getTimePeriod().getEndPosition().getValue().size() > 0) {
                            String a = objectType.getCancelledReportValidPeriod().getTimePeriod().getEndPosition().getValue().get(0);
                            a = getTime(a, "ddHHmm");
                            if (!out.isEmpty()) {
                                out = out + " " + a;
                            }
                        }
                    }
                }

            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    public String getIdentificationOfAancelled(TAFType objectType) {
        String out = "";
        try {
            if (objectType.isIsCancelReport()) {
                out = "CNL";
            }

        } catch (Exception e) {
            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    //setStrAirport
    public String getStrAirport(TAFType objectType) {
        String out = "";
//        try {
        if (objectType != null) {
            if (objectType.getAerodrome() != null) {
                if (objectType.getAerodrome().getAirportHeliport() != null) {
                    if (objectType.getAerodrome().getAirportHeliport().getTimeSlice() != null) {
                        if (objectType.getAerodrome().getAirportHeliport().getTimeSlice().size() > 0) {
                            if (objectType.getAerodrome().getAirportHeliport().getTimeSlice().get(0).getAirportHeliportTimeSlice() != null) {
                                if (objectType.getAerodrome().getAirportHeliport().getTimeSlice().get(0).getAirportHeliportTimeSlice().getRest() != null) {
                                    if (objectType.getAerodrome().getAirportHeliport().getTimeSlice().get(0).getAirportHeliportTimeSlice().getRest().size() > 0) {
                                        CodeAirportHeliportDesignatorType a = (CodeAirportHeliportDesignatorType) objectType.getAerodrome().getAirportHeliport().getTimeSlice().get(0).getAirportHeliportTimeSlice().getRest().get(0).getValue();
                                        out = a.getValue();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    private String getValidPeriod(TAFType objectType) {
        String out = "";
        String from = "";
        String end = "";
//        try {
        if (objectType.getValidPeriod() != null) {
            if (objectType.getValidPeriod().getTimePeriod() != null) {
                if (objectType.getValidPeriod().getTimePeriod().getBeginPosition() != null) {
                    if (objectType.getValidPeriod().getTimePeriod().getBeginPosition().getValue() != null) {
                        from = objectType.getValidPeriod().getTimePeriod().getBeginPosition().getValue().size() > 0 ? objectType.getValidPeriod().getTimePeriod().getBeginPosition().getValue().get(0) : "";
                        from = getTime(from, "ddHH");
                    }
                }
                if (objectType.getValidPeriod().getTimePeriod().getEndPosition() != null) {
                    if (objectType.getValidPeriod().getTimePeriod().getEndPosition().getValue() != null) {
                        end = objectType.getValidPeriod().getTimePeriod().getEndPosition().getValue().size() > 0 ? objectType.getValidPeriod().getTimePeriod().getEndPosition().getValue().get(0) : "";
                        end = getTime(end, "ddHH");
                    }
                }
                if (!from.isEmpty() & !end.isEmpty()) {
                    out = from + "/" + end;
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    private String getTimeOfIssue(TAFType objectType) {
        String out = "";
//        try {
        if (objectType.getIssueTime() != null) {
            if (objectType.getIssueTime().getTimeInstant() != null) {
                if (objectType.getIssueTime().getTimeInstant().getTimePosition() != null) {
                    if (objectType.getIssueTime().getTimeInstant().getTimePosition().getValue() != null) {
                        out = objectType.getIssueTime().getTimeInstant().getTimePosition().getValue().size() > 0 ? objectType.getIssueTime().getTimeInstant().getTimePosition().getValue().get(0) : "";
                        out = getTime(out, "ddHHmm");
                        if (!out.isEmpty()) {
                            out = out + "Z";
                        }
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    //getTrend 
    private String getTrend(TAFType objectType) {
        String out = "";
//        try {
        List<MeteorologicalAerodromeForecastPropertyType> list = objectType.getChangeForecast();
        for (MeteorologicalAerodromeForecastPropertyType item : list) {
            String content = "";
            String content1 = getBeginTrend(item);//chcek
            String content2 = getTimePeriod(item);
            String content3 = getSurfaceWind(item);
            String content4 = getPervalilingVisibi(item);
            String content5 = getPrevailingWeather(item);
            String content6 = getCloud(item);
            content = content1 + content2 + content3 + content4 + content5 + content6;
            content = content.trim();
            if (!content.isEmpty()) {
                if (out.isEmpty()) {
                    out = content;
                } else {
                    out = out + "\n" + content;
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    //Present weather 
    private String getTimePeriod(MeteorologicalAerodromeForecastPropertyType object) {
        String out = "";
//        try {
        if (object != null) {
            if (object.getMeteorologicalAerodromeForecast() != null) {
                if (object.getMeteorologicalAerodromeForecast().getPhenomenonTime() != null) {
                    if (object.getMeteorologicalAerodromeForecast().getPhenomenonTime().getTimePeriod() != null) {
                        TimePeriodType TimObject = (TimePeriodType) object.getMeteorologicalAerodromeForecast().getPhenomenonTime().getTimePeriod();
                        String timeTimePeriodBegin = "";
                        String timeTimePeriodEnd = "";
                        String strTrendForecastTimeIndicatorType = "";
                        if (TimObject.getBeginPosition().getValue() != null) {
                            timeTimePeriodBegin = getTime(TimObject.getBeginPosition().getValue().size() > 0 ? TimObject.getBeginPosition().getValue().get(0) : "", "ddHH");
                        }
                        if (TimObject.getEndPosition().getValue() != null) {
                            timeTimePeriodEnd = getTime(TimObject.getEndPosition().getValue().size() > 0 ? TimObject.getEndPosition().getValue().get(0) : "", "ddHH");//1700
                        }
                        if (object.getMeteorologicalAerodromeForecast() != null) {
                            strTrendForecastTimeIndicatorType = getTrendForecastTimeIndicatorType(object.getMeteorologicalAerodromeForecast().getChangeIndicator());
                            if (strTrendForecastTimeIndicatorType.equals("FM")) {
                                timeTimePeriodEnd = strTrendForecastTimeIndicatorType + getTime(TimObject.getBeginPosition().getValue().size() > 0 ? TimObject.getBeginPosition().getValue().get(0) : "", "ddHHmm");
                            } else if (timeTimePeriodBegin.length() > 0 && timeTimePeriodEnd.length() > 0) {
                                if (!timeTimePeriodBegin.equals(timeTimePeriodEnd)) {
                                    timeTimePeriodEnd = timeTimePeriodBegin + "/" + timeTimePeriodEnd;
                                }
                            }
                        }
                        out = timeTimePeriodEnd;
                        out = out.trim();
                        if (!out.isEmpty()) {
                            out = out + " ";
                        }
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        return out;
    }

    private String getSurfaceWind(MeteorologicalAerodromeForecastPropertyType object) {
        String out = "";
//        try {
        String value = "";
        String str1 = "";
        String str2 = "";
        String str3 = "";
        String str4 = "";
        String str5 = "";
        if (object != null) {
            if (object.getMeteorologicalAerodromeForecast() != null) {
                if (object.getMeteorologicalAerodromeForecast().getSurfaceWind() != null) {
                    if (object.getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast() != null) {
                        String oum = "";
                        if (object.getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast().getMeanWindDirection() != null) {
                            Object checkAuto = object.getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast().getMeanWindDirection().getValue();//AngleType                    
                            if (checkAuto != null) {
                                double i = (double) checkAuto;
//                                int v = (int) i;
                                value = formatDoubleToString(i, 3); //String.format("%03d", v).trim(); // dang 170.0can dinh dang laij 170                
                                str1 = value;
                            }
                            checkAuto = object.getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast().getMeanWindDirection().getUom();
                            if (checkAuto != null) {
                                value = String.valueOf(checkAuto).trim();//'' customFormat("##", (double) checkAuto);
                                if (value.equals("deg")) {
                                    value = "G";
                                    str3 = value;
                                }
                            }
                        }
                        if (object.getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast().getMeanWindSpeed() != null) {
                            Object checkAuto = object.getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast().getMeanWindSpeed().getValue();
                            if (checkAuto != null) {
                                double i = (double) checkAuto;
//                                int v = (int) i;
                                value = formatDoubleToString(i, 2); // String.format("%02d", v).trim(); // dang 6.0 can dinh dang laij 06
                                str2 = value;
                            }
                            checkAuto = object.getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast().getMeanWindSpeed().getUom();
                            if (checkAuto != null) {
                                value = String.valueOf(checkAuto).trim();//'' customFormat("##", (double) checkAuto);
                                if (!value.isEmpty()) {
                                    str5 = getSpeedUnlts(value);
                                }
                            }
//                    }
                        }
                        if (object.getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast().getWindGustSpeed() != null) {
                            Object checkAuto = object.getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast().getWindGustSpeed().getValue();
                            if (checkAuto != null) {
                                double i = (double) checkAuto;
//                                int v = (int) i;
                                value = formatDoubleToString(i, 2); //String.format("%02d", v).trim();// dang 12.0 can dinh dang laij 12
                                str4 = value;
                            }
                        }
                    }
                }
            }
        }
        if (str4.isEmpty()) {
            if (str1.isEmpty()) {
                str1 = "VBR";
            }
            out = str1 + str2 + str5;
        } else {
            out = str1 + str2 + str3 + str4 + str5;
        }
        out = out.trim();
        if (!out.isEmpty()) {
            out = out + " ";
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        return out;
    }

    private String getPervalilingVisibi(MeteorologicalAerodromeForecastPropertyType object) {
        String out = "";
//        try {
        if (object != null) {
            if (object.getMeteorologicalAerodromeForecast() != null) {
                if (object.getMeteorologicalAerodromeForecast().getPrevailingVisibility() != null) {
                    double checkAuto = object.getMeteorologicalAerodromeForecast().getPrevailingVisibility().getValue();
                    RelationalOperatorType type = object.getMeteorologicalAerodromeForecast().getPrevailingVisibilityOperator();
                    out = getPrevailingVisibility(checkAuto, type);
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
//        out = out.trim();
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    private String getPrevailingWeather(MeteorologicalAerodromeForecastPropertyType object) {
        String out = "";
//        try {
        if (object != null) {
            if (object.getMeteorologicalAerodromeForecast() != null) {
                if (object.getMeteorologicalAerodromeForecast().getWeather() != null) {
                    List<AerodromeForecastWeatherType> list = object.getMeteorologicalAerodromeForecast().getWeather();
                    if (list.size() > 0) {
                        for (AerodromeForecastWeatherType item : list) {
                            String va  = split(item.getHref());
                            out = out + " " + va;
                        }
                    }
                    out = out.trim();
                    if (!out.isEmpty()) {
                        out = out + " ";
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        return out;
    }

    private String getCloud(MeteorologicalAerodromeForecastPropertyType object) {
        String out = "";
//        try {
        if (object != null) {
            if (object.getMeteorologicalAerodromeForecast() != null) {
                if (object.getMeteorologicalAerodromeForecast().getCloud() != null) {
                    if (object.getMeteorologicalAerodromeForecast().getCloud().getAerodromeCloudForecast() != null) {
                        List<CloudLayerPropertyType> list = object.getMeteorologicalAerodromeForecast().getCloud().getAerodromeCloudForecast().getLayer();
                        if (list.size() > 0) {
                            for (CloudLayerPropertyType item : list) {
                                String sty = split(item.getCloudLayer().getAmount().getHref());
                                double i = (double) item.getCloudLayer().getBase().getValue() / 100;
                                String val = formatDoubleToString(i, 3);
                                out = out + " " + sty + val;
                            }
                            out = out.trim();
                            if (!out.isEmpty()) {
                                out = out + " ";
                            }
                        }
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        return out;
    }

    // Set Wind
    private String getWind(TAFType objectType) {
        String out = "";
//        try {
        if (objectType != null) {
            if (objectType.getBaseForecast() != null) {
                if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast() != null) {
                    if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getSurfaceWind() != null) {
                        if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast() != null) {
                            if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast().getMeanWindSpeed() != null) {
                                Object checkAuto = objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getSurfaceWind().getAerodromeSurfaceWindForecast().getMeanWindSpeed().getValue();
                                if (checkAuto != null) {
                                    double i = (double) checkAuto;
                                    out = formatDoubleToString(i, 4); //customFormat("###", (double) checkAuto);
                                }
                            }
                        }
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    //Cavok
    private boolean getCavok(TAFType objectType) {
        boolean check = false;
        try {
            if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast() != null) {
                Object checkAuto = objectType.getBaseForecast().getMeteorologicalAerodromeForecast().isCloudAndVisibilityOK();
                if (checkAuto != null) {
                    check = (boolean) checkAuto;
                }
            }
        } catch (Exception e) {
            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
        }
        return check;
    }

    //clould
    private String getClould(TAFType objectType) {
        String out = "";
        if (objectType != null) {
            if (objectType.getBaseForecast() != null) {
                if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast() != null) {
                    if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getCloud() != null) {
                        if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getCloud().getAerodromeCloudForecast() != null) {
                            if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getCloud().getAerodromeCloudForecast().getLayer() != null) {
//        try {
                                List<CloudLayerPropertyType> list = objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getCloud().getAerodromeCloudForecast().getLayer();
                                for (CloudLayerPropertyType item : list) {
                                    String sty = split(item.getCloudLayer().getAmount().getHref());
                                    double i = (double) item.getCloudLayer().getBase().getValue() / 100;
                                    String type = "";
                                    if (item.getCloudLayer().getCloudType() != null) {
                                        if (item.getCloudLayer().getCloudType().getValue() != null) {
                                            if (item.getCloudLayer().getCloudType().getValue().getHref() != null) {
                                                type = split(item.getCloudLayer().getCloudType().getValue().getHref());
                                            }
                                        }
                                    }
                                    int v = (int) i;
                                    String val = formatDoubleToString(i, 3); //String.format("%03d", v).trim();
                                    if (out.isEmpty()) {
                                        out = sty + val + type;
                                    } else {
                                        out = out + " " + sty + val + type;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }
////Present weather 
//  //getTimeFroms --ValidPeriod

    private String getTimeFroms(TAFType objectType) {
        String out = "";
//        try {
        if (objectType != null) {
            if (objectType.getValidPeriod() != null) {
                if (objectType.getValidPeriod().getTimePeriod() != null) {
                    if (objectType.getValidPeriod().getTimePeriod().getBeginPosition() != null) {
                        if (objectType.getValidPeriod().getTimePeriod().getBeginPosition().getValue() != null) {
                            List<String> list = objectType.getValidPeriod().getTimePeriod().getBeginPosition().getValue();
                            if (list.size() > 0) {
                                out = String.valueOf(list.get(0)).trim();
                                out = getTime(out, "yyyy-MM-dd HH:mm:ss");
                            }
                        }
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + "Z ";
        }
        return out;
    }

    //getTimeTos --ValidPeriod
    private String getTimeTos(TAFType objectType) {
        String out = "";
//        try {
        if (objectType.getValidPeriod() != null) {
            if (objectType != null) {
                if (objectType.getValidPeriod().getTimePeriod() != null) {
                    if (objectType.getValidPeriod().getTimePeriod().getEndPosition() != null) {
                        if (objectType.getValidPeriod().getTimePeriod().getEndPosition().getValue() != null) {
                            List<String> list = objectType.getValidPeriod().getTimePeriod().getEndPosition().getValue();
                            if (list.size() > 0) {
                                out = String.valueOf(list.get(0)).trim();
                                out = getTime(out, "yyyy-MM-dd HH:mm:ss");
                            }
                        }
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + "Z ";
        }
        return out;
    }

    //visibi
    private String getVisibi(TAFType objectType) {
        String out = "";
//        try {
        if (objectType.getBaseForecast() != null) {
            if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast() != null) {
                if (objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getPrevailingVisibility() != null) {
                    Object checkAuto = objectType.getBaseForecast().getMeteorologicalAerodromeForecast().getPrevailingVisibility().getValue();
                    if (checkAuto != null) {
                        double i = (double) checkAuto;
                        out = formatDoubleToString(i, 4); //String.format("%04d", v).trim();// String.valueOf(checkAutos).trim();//customFormat("####", (double) checkAuto);
                    }
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }
//RAWW

    private String getRAWWs(TAFType objectType) {
        String out = "";
//        try {
        if (objectType != null) {
            if (objectType.getDescription() != null) {
                Object checkAuto = objectType.getDescription().getValue();
                if (checkAuto != null) {
                    out = checkAuto.toString().trim();
                    out = addValueToContent(out);
                }
            }
        }
//        } catch (Exception e) {
//            Logger.getLogger(ProcessTaf.class.getName()).log(Level.SEVERE, null, e);
//        }
        return out;
    }

    //amd
    private String getAMDs(TAFType objectType) {
        String out = "";
        //AMD
        if (objectType.getReportStatus() != null) {
            ReportStatusType checkAuto = objectType.getReportStatus();
            out = convertReportStatus(checkAuto);
        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }
}

