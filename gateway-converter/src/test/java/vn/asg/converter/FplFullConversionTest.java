package vn.asg.converter;

import org.junit.jupiter.api.Test;
import vn.asg.converter.builder.FixmBuilder;
import vn.asg.converter.model.FplMessage;
import vn.asg.converter.parser.FplParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test đầy đủ FPL parser và FIXM builder với bản tin thực tế từ database.
 */
public class FplFullConversionTest {

    private final FplParser parser = new FplParser();
    private final FixmBuilder builder = new FixmBuilder();

    @Test
    public void testRealFplFromDatabase() throws Exception {
        // FPL thực tế từ database (msgid 19)
        String fplText = "(FPL-VJC123-IS\n" +
                "-A321/M-SDE2E3FGHIRWY/LB1\n" +
                "-VVTS0800\n" +
                "-N0450F350 DCT VVK DCT DONDA Y1 ENRIN\n" +
                "-VVDN0105 VVCR\n" +
                "-PBN/A1B1C1D1O1S2 DOF/260507 REG/VN-A123 EET/VVTS0010 VVDN0105\n" +
                ")";

        // Parse FPL
        FplMessage msg = parser.parse(fplText);
        assertNotNull(msg, "FplMessage should not be null");

        // Verify basic fields
        assertEquals("VJC123", msg.getAircraftId());
        assertEquals("I", msg.getFlightRules());
        assertEquals("S", msg.getFlightType());
        assertEquals("A321", msg.getAircraftType());
        assertEquals("M", msg.getWakeTurbulence());
        assertEquals("SDE2E3FGHIRWY/LB1", msg.getEquipment());
        assertEquals("VVTS", msg.getDepartureIcao());
        assertEquals("0800", msg.getDepartureTime());
        assertEquals("N0450", msg.getCruisingSpeed());
        assertEquals("F350", msg.getCruisingLevel());
        assertEquals("DCT VVK DCT DONDA Y1 ENRIN", msg.getRoute());
        assertEquals("VVDN", msg.getDestinationIcao());
        assertEquals("0105", msg.getTotalEet());
        assertEquals("VVCR", msg.getAltDestination1());

        // Verify Field 18 parsed items
        assertEquals("260507", msg.getDof());
        assertEquals("VN-A123", msg.getRegistration());
        assertEquals("A1B1C1D1O1S2", msg.getPbn());
        assertEquals("VVTS0010 VVDN0105", msg.getEet());

        // Build FIXM
        String fixmXml = builder.buildFpl(msg);
        assertNotNull(fixmXml, "FIXM XML should not be null");

        // Verify FIXM XML contains all critical fields
        System.out.println("\n=== Verifying FIXM XML Output ===");

        // Flight Identification
        assertTrue(fixmXml.contains("<aircraftIdentification>VJC123</aircraftIdentification>"),
            "Missing aircraft ID");

        // Flight Rules (now in RouteInformation per FIXM 4.2)
        assertTrue(fixmXml.contains("<flightRulesCategory>I</flightRulesCategory>"),
            "Missing flight rules category");

        // Type of Flight
        assertTrue(fixmXml.contains("<flightType>S</flightType>"),
            "Missing flight type");

        // Aircraft
        assertTrue(fixmXml.contains("<icaoAircraftTypeDesignator>A321</icaoAircraftTypeDesignator>"),
            "Missing aircraft type");
        assertTrue(fixmXml.contains("<wakeTurbulence>M</wakeTurbulence>"),
            "Missing wake turbulence");
        assertTrue(fixmXml.contains("VN-A123"),
            "Missing registration");

        // Departure & Arrival
        assertTrue(fixmXml.contains("VVTS"),
            "Missing departure aerodrome");
        assertTrue(fixmXml.contains("VVDN"),
            "Missing destination aerodrome");
        assertTrue(fixmXml.contains("2026-05-07T08:00"),
            "Missing or incorrect EOBT (should use DOF)");

        // Route with Speed & Level (FIXM 4.2 format)
        assertTrue(fixmXml.contains("DCT VVK DCT DONDA Y1 ENRIN"),
            "Missing route text");
        assertTrue(fixmXml.contains("<uom>KT</uom>") && fixmXml.contains("<value>450"),
            "Missing or incorrect cruising speed");
        assertTrue(fixmXml.contains("FL") && fixmXml.contains("350"),
            "Missing or incorrect cruising level");

        // Alternate
        assertTrue(fixmXml.contains("VVCR"),
            "Missing alternate aerodrome");

        // EET as Duration
        assertTrue(fixmXml.contains("PT1H5M") || fixmXml.contains("P0Y0M0DT1H5M"),
            "Missing or incorrect EET duration");

        // Equipment codes
        assertTrue(fixmXml.contains("SDE2E3FGHIRWY"),
            "Missing navigation/communication equipment codes");
        assertTrue(fixmXml.contains("LB1"),
            "Missing surveillance codes");

        // PBN capabilities
        assertTrue(fixmXml.contains("PBN/A1B1C1D1O1S2") || fixmXml.contains("A1B1C1D1O1S2"),
            "Missing PBN capabilities");

        System.out.println("=== PARSED FPL MESSAGE ===");
        System.out.println("Aircraft ID: " + msg.getAircraftId());
        System.out.println("Flight Rules: " + msg.getFlightRules());
        System.out.println("Flight Type: " + msg.getFlightType());
        System.out.println("Equipment: " + msg.getEquipment());
        System.out.println("Speed: " + msg.getCruisingSpeed());
        System.out.println("Level: " + msg.getCruisingLevel());
        System.out.println("DOF: " + msg.getDof());
        System.out.println("REG: " + msg.getRegistration());
        System.out.println("PBN: " + msg.getPbn());
        System.out.println("EET: " + msg.getEet());
        System.out.println("\n=== FIXM XML OUTPUT ===");
        System.out.println(fixmXml);
    }

