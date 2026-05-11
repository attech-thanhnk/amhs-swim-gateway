package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.AIRMETExpectedIntensityChangeType;
import _int.icao.iwxxm._2023_1.AerodromeCloudType.Layer;
import _int.icao.iwxxm._2023_1.AerodromeForecastChangeIndicatorType;
import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindTrendForecastType;
import _int.icao.iwxxm._2023_1.AirportHeliportPropertyType;
import _int.icao.iwxxm._2023_1.CloudAmountReportedAtAerodromeType;
import _int.icao.iwxxm._2023_1.CloudLayerPropertyType;
import _int.icao.iwxxm._2023_1.CloudLayerType;
import _int.icao.iwxxm._2023_1.DistanceWithNilReasonType;
import _int.icao.iwxxm._2023_1.SIGMETExpectedIntensityChangeType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeForecastPropertyType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeTrendForecastPropertyType;
import _int.icao.iwxxm._2023_1.RelationalOperatorType;
import _int.icao.iwxxm._2023_1.ReportStatusType;
import _int.icao.iwxxm._2023_1.SIGMETExpectedIntensityChangeType;
import _int.icao.iwxxm._2023_1.SigConvectiveCloudTypeType;
import _int.icao.iwxxm._2023_1.TimeIndicatorType;
import _int.icao.iwxxm._2023_1.TrendForecastTimeIndicatorType;
import _int.icao.iwxxm._2023_1.VisualRangeTendencyType;
import aero.aixm.schema._5_1.AirportHeliportTimeSlicePropertyType;
import aero.aixm.schema._5_1.AirportHeliportTimeSliceType;
import aero.aixm.schema._5_1.AirportHeliportType;
import aero.aixm.schema._5_1.CodeAirportHeliportDesignatorType;
import aero.aixm.schema._5_1.CodeOrganisationDesignatorType;
import aero.aixm.schema._5_1.UnitTimeSlicePropertyType;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import javax.xml.bind.JAXBElement;
import net.opengis.gml.v_3_2_1.LengthType;
import net.opengis.gml.v_3_2_1.ReferenceType;
import net.opengis.gml.v_3_2_1.TimeInstantPropertyType;
import net.opengis.gml.v_3_2_1.TimeInstantType;
import net.opengis.gml.v_3_2_1.TimePositionType;
import vn.asg.converter.utils.Builder;
import vn.asg.converter.utils.CustomDateFormat;

/**
 *
 * @author ThanhNk
 */
public class Common {
    private static final Logger log = LoggerFactory.getLogger(Common.class);
    protected DateFormat dateFormater = new CustomDateFormat();

    private static SimpleDateFormat date2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'");// dd/MM/yyyy
    private SimpleDateFormat instantTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");// dd/MM/yyyy

    public static String convertBulletinIdentification(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }

        Matcher matcher = Regex.metarBulletin.matcher(id);
        if (!matcher.matches()) {
            return "";
        }

