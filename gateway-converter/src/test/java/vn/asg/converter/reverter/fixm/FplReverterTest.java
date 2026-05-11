package vn.asg.converter.reverter.fixm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FplReverterTest {

    private final FplReverter reverter = new FplReverter();

    @Test
    public void testRevertFpl_Normal() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<fixm:Flight xmlns:fixm=\"http://www.fixm.aero/flight/4.2\" \n" +
                "             xmlns:base=\"http://www.fixm.aero/base/4.2\" \n" +
                "             xmlns:gml=\"http://www.opengis.net/gml/3.2\">\n" +
                "    <fixm:flightIdentification>\n" +
                "        <fixm:aircraftIdentification>HVN123</fixm:aircraftIdentification>\n" +
                "    </fixm:flightIdentification>\n" +
                "    <fixm:aircraft>\n" +
                "        <fixm:aircraftType>\n" +
                "            <base:icaoModelIdentifier>A321</base:icaoModelIdentifier>\n" +
                "        </fixm:aircraftType>\n" +
                "        <fixm:wakeTurbulence>M</fixm:wakeTurbulence>\n" +
                "    </fixm:aircraft>\n" +
                "    <fixm:departure>\n" +
                "        <fixm:aerodrome>\n" +
                "            <base:locationIndicator>VVNB</base:locationIndicator>\n" +
                "        </fixm:aerodrome>\n" +
                "        <fixm:estimatedOffBlockTime>\n" +
                "            <base:time>2024-05-06T09:00:00Z</base:time>\n" +
                "        </fixm:estimatedOffBlockTime>\n" +
                "    </fixm:departure>\n" +
                "    <fixm:enRoute>\n" +
                "        <fixm:routeTrajectoryGroup>\n" +
                "            <fixm:routeTrajectory>\n" +
                "                <fixm:routeText>DCT</fixm:routeText>\n" +
                "            </fixm:routeTrajectory>\n" +
                "        </fixm:routeTrajectoryGroup>\n" +
                "    </fixm:enRoute>\n" +
                "    <fixm:destination>\n" +
                "        <fixm:aerodrome>\n" +
                "            <base:locationIndicator>VVTS</base:locationIndicator>\n" +
                "        </fixm:aerodrome>\n" +
                "    </fixm:destination>\n" +
                "</fixm:Flight>";

        String result = reverter.revert(xml);
        assertNotNull(result);
        assertTrue(result.startsWith("(FPL-HVN123"), "Should start with FPL header");
        assertTrue(result.contains("A321/M"), "Should contain aircraft type/wake");
        assertTrue(result.contains("VVNB"), "Should contain departure");
        assertTrue(result.contains("VVTS"), "Should contain destination");
    }
}
