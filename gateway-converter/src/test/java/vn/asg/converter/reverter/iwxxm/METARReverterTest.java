package vn.asg.converter.reverter.iwxxm;

import org.junit.jupiter.api.Test;
import vn.asg.converter.iwxxm.METARConverterV3;
import static org.junit.jupiter.api.Assertions.*;

public class METARReverterTest {

    private final METARReverter reverter = new METARReverter();

    @Test
    public void testRevertSeaCondition_State() throws IWXXMParsingException {
        // XML structure matching what METARConverterV3 would produce
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<iwxxm:METAR xmlns:iwxxm=\"http://icao.int/iwxxm/2023-1\" \n" +
                "             xmlns:gml=\"http://www.opengis.net/gml/3.2\"\n" +
                "             xmlns:xlink=\"http://www.w3.org/1999/xlink\"\n" +
                "             xmlns:aixm=\"http://www.aixm.aero/schema/5.1.1\"\n" +
                "             gml:id=\"metar-vvnb-1\">\n" +
                "    <iwxxm:issueTime>\n" +
                "        <gml:TimeInstant gml:id=\"ti-001\">\n" +
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
                "    <iwxxm:observationTime>\n" +
                "        <gml:TimeInstant gml:id=\"ti-002\">\n" +
                "            <gml:timePosition>2024-05-06T09:00:00Z</gml:timePosition>\n" +
                "        </gml:TimeInstant>\n" +
                "    </iwxxm:observationTime>\n" +
                "    <iwxxm:observation>\n" +
                "        <iwxxm:MeteorologicalAerodromeObservation gml:id=\"obs-001\" cloudAndVisibilityOK=\"false\">\n" +
                "            <iwxxm:airTemperature uom=\"degC\">18.0</iwxxm:airTemperature>\n" +
                "            <iwxxm:dewpointTemperature uom=\"degC\">12.0</iwxxm:dewpointTemperature>\n" +
                "            <iwxxm:qnh uom=\"hPa\">1013.0</iwxxm:qnh>\n" +
                "            <iwxxm:seaCondition>\n" +
                "                <iwxxm:AerodromeSeaCondition>\n" +
                "                    <iwxxm:seaSurfaceTemperature uom=\"degC\">18.4</iwxxm:seaSurfaceTemperature>\n" +
                "                    <iwxxm:seaState xlink:href=\"http://codes.wmo.int/common/sea-state/5\"/>\n" +
                "                </iwxxm:AerodromeSeaCondition>\n" +
                "            </iwxxm:seaCondition>\n" +
                "        </iwxxm:MeteorologicalAerodromeObservation>\n" +
                "    </iwxxm:observation>\n" +
                "</iwxxm:METAR>";

        String result = reverter.convertToString(xml);
        assertNotNull(result);
        assertTrue(result.contains("VVNB"), "Result should contain VVNB. Actual: " + result);
        assertTrue(result.contains("W18/S5"), "Result should contain Sea Condition W18/S5. Actual: " + result);
    }

    @Test
    public void testRevertWindShear_AllRunways() throws IWXXMParsingException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<iwxxm:METAR xmlns:iwxxm=\"http://icao.int/iwxxm/2023-1\" \n" +
                "             xmlns:gml=\"http://www.opengis.net/gml/3.2\"\n" +
                "             xmlns:aixm=\"http://www.aixm.aero/schema/5.1.1\"\n" +
                "             gml:id=\"metar-vvts-1\">\n" +
                "    <iwxxm:issueTime>\n" +
                "        <gml:TimeInstant gml:id=\"ti-003\">\n" +
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
                "    <iwxxm:observationTime>\n" +
                "        <gml:TimeInstant gml:id=\"ti-004\">\n" +
                "            <gml:timePosition>2024-05-06T09:00:00Z</gml:timePosition>\n" +
                "        </gml:TimeInstant>\n" +
                "    </iwxxm:observationTime>\n" +
                "    <iwxxm:observation>\n" +
                "        <iwxxm:MeteorologicalAerodromeObservation gml:id=\"obs-003\" cloudAndVisibilityOK=\"false\">\n" +
                "            <iwxxm:airTemperature uom=\"degC\">25.0</iwxxm:airTemperature>\n" +
                "            <iwxxm:dewpointTemperature uom=\"degC\">20.0</iwxxm:dewpointTemperature>\n" +
                "            <iwxxm:qnh uom=\"hPa\">1008.0</iwxxm:qnh>\n" +
                "            <iwxxm:windShear>\n" +
                "                <iwxxm:AerodromeWindShear gml:id=\"ws-001\" allRunways=\"true\"/>\n" +
                "            </iwxxm:windShear>\n" +
                "        </iwxxm:MeteorologicalAerodromeObservation>\n" +
                "    </iwxxm:observation>\n" +
                "</iwxxm:METAR>";

        String result = reverter.convertToString(xml);
        assertNotNull(result);
        assertTrue(result.contains("WS ALL RWY"), "Result should contain WS ALL RWY. Actual: " + result);
    }
}
