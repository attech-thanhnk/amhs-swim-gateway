package vn.asg.converter.reverter.aixm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NotamReverterTest {

    private final NotamReverter reverter = new NotamReverter();

    @Test
    public void testRevertNotam_Normal() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<aixm:NOTAM xmlns:aixm=\"urn:aero:aixm:5.1.1\" \n" +
                "            xmlns:gml=\"http://www.opengis.net/gml/3.2\" \n" +
                "            gml:id=\"n-1\">\n" +
                "    <aixm:name>A1234/24</aixm:name>\n" +
                "    <aixm:encoding>NOTAMN</aixm:encoding>\n" +
                "    <aixm:purpose>NBO</aixm:purpose>\n" +
                "    <aixm:note>RUNWAY 11/29 CLOSED DUE TO WIP.</aixm:note>\n" +
                "    <aixm:validTime>\n" +
                "        <gml:TimePeriod gml:id=\"tp-1\">\n" +
                "            <gml:beginPosition>2024-05-06T09:00:00Z</gml:beginPosition>\n" +
                "            <gml:endPosition>2024-05-06T12:00:00Z</gml:endPosition>\n" +
                "        </gml:TimePeriod>\n" +
                "    </aixm:validTime>\n" +
                "</aixm:NOTAM>";

        String result = reverter.revert(xml);
        assertNotNull(result);
        assertTrue(result.contains("A1234/24 NOTAMN"), "Should contain ID and type");
        assertTrue(result.contains("B) 2405060900"), "Should contain begin time");
        assertTrue(result.contains("C) 2405061200"), "Should contain end time");
        assertTrue(result.contains("E) RUNWAY 11/29 CLOSED"), "Should contain note");
    }
}