        return (new Builder())
                .append(matcher.group("indicator"))
                .append(matcher.group("icaoDesignator"))
                .append(matcher.group("time"))
                .toString();
    }

    public static String convertDirectionFromDecimalToString(double val) {
        while (val < 0) {
            val += 360;
        }

        if (val >= 360) {
            val = val % 360;
        }

        if (val == 0) {
            return "N";
        }

        if (val > 0 && val < 90) {
            return "NE";
        }
        if (val == 90) {
            return "E";
        }

        if (val > 90 && val < 180) {
            return "SE";
        }

        if (val == 180) {
            return "S";
        }

        if (val > 190 && val < 270) {
            return "SW";
        }

        if (val == 270) {
            return "W";
        }

        return "NW";
    }

    public String getTime(String strDate, String format) {
        SimpleDateFormat sdfDate = new SimpleDateFormat(format);// hhmm
        Date now = getDateTime(strDate);
        return sdfDate.format(now);
    }

    public String getTime(String strDate, SimpleDateFormat format) {
        Date now = getDateTime(strDate);
        return format.format(now);
    }

    public Date parse(String date) throws ParseException {
        return dateFormater.parse(date);
    }

    private Date getDateTime(String strDate) {
        if (strDate == null || strDate.isEmpty())
            return new Date();

        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            return sdfDate.parse(strDate.replace("T", " ").replace("Z", ""));
        } catch (ParseException ex) {
            log.error("Failed to parse date: {}", strDate, ex);
            return new Date();
        }
    }

    public static Date getDateTime2(String date) throws ParseException {
        return date2.parse(date);
    }

    public String GetPos(List<Double> ds) {
        StringBuilder out = new StringBuilder();
        try {
            int j = 0;
            for (int i = 0; i < ds.size(); i++) {
                if (i % 2 == 0) {
                    if (i == 0) {
                        if (ds.get(i) < 0) {
                            out.append("S").append(convertPoint(ds.get(i), 2).replace("-", ""));
                        } else {
                            out.append("N").append(convertPoint(ds.get(i), 2));
                        }
                    } else {
                        if (ds.get(i) < 0) {
                            out.append(" - S").append(convertPoint(ds.get(i), 2).replace("-", ""));
                        } else {
                            out.append(" - N").append(convertPoint(ds.get(i), 2));
                        }
                    }
                } else {
                    if (ds.get(i) < 0) {
                        out.append(" W").append(convertPoint(ds.get(i), 3).replace("-", ""));
                    } else {
                        out.append(" E").append(convertPoint(ds.get(i), 3));
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Error in GetPos: ", ex);
        }
        return out.toString();
    }

    public String getPos(String strPos) {
        StringBuilder out = new StringBuilder();
        strPos = strPos.trim();
        try {
            String a[] = strPos.split(" ");
            int y = a.length;
            if (y > 0) {
                for (int i = 0; i < y; i++) {
                    double v = Double.parseDouble(a[i]);
                    // formatDoubleToString1(v, 3);
                    if (i % 2 == 0) {
                        String pos = convertPoint(v, 2);
                        if (i == 0) {
                            if (pos.contains("-")) {
                                out.append("S").append(pos.replace("-", ""));
                            } else {
                                out.append("N").append(pos);
                            }
                        } else {
                            if (pos.contains("-")) {
                                out.append(" - S").append(pos.replace("-", ""));
                            } else {
                                out.append(" - N").append(pos);
                            }
                        }
                    } else {
                        String pos = convertPoint(v, 3);
                        if (pos.contains("-")) {
                            out.append(" W").append(pos.replace("-", ""));
                        } else {
                            out.append(" E").append(pos);
                        }
                    }

                }
            }
        } catch (NumberFormatException ex) {
            log.error("Error in getPos: ", ex);
        }
        return out.toString();
    }

    public String convertDouble2String(double a, int i) {
        String out = "000";
        double b = a;
        String o = String.valueOf(b);// 12345.1
        String[] l = o.split("\\.");
        if (l.length > 0) {
            o = l[0];
            int v = Integer.parseInt(o);
            out = formatDoubleToString(v, i);// String.format("%0" + i + "d", v);
        }
        return out;
    }

    public String formatDoubleToString(double a, int s) {
        String out = "";
        try {
            int u = (int) a;
            String v = String.format("%0" + s + "d", u).trim();
            out = v.substring(0, s);
        } catch (Exception e) {
            log.error("Error formatting double", e);
        }
        return out;
    }

    public String convertPoint(double point, int NE) {
        boolean check = false;
        int b = (int) point;
        int a = (int) Math.round((point - b) * 60);
        if (a < 0) {
            a = a * -1;
        }
        if (b < 0) {
            b = b * -1;
            check = true;
        }
        StringBuilder buffer = new StringBuilder();
        if (b != 0) {
            buffer.append(String.format("%0" + NE + "d", b));
        } else {
            if (NE == 2) {
                buffer.append("00");
            } else if (NE == 3) {
                buffer.append("000");
            }
        }
        if (a > 0) {
            buffer.append(String.format("%0" + 2 + "d", a));
        } else {
            buffer.append("00");
        }
        if (check) {
            return "-" + buffer.toString();
        } else {
            return buffer.toString();
        }
    }

    public static String split(String str) {
        String out = "";
        if (!str.isEmpty()) {
            String[] ls = str.split("/");
            int c = ls.length;
            if (c > 0) {
                out = ls[c - 1].trim();
            }
        }
        return out;
    }

    public static String getValue(String href) {
        if (href == null || href.isEmpty()) {
            return "";
        }

        int index = href.lastIndexOf("/");
        if (index < 0) {
            index = href.lastIndexOf(":");
        }

        if (index < 0) {
            return href;
        }

        return href.substring(index + 1);
    }

    // metar
    public String getPrevailingVisibility(double value1, RelationalOperatorType v) {
        String out = "";
        try {
            int value = (int) value1;
            if (v != null) {
                if (v.equals(RelationalOperatorType.ABOVE)) {
                    if (value == 10000 | value == 1000) {
                        value = 9999;
                    }
                } else if (v.equals(RelationalOperatorType.BELOW)) {
                    if (value == 50) {
                        value = 0;
                    }
                }
            }
            out = formatDoubleToString(value, 4);// String.format("%04d", value).trim();
        } catch (Exception e) {
            log.error("Error formatting double", e);
        }
        return out;
    }

    public String getTrendForecastTimeIndicatorType(TrendForecastTimeIndicatorType timeIndicator) {
        if (timeIndicator == null) {
            return "";
        }
        switch (timeIndicator) {
            case AT:
                return "AT";
            case FROM:
                return "FM";
            case UNTIL:
                return "LT";
            default:
                return "";
        }
    }

    public String getCOR(ReportStatusType objectType) {

        if (objectType == null) {
            return "";
        }

        switch (objectType) {
            case AMENDMENT:
                return "AMD";
            case CORRECTION:
                return "COR";
            case NORMAL:
                return "";
            default:
                return "NIL";
        }
    }

    public String getPastTendency(VisualRangeTendencyType v) {
        if (v == null) {
            return "";
        }

        switch (v) {
            case DOWNWARD:
                return "D";
            case NO_CHANGE:
                return "N";
            case UPWARD:
                return "U";
            // case MISSING_VALUE:
            // return "M";
            default:
                return "";
        }
    }

    public String addValueToContent(String c, String v) {
        String out = "";
        if (c.isEmpty()) {
            return v;
        }
        String[] a = c.split("\n");
        if (a.length > 0) {
            String cc = a[a.length - 1];
            String tem = cc + v;
            if (tem.length() > 69) {
                c = c + "\n" + v;
            } else {
                c = c + " " + v;
            }
        }
        out = c;
        return out;
    }

    public String addValueToContent(String c) {
        String out = "";
        int i = 1;
        try {
            if (c.isEmpty()) {
                return out;
            }
            c = c.replace("\r\n", "").replace("\n", "");
            String[] a = c.split(" ");
            if (a.length > 0) {
                for (String it : a) {
                    it = it.trim();
                    if (!it.isEmpty()) {
                        if (out.length() > 69 * i) {
                            i++;
                            out = out + "\n";
                        } else {
                            out = out + " " + it;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error formatting double", e);
        }
        return out;
    }

    // taf
    public static String convertReportStatus(ReportStatusType obj) {
        String out = "";
        if (obj != null) {
            switch (obj) {
                case AMENDMENT:
                    out = "AMD";
                    break;
                case CORRECTION:
                    out = "";
                    break;
                case NORMAL:
                    out = "";
                    break;
                default:
                    out = "NIL";
            }
        }
        return out;
    }

    public String getTyoe(String strType) {
        switch (strType) {
            case "9":
                return "CB";
            case "32":
                return "TCU";
            default:
                return null;
        }
    }

    public static String getChangeIndicatorType(AerodromeForecastChangeIndicatorType changeIndicator) {

        switch (changeIndicator) {
            case BECOMING:
                return "BECMG";
            case FROM:
                return "FM";
            case PROBABILITY_30:
                return "PROB30";
            case PROBABILITY_30_TEMPORARY_FLUCTUATIONS:
                return "PROB30 TEMPO";
            case PROBABILITY_40:
                return "PROB40";
            case PROBABILITY_40_TEMPORARY_FLUCTUATIONS:
                return "PROB40 TEMPO";
            case TEMPORARY_FLUCTUATIONS:
                return "TEMPO";
            default:
                return "";
        }

    }

    public String getBeginTrend(MeteorologicalAerodromeForecastPropertyType object) {
        String out = "";
        if (object != null) {
            if (object.getMeteorologicalAerodromeForecast() != null) {
                if (object.getMeteorologicalAerodromeForecast().getChangeIndicator() != null) {
                    if (object.getMeteorologicalAerodromeForecast().getChangeIndicator().name() != null) {
                        Object value1 = object.getMeteorologicalAerodromeForecast().getChangeIndicator().name();
                        String value = value1.toString();
                        switch (value) {
                            case "BECOMING":
                                out = "BECMG";
                                break;
                            case "TEMPORARY_FLUCTUATIONS":
                                out = "TEMPO";
                                break;
                            // case "FROM":
                            // out = "FM";
                            // break;
                            default:
                                break;

                        }
                    }
                }
            }
        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    public String getBeginTrend(MeteorologicalAerodromeTrendForecastPropertyType object) {
        String out = "";
        if (object != null) {
            if (object.getMeteorologicalAerodromeTrendForecast() != null) {
                if (object.getMeteorologicalAerodromeTrendForecast().getChangeIndicator() != null) {
                    if (object.getMeteorologicalAerodromeTrendForecast().getChangeIndicator().name() != null) {
                        Object value1 = object.getMeteorologicalAerodromeTrendForecast().getChangeIndicator().name();
                        String value = value1.toString();
                        switch (value) {
                            case "BECOMING":
                                out = "BECMG";
                                break;
                            case "TEMPORARY_FLUCTUATIONS":
                                out = "TEMPO";
                                break;
                            case "FROM":
                                out = "FM";
                                break;
                            default:
                                break;

                        }
                    }
                }
            }
        }
        out = out.trim() + " ";
        return out;
    }

    public String getBeginTrend(String value) {
        switch (value) {
            case "BECOMING":
                return "BECMG";
            case "TEMPORARY_FLUCTUATIONS":
                return "TEMPO";
            case "FROM":
                return "FM";
            default:
                return "";
        }
    }

    public String getTrendForecastTimeIndicatorType(AerodromeForecastChangeIndicatorType timeIndicator) {
        String out = "";
        switch (timeIndicator) {
            case BECOMING:
                out = "BECMG";
                break;
            case TEMPORARY_FLUCTUATIONS:
                out = "TEMPO";
                break;
            case PROBABILITY_30:
                out = "PROB30";
                break;
            case PROBABILITY_40:
                out = "PROB40";
                break;
            case PROBABILITY_30_TEMPORARY_FLUCTUATIONS:
                out = "PROB30TEMPO";
                break;
            case PROBABILITY_40_TEMPORARY_FLUCTUATIONS:
                out = "PROB40TEMPO";
                break;
            case FROM:
                out = "FM";
                break;
            default:
                break;
        }
        return out;
    }

    // Sigmet
    public String getdirectionOfMotion(double v) {
        String out = "";
        if (v == 90) {
            out = "E";
        } else if (v == 180) {
            out = "S";
        } else if (v == 270) {
            out = "W";
        } else if (v == 360) {
            out = "N";
        } else if ((v < 90) & (0 < v)) {
            out = "NE";
        } else if ((v < 180) & (90 < v)) {
            out = "SE";
        } else if ((v < 270) & (180 < v)) {
            out = "SW";
        } else if ((v < 360) & (270 < v)) {
            out = "NW";
        }
        return out;
    }

    public String getIntensityChangeType(SIGMETExpectedIntensityChangeType type) {
        String out = "";
        switch (type) {
            case INTENSIFY:
                out = "INTSF";
                break;
            case WEAKEN:
                out = "WKN";
                break;
            case NO_CHANGE:
                out = "NC";
                break;
            default:
                break;
        }
        return out;
    }

    public String getIntensityChangeType(AIRMETExpectedIntensityChangeType type) {
        String out = "";
        switch (type) {
            case INTENSIFY:
                out = "INTSF";
                break;
            case WEAKEN:
                out = "WKN";
                break;
            case NO_CHANGE:
                out = "NC";
                break;
            default:
                break;
        }
        return out;
    }

    public String getTimeIndicatorType(TimeIndicatorType type) {
        String out = "";
        if (type == null) {
            return "";
        }
        if (type.equals(TimeIndicatorType.OBSERVATION)) {
            out = "OBS";
        } else if (type.equals(TimeIndicatorType.FORECAST)) {
            out = "FCST";
        }
        return out;

    }

    // MPS("m/s"), KT("[kn_i]"),KMH("km/h");
    public static String getSpeedUnlts(String str) {

        if (str == null) {
            return "KMH";
        }
        switch (str) {
            case "m/s":
                return "MPS";
            case "[kn_i]":
                return "KT";
            case "km/h":
                return "KMH";
            default:
                return "KMH";
        }
    }
    // MPS("m/s"), KT("[kn_i]"),KMH("km/h");

    public String getSpeedUnltsTca(String str1) {
        String out = "";
        if (str1 != null) {
            String a[] = str1.split(" ");
            if (a.length > 0) {
                String str = a[a.length - 1];
                if (str.equals("m/s")) {
                    out = "MPS";
                } else if (str.equals("[kn_i]")) {
                    out = "KT";
                } else if (str.equals("km/h")) {
                    out = "KMH";
                } else {
                    out = "KMH";
                }
                out = str1.substring(0, str.length() - 1).trim() + out;
            }
        }
        return out;
    }

    public String getIndicator(List<UnitTimeSlicePropertyType> list) {
        String out = "";
        if (list != null) {
            if (list.size() > 0) {
                for (UnitTimeSlicePropertyType item : list) {
                    int i = item.getUnitTimeSlice().getRest().size();
                    if (i > 0) {
                        CodeOrganisationDesignatorType v = (CodeOrganisationDesignatorType) item.getUnitTimeSlice()
                                .getRest().get(i - 1).getValue();
                        out = out + " " + v.getValue();
                    }
                }
            }
        }
        out = out.trim();
        return out;
    }

    // VAA
    public String addSpa(String str1, int count) {
        String out = "";
        if (!str1.isEmpty()) {
            int l = str1.length();
            if (l >= count) {
                out = str1;
            } else {
                for (int i = 0; i < count - l; i++) {
                    str1 = str1 + " ";
                }
                out = str1;
            }
        }
        return out;
    }

    // TCA
    // MM("mm"),M("m"), KM("km"), FT("[ft_i]"), SM("SM"), NM("[nmi_i]");
    public String getIwxxmEnum(String str) {
        String out = "";
        if (str != null) {
            switch (str) {
                case "[nmi_i]":
                    out = "NM";
                    break;
                case "SM":
                    out = "SM";
                    break;
                case "[ft_i]":
                    out = "FT";
                    break;
                case "km":
                    out = "KM";
                    break;
                case "m":
                    out = "M";
                    break;
                case "mm":
                    out = "MM";
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------------------
    public String getIssuedTime(TimeInstantPropertyType timeInstance) {

        if (timeInstance == null || !timeInstance.isSetTimeInstant()) {
            return "";
        }

        TimeInstantType timeType = timeInstance.getTimeInstant();
        if (!timeType.isSetTimePosition()) {
            return "";
        }

        TimePositionType timePosition = timeType.getTimePosition();
        if (!timePosition.isSetValue()) {
            return "";
        }

        List<String> values = timePosition.getValue();
        return values.get(0);
    }

    public Date getDate(TimeInstantPropertyType timeInstanct) {

        if (timeInstanct == null || !timeInstanct.isSetTimeInstant()) {
            return new Date();
        }

        TimeInstantType timeInstanceType = timeInstanct.getTimeInstant();
        if (!timeInstanceType.isSetTimePosition()) {
            return new Date();
        }

        TimePositionType timePosition = timeInstanceType.getTimePosition();
        if (!timePosition.isSetValue()) {
            return new Date();
        }

        List<String> strList = timePosition.getValue();
        String time = strList.get(0);

        try {

            return instantTimeFormat.parse(time);

        } catch (ParseException ex) {
            System.out.print("Convert date time " + time + " fail." + ex.getMessage());
            return new Date();
        }

    }

    public String getStrAirport(AirportHeliportPropertyType aeridrome) {

        if (aeridrome == null || !aeridrome.isSetAirportHeliport()) {
            return "";
        }

        AirportHeliportType airportHeliport = aeridrome.getAirportHeliport();
        if (!airportHeliport.isSetTimeSlice()) {
            return "";
        }

        List<AirportHeliportTimeSlicePropertyType> timeSlices = airportHeliport.getTimeSlice();
        AirportHeliportTimeSlicePropertyType airportHeliportTimeSlice = timeSlices.get(0);
        if (!airportHeliportTimeSlice.isSetAirportHeliportTimeSlice()) {
            return "";
        }

        AirportHeliportTimeSliceType airportHeliportTimeSliceType = airportHeliportTimeSlice
                .getAirportHeliportTimeSlice();
        List<JAXBElement<?>> jaxbList = airportHeliportTimeSliceType.getRest();
        if (jaxbList == null || jaxbList.size() == 0) {
            return "";
        }

        CodeAirportHeliportDesignatorType codeAirport = (CodeAirportHeliportDesignatorType) jaxbList.get(0).getValue();
        if (codeAirport == null) {
            return "";
        }

        return codeAirport.getValue();
    }

    public String getSurfaceWind(AerodromeSurfaceWindTrendForecastType surfaceWindTrendForecase) {

        // if (!surfaceWindTrendForecase.isSetMeanWindDirection() ||
        // !surfaceWindTrendForecase.isSetMeanWindSpeed() ||
        // !surfaceWindTrendForecase.isSetWindGustSpeed()) {
        if (!surfaceWindTrendForecase.isSetMeanWindDirection() || !surfaceWindTrendForecase.isSetMeanWindSpeed()) {
            return "";
        }

        double direction = surfaceWindTrendForecase.getMeanWindDirection().getValue();
        double meanWind = surfaceWindTrendForecase.getMeanWindSpeed().getValue();

        String unit = Common.getSpeedUnlts(surfaceWindTrendForecase.getMeanWindSpeed().getUom());
        Builder builder = new Builder("");
        builder.append("%03d", (int) direction);
        builder.append(meanWind < 100 ? "%02d" : "%03d", (int) meanWind);

        if (!surfaceWindTrendForecase.isSetWindGustSpeed()) {
            builder.append(unit);
            return builder.toString();
        }

        double gustWind = surfaceWindTrendForecase.getWindGustSpeed().getValue();
        builder.append(gustWind < 100 ? "G%02d" : "G%03d", (int) gustWind);
        builder.append(unit);
        return builder.toString();

    }

    public String getPrevailVisibility(LengthType object) {

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

    public <T extends ReferenceType> String getWeathers(List<T> presentWeatherTypeList) {
        // StringBuilder builder = new StringBuilder();
        // String joinedCharacter = "";
        Builder builder = new Builder();
        for (T item : presentWeatherTypeList) {

            if (item == null) {
                continue;
            }
            if (item.isSetNilReason()) {
                String nilReason = item.getNilReason().get(0);
                if (nilReason.equalsIgnoreCase("http://codes.wmo.int/common/nil/notObservable")) {
                    builder.append("//");
                }
                continue;
            }

            String presentWeatherCharacter = Common.split(item.getHref());
            if (presentWeatherCharacter.isEmpty()) {
                continue;
            }
            // builder.append(joinedCharacter).append(presentWeatherCharacter);
            // joinedCharacter = " ";
            builder.append(presentWeatherCharacter);
        }
        return builder.toString();

    }

    // TODO: Reimplement
    public <T extends CloudLayerPropertyType> String getCloud(List<T> cloudLayerList) {
        // List<Layer> cloudLayerList = cloudType.getLayer();
        Builder builder = new Builder();

        for (T layer : cloudLayerList) {

            if (layer instanceof Layer && ((Layer) layer).isSetNilReason()) {
                Layer cldLayer = (Layer) layer;
                if (cldLayer.isSetNilReason()) {
                    String nilReason = cldLayer.getNilReason().get(0);
                    if (nilReason.equalsIgnoreCase("http://codes.wmo.int/common/nil/notObservable")
                            || nilReason.equalsIgnoreCase("http://codes.wmo.int/common/nil/notDetectedByAutoSystem")) {
                        builder.append("//////");
                    }
                    continue;
                }
            }

            if (!layer.isSetCloudLayer()) {
                continue;
            }

            final CloudLayerType cloudLayer = layer.getCloudLayer();
            String amount = this.getCloudAmount(cloudLayer.getAmount());
            String value = this.getCloudValue(cloudLayer.getBase());
            String type = this.getCloudType(this.getObject(cloudLayer.getCloudType()));
            builder.append("%s%s%s", amount, value, type);
            //
            // CloudLayerType cloudLayer = layer.getCloudLayer();
            //
            // String code = "";
            //
            // if (cloudLayer.isSetAmount()) {
            // CloudAmountReportedAtAerodromeType cloudAmountReport =
            // cloudLayer.getAmount();
            // if (cloudAmountReport.isSetNilReason()) {
            // String nilReason = cloudAmountReport.getNilReason().get(0);
            // if
            //
            // }
            // }
            //
            // if (!cloudLayer.isSetAmount() || !cloudLayer.isSetBase()) {
            // continue;
            // }
            //
            //
            // String code = Common.split(cloudLayer.getAmount().getHref());
            // int value = (int) cloudLayer.getBase().getValue() / 100;
            //
            //
            //
            // SigConvectiveCloudTypeType sigConvectiveCloudType =
            // getObject(cloudLayer.getCloudType());
            // if (sigConvectiveCloudType == null || !sigConvectiveCloudType.isSetHref()) {
            // builder.append("%s%03d", code, value);
            // continue;
            // }

            // builder.append("%s%03d%s", code, value,
            // Common.split(sigConvectiveCloudType.getHref()));
        }

        return builder.toString();
    }

    private String getCloudAmount(CloudAmountReportedAtAerodromeType cloudAmountReport) {
        if (cloudAmountReport == null) {
            return "";
        }

        if (cloudAmountReport.isSetNilReason()) {
            String nilReason = cloudAmountReport.getNilReason().get(0);
            return nilReason.equalsIgnoreCase("http://codes.wmo.int/common/nil/notObservable") ? "//" : "";
        }

        if (!cloudAmountReport.isSetHref()) {
            return "";
        }
        return getValue(cloudAmountReport.getHref());
    }

    private String getCloudValue(DistanceWithNilReasonType value) {
        if (value == null) {
            return "";
        }

        if (value.isSetNilReason()) {
            String nilReason = value.getNilReason().get(0);
            return nilReason.equalsIgnoreCase("http://codes.wmo.int/common/nil/notObservable") ? "//" : "";
        }

        if (!value.isSetValue()) {
            return "";
        }

        int result = (int) value.getValue() / 100;
        return String.format("%03d", result);
    }

    private String getCloudType(SigConvectiveCloudTypeType cloudType) {
        if (cloudType == null) {
            return "";
        }

        if (cloudType.isSetNilReason()) {
            String nilReason = cloudType.getNilReason().get(0);
            return nilReason.equalsIgnoreCase("http://codes.wmo.int/common/nil/notObservable") ? "//" : "";
        }

        if (!cloudType.isSetHref()) {
            return "";
        }

        return this.getValue(cloudType.getHref());
    }

    // public String getIntensityChangeType(ExpectedIntensityChangeType type) {
    // String out = "";
    // switch (type) {
    // case INTENSIFY:
    // out = "INTSF";
    // break;
    // case WEAKEN:
    // out = "WKN";
    // break;
    // case NO_CHANGE:
    // out = "NC";
    // break;
    // default:
    // break;
    // }
    // return out;
    // }
    public <T> T getObject(JAXBElement<T> element) {
        if (element == null) {
            return null;
        }
        return element.getValue();
    }

    public Date parseDatetime(TimePositionType date) {

        if (date == null || !date.isSetValue()) {
            return new Date();
        }

        String strDate = date.getValue().get(0);

        try {
            return dateFormater.parse(strDate);
        } catch (ParseException ex) {
            return new Date();
        }
    }

}