    @Test
    public void testField18ParsingIndividualItems() throws Exception {
        String fplText = "(FPL-TEST123-IS-A320/M-S/C-VVNB0900-N0460F330 DCT-VVTS0100-PBN/A1 DOF/260507 REG/ABC123 SEL/ABCD OPR/TESTAIR STS/MEDEVAC RMK/TEST FLIGHT)";

        FplMessage msg = parser.parse(fplText);
        assertNotNull(msg);

        assertEquals("260507", msg.getDof());
        assertEquals("ABC123", msg.getRegistration());
        assertEquals("A1", msg.getPbn());
        assertEquals("ABCD", msg.getSelcal());
        assertEquals("TESTAIR", msg.getOperator());
        assertEquals("MEDEVAC", msg.getSts());
        assertEquals("TEST FLIGHT", msg.getRemarks());
    }

    @Test
    public void testDofDateConversion() throws Exception {
        String fplText = "(FPL-TEST-IS-A320/M-S/C-VVNB0930-N0450F330 DCT-VVTS0100-DOF/260507)";

        FplMessage msg = parser.parse(fplText);
        String fixmXml = builder.buildFpl(msg);

        // Verify DOF is used for EOBT
        assertTrue(fixmXml.contains("2026-05-07T09:30:00"), "Should use DOF 260507 for date");
    }

    @Test
    public void testCruisingSpeedFormats() {
        // Test Knots
        FplMessage msg1 = new FplMessage();
        msg1.setCruisingSpeed("N0450");
        String xml1 = builder.buildFpl(msg1);
        assertTrue(xml1.contains("<value>450.0</value>") && xml1.contains("<uom>KT</uom>"));

        // Test Km/h
        FplMessage msg2 = new FplMessage();
        msg2.setCruisingSpeed("K0720");
        String xml2 = builder.buildFpl(msg2);
        assertTrue(xml2.contains("<value>720.0</value>") && xml2.contains("<uom>KM_H</uom>"));

        // Test Mach
        FplMessage msg3 = new FplMessage();
        msg3.setCruisingSpeed("M082");
        String xml3 = builder.buildFpl(msg3);
        assertTrue(xml3.contains("<value>0.82</value>") && xml3.contains("<uom>MACH</uom>"));
    }

    @Test
    public void testCruisingLevelFormats() {
        // Test Flight Level
        FplMessage msg1 = new FplMessage();
        msg1.setCruisingLevel("F350");
        String xml1 = builder.buildFpl(msg1);
        assertTrue(xml1.contains("<value>350.0</value>") && xml1.contains("<uom>FL</uom>"));

        // Test Altitude
        FplMessage msg2 = new FplMessage();
        msg2.setCruisingLevel("A100");
        String xml2 = builder.buildFpl(msg2);
        assertTrue(xml2.contains("<value>10000.0</value>") && xml2.contains("<uom>FT</uom>"));

        // Test Metric
        FplMessage msg3 = new FplMessage();
        msg3.setCruisingLevel("S1300");
        String xml3 = builder.buildFpl(msg3);
        assertTrue(xml3.contains("<value>13000.0</value>") && xml3.contains("<uom>M</uom>"));
    }
}
