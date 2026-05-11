package vn.asg.converter.reverter.iwxxm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SIGMETReverterTest {

    private final SIGMETReverter reverter = new SIGMETReverter();

    @Test
    public void testRevertSigmet_Normal() throws IWXXMParsingException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<iwxxm:SIGMET xmlns:iwxxm=\"http://icao.int/iwxxm/2023-1\" \n" +
                "              xmlns:gml=\"http://www.opengis.net/gml/3.2\"\n" +
                "              xmlns:aixm=\"http://www.aixm.aero/schema/5.1.1\"\n" +
                "              xmlns:xlink=\"http://www.w3.org/1999/xlink\"\n" +
                "              gml:id=\"sigmet-1\" reportStatus=\"NORMAL\">\n" +
                "    <iwxxm:issueTime>\n" +
                "        <gml:TimeInstant gml:id=\"ti-s1\">\n" +
                "            <gml:timePosition>2024-05-06T09:00:00Z</gml:timePosition>\n" +
                "        </gml:TimeInstant>\n" +
                "    </iwxxm:issueTime>\n" +
                "    <iwxxm:issuingAirTrafficServicesUnit>\n" +
                "        <aixm:Unit gml:id=\"unit-1\">\n" +
                "            <aixm:timeSlice>\n" +
                "                <aixm:UnitTimeSlice gml:id=\"unit-ts-1\">\n" +
                "                    <gml:validTime/>\n" +
                "                    <aixm:interpretation>SNAPSHOT</aixm:interpretation>\n" +
                "                    <aixm:designator>VVVV</aixm:designator>\n" +
                "                </aixm:UnitTimeSlice>\n" +
                "            </aixm:timeSlice>\n" +
                "        </aixm:Unit>\n" +
                "    </iwxxm:issuingAirTrafficServicesUnit>\n" +
                "    <iwxxm:originatingMeteorologicalWatchOffice>\n" +
                "        <aixm:Unit gml:id=\"mwo-1\">\n" +
                "            <aixm:timeSlice>\n" +
                "                <aixm:UnitTimeSlice gml:id=\"mwo-ts-1\">\n" +
                "                    <gml:validTime/>\n" +
                "                    <aixm:interpretation>SNAPSHOT</aixm:interpretation>\n" +
                "                    <aixm:designator>VVVV</aixm:designator>\n" +
                "                </aixm:UnitTimeSlice>\n" +
                "            </aixm:timeSlice>\n" +
                "        </aixm:Unit>\n" +
                "    </iwxxm:originatingMeteorologicalWatchOffice>\n" +
                "    <iwxxm:sequenceNumber>1</iwxxm:sequenceNumber>\n" +
                "    <iwxxm:validPeriod>\n" +
                "        <gml:TimePeriod gml:id=\"tp-s1\">\n" +
                "            <gml:beginPosition>2024-05-06T09:00:00Z</gml:beginPosition>\n" +
                "            <gml:endPosition>2024-05-06T13:00:00Z</gml:endPosition>\n" +
                "        </gml:TimePeriod>\n" +
                "    </iwxxm:validPeriod>\n" +
                "    <iwxxm:issuingAirTrafficServicesRegion>\n" +
                "        <aixm:Airspace gml:id=\"as-1\">\n" +
                "            <aixm:timeSlice>\n" +
                "                <aixm:AirspaceTimeSlice gml:id=\"as-ts-1\">\n" +
                "                    <gml:validTime/>\n" +
                "                    <aixm:interpretation>SNAPSHOT</aixm:interpretation>\n" +
                "                    <aixm:type>FIR</aixm:type>\n" +
                "                    <aixm:designator>VVVV</aixm:designator>\n" +
                "                    <aixm:name>HANOI</aixm:name>\n" +
                "                </aixm:AirspaceTimeSlice>\n" +
                "            </aixm:timeSlice>\n" +
                "        </aixm:Airspace>\n" +
                "    </iwxxm:issuingAirTrafficServicesRegion>\n" +
                "    <iwxxm:phenomenon xlink:href=\"http://codes.wmo.int/49-2/SigWxPhenomena/TSGR\"/>\n" +
                "    <iwxxm:analysisCollection>\n" +
                "        <iwxxm:analysisAndForecastPositionAnalysis gml:id=\"apa-1\">\n" +
                "             <iwxxm:analysis>\n" +
                "                 <iwxxm:SIGMETEvolvingConditionCollection gml:id=\"ecc-1\" timeIndicator=\"OBSERVATION\">\n" +
                "                     <iwxxm:phenomenonTime>\n" +
                "                         <gml:TimeInstant gml:id=\"ti-s2\">\n" +
                "                             <gml:timePosition>2024-05-06T08:50:00Z</gml:timePosition>\n" +
                "                         </gml:TimeInstant>\n" +
                "                     </iwxxm:phenomenonTime>\n" +
                "                     <iwxxm:member>\n" +
                "                         <iwxxm:SIGMETEvolvingCondition gml:id=\"ec-1\" intensityChange=\"NO_CHANGE\">\n" +
                "                             <iwxxm:geometry>\n" +
                "                                 <aixm:AirspaceVolume gml:id=\"av-1\">\n" +
                "                                     <aixm:upperLimit uom=\"FL\">340</aixm:upperLimit>\n" +
                "                                     <aixm:horizontalProjection>\n" +
                "                                         <aixm:Surface gml:id=\"s-1\">\n" +
                "                                             <gml:patches>\n" +
                "                                                 <gml:PolygonPatch>\n" +
                "                                                     <gml:exterior>\n" +
                "                                                         <gml:LinearRing>\n" +
                "                                                             <gml:posList>21.0 105.0 21.0 106.0 20.0 106.0 20.0 105.0 21.0 105.0</gml:posList>\n" +
                "                                                         </gml:LinearRing>\n" +
                "                                                     </gml:exterior>\n" +
                "                                                 </gml:PolygonPatch>\n" +
                "                                             </gml:patches>\n" +
                "                                         </aixm:Surface>\n" +
                "                                     </aixm:horizontalProjection>\n" +
                "                                 </aixm:AirspaceVolume>\n" +
                "                             </iwxxm:geometry>\n" +
                "                         </iwxxm:SIGMETEvolvingCondition>\n" +
                "                     </iwxxm:member>\n" +
                "                 </iwxxm:SIGMETEvolvingConditionCollection>\n" +
                "             </iwxxm:analysis>\n" +
                "        </iwxxm:analysisAndForecastPositionAnalysis>\n" +
                "    </iwxxm:analysisCollection>\n" +
                "</iwxxm:SIGMET>";

        String result = reverter.convertToString(xml);
        assertNotNull(result);
        assertTrue(result.contains("VVVV SIGMET 1"), "Result should contain SIGMET header");
        assertTrue(result.contains("VALID 060900/061300"), "Result should contain validity period");
        assertTrue(result.contains("TSGR"), "Result should contain phenomenon");
        assertTrue(result.contains("OBS AT 0850Z"), "Result should contain observation time");
        assertTrue(result.contains("TOP FL340"), "Result should contain vertical level");
        String normalizedResult = result.replace("\n", " ").replace("\r", "");
        assertTrue(normalizedResult.contains("WI N2100 E10500 - N2100 E10600 - N2000 E10600 - N2000 E10500 - N2100 E10500"), "Result should contain coordinates");
    }
}
