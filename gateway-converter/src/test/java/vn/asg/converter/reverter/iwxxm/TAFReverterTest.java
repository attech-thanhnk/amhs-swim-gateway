package vn.asg.converter.reverter.iwxxm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TAFReverterTest {

    private final TAFReverter reverter = new TAFReverter();

    @Test
    public void testRevertTaf_BaseForecast() throws IWXXMParsingException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<iwxxm:TAF xmlns:iwxxm=\"http://icao.int/iwxxm/2023-1\" \n" +
                "           xmlns:gml=\"http://www.opengis.net/gml/3.2\"\n" +
                "           xmlns:aixm=\"http://www.aixm.aero/schema/5.1.1\"\n" +
                "           gml:id=\"taf-vvnb-1\" reportStatus=\"NORMAL\">\n" +
                "    <iwxxm:issueTime>\n" +
                "        <gml:TimeInstant gml:id=\"ti-1\">\n" +
                "            <gml:timePosition>2024-05-06T09:00:00Z</gml:timePosition>\n" +
                "        </gml:TimeInstant>\n" +
                "    </iwxxm:issueTime>\n" +
                "    <iwxxm:aerodrome>\n" +
                "        <aixm:AirportHeliport gml:id=\"vvnb\">\n" +
                "            <aixm:timeSlice>\n" +
                "                <aixm:AirportHeliportTimeSlice gml:id=\"vvnb-ts\">\n" +
                "                    <gml:validTime/>\n" +
                "                    <aixm:interpretation>SNAPSHOT</aixm:interpretation>\n" +
                "                    <aixm:designator>VVNB</aixm:designator>\n" +
                "                </aixm:AirportHeliportTimeSlice>\n" +
                "            </aixm:timeSlice>\n" +
                "        </aixm:AirportHeliport>\n" +
                "    </iwxxm:aerodrome>\n" +
                "    <iwxxm:validPeriod>\n" +
                "        <gml:TimePeriod gml:id=\"tp-1\">\n" +
                "            <gml:beginPosition>2024-05-06T12:00:00Z</gml:beginPosition>\n" +
                "            <gml:endPosition>2024-05-07T12:00:00Z</gml:endPosition>\n" +
                "        </gml:TimePeriod>\n" +
                "    </iwxxm:validPeriod>\n" +
                "    <iwxxm:baseForecast>\n" +
                "        <iwxxm:MeteorologicalAerodromeForecast gml:id=\"fcst-1\">\n" +
                "            <iwxxm:surfaceWind>\n" +
                "                <iwxxm:AerodromeSurfaceWindForecast>\n" +
                "                    <iwxxm:meanWindDirection uom=\"deg\">120</iwxxm:meanWindDirection>\n" +
                "                    <iwxxm:meanWindSpeed uom=\"[kn_i]\">10</iwxxm:meanWindSpeed>\n" +
                "                </iwxxm:AerodromeSurfaceWindForecast>\n" +
                "            </iwxxm:surfaceWind>\n" +
                "            <iwxxm:prevailingVisibility uom=\"m\">9000</iwxxm:prevailingVisibility>\n" +
                "            <iwxxm:cloud>\n" +
                "                <iwxxm:AerodromeCloudForecast gml:id=\"cloud-1\">\n" +
                "                    <iwxxm:layer>\n" +
                "                        <iwxxm:CloudLayer>\n" +
                "                            <iwxxm:amount xlink:href=\"http://codes.wmo.int/49-2/CloudAmountReportedAtAerodrome/BKN\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"/>\n" +
                "                            <iwxxm:base uom=\"[ft_i]\">2000</iwxxm:base>\n" +
                "                        </iwxxm:CloudLayer>\n" +
                "                    </iwxxm:layer>\n" +
                "                </iwxxm:AerodromeCloudForecast>\n" +
                "            </iwxxm:cloud>\n" +
                "        </iwxxm:MeteorologicalAerodromeForecast>\n" +
                "    </iwxxm:baseForecast>\n" +
                "</iwxxm:TAF>";

        String result = reverter.convertToString(xml);
        assertNotNull(result);
        assertTrue(result.contains("TAF VVNB"), "Result should contain TAF VVNB");
        assertTrue(result.contains("060900Z"), "Result should contain issue time");
        assertTrue(result.contains("0612/0712"), "Result should contain validity period");
        assertTrue(result.contains("12010KT"), "Result should contain wind");
        assertTrue(result.contains("9000"), "Result should contain visibility");
        assertTrue(result.contains("BKN020"), "Result should contain clouds");
    }

    @Test
    public void testRevertTaf_ChangeForecast() throws IWXXMParsingException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<iwxxm:TAF xmlns:iwxxm=\"http://icao.int/iwxxm/2023-1\" \n" +
                "           xmlns:gml=\"http://www.opengis.net/gml/3.2\"\n" +
                "           xmlns:aixm=\"http://www.aixm.aero/schema/5.1.1\"\n" +
                "           gml:id=\"taf-vvts-1\" reportStatus=\"NORMAL\">\n" +
                "    <iwxxm:issueTime>\n" +
                "        <gml:TimeInstant gml:id=\"ti-2\">\n" +
                "            <gml:timePosition>2024-05-06T09:00:00Z</gml:timePosition>\n" +
                "        </gml:TimeInstant>\n" +
                "    </iwxxm:issueTime>\n" +
                "    <iwxxm:aerodrome>\n" +
                "        <aixm:AirportHeliport gml:id=\"vvts\">\n" +
                "            <aixm:timeSlice>\n" +
                "                <aixm:AirportHeliportTimeSlice gml:id=\"vvts-ts\">\n" +
                "                    <gml:validTime/>\n" +
                "                    <aixm:interpretation>SNAPSHOT</aixm:interpretation>\n" +
                "                    <aixm:designator>VVTS</aixm:designator>\n" +
                "                </aixm:AirportHeliportTimeSlice>\n" +
                "            </aixm:timeSlice>\n" +
                "        </aixm:AirportHeliport>\n" +
                "    </iwxxm:aerodrome>\n" +
                "    <iwxxm:validPeriod>\n" +
                "        <gml:TimePeriod gml:id=\"tp-2\">\n" +
                "            <gml:beginPosition>2024-05-06T12:00:00Z</gml:beginPosition>\n" +
                "            <gml:endPosition>2024-05-07T12:00:00Z</gml:endPosition>\n" +
                "        </gml:TimePeriod>\n" +
                "    </iwxxm:validPeriod>\n" +
                "    <iwxxm:baseForecast>\n" +
                "        <iwxxm:MeteorologicalAerodromeForecast gml:id=\"fcst-2\">\n" +
                "            <iwxxm:surfaceWind>\n" +
                "                <iwxxm:AerodromeSurfaceWindForecast>\n" +
                "                    <iwxxm:meanWindDirection uom=\"deg\">250</iwxxm:meanWindDirection>\n" +
                "                    <iwxxm:meanWindSpeed uom=\"[kn_i]\">05</iwxxm:meanWindSpeed>\n" +
                "                </iwxxm:AerodromeSurfaceWindForecast>\n" +
                "            </iwxxm:surfaceWind>\n" +
                "            <iwxxm:prevailingVisibility uom=\"m\">9999</iwxxm:prevailingVisibility>\n" +
                "        </iwxxm:MeteorologicalAerodromeForecast>\n" +
                "    </iwxxm:baseForecast>\n" +
                "    <iwxxm:changeForecast>\n" +
                "        <iwxxm:MeteorologicalAerodromeForecast gml:id=\"change-1\" changeIndicator=\"TEMPORARY_FLUCTUATIONS\">\n" +
                "            <iwxxm:phenomenonTime>\n" +
                "                <gml:TimePeriod gml:id=\"tp-c1\">\n" +
                "                    <gml:beginPosition>2024-05-06T15:00:00Z</gml:beginPosition>\n" +
                "                    <gml:endPosition>2024-05-06T18:00:00Z</gml:endPosition>\n" +
                "                </gml:TimePeriod>\n" +
                "            </iwxxm:phenomenonTime>\n" +
                "            <iwxxm:prevailingVisibility uom=\"m\">2000</iwxxm:prevailingVisibility>\n" +
                "            <iwxxm:weather xlink:href=\"http://codes.wmo.int/bufr4/codeflag/0-20-003/TSRA\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"/>\n" +
                "        </iwxxm:MeteorologicalAerodromeForecast>\n" +
                "    </iwxxm:changeForecast>\n" +
                "</iwxxm:TAF>";

        String result = reverter.convertToString(xml);
        assertNotNull(result);
        assertTrue(result.contains("TEMPO 0615/0618"), "Result should contain TEMPO period");
        assertTrue(result.contains("2000"), "Result should contain TEMPO visibility");
        assertTrue(result.contains("TSRA"), "Result should contain TEMPO weather");
    }
}
