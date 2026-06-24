package vn.asg.converter;

import org.junit.jupiter.api.Test;
import vn.asg.converter.core.OutputFormat;
import vn.asg.converter.core.ConversionResult;
import static org.junit.jupiter.api.Assertions.*;

public class IcaoSyntaxTest {

    private final ConverterFacade facade = new ConverterFacade();

    @Test
    public void testFplRouteCleaning() {
        String json = """
            {
              "messageType": "FPL",
              "aircraftId": "HVN123",
              "departureIcao": "VVTS",
              "eobt": "1600",
              "aircraftType": "A321",
              "wakeTurbulence": "M",
              "route": "DCT PANTO DCT NOB DCT VVNB0100",
              "destinationIcao": "VVNB",
              "totalEet": "0100"
            }
            """;

        ConversionResult result = facade.convert(json, "FPL", OutputFormat.TEXT);
        assertTrue(result.isSuccess(), "Conversion should succeed");
        String tac = result.getPayload();
        assertFalse(tac.contains("VVNB0100-VVNB0100"));
        assertTrue(tac.contains("DCT PANTO DCT NOB-VVNB0100"));
    }

    @Test
    public void testField18Reconstruction() {
        String json = """
            {
              "messageType": "FPL",
              "aircraftId": "HVN123",
              "departureIcao": "VVTS",
              "destinationIcao": "VVNB",
              "eobt": "1600",
              "aircraftType": "A321",
              "pbn": "A1B1C1D1",
              "registration": "VNA678",
              "dof": "240513"
            }
            """;
        ConversionResult result = facade.convert(json, "FPL", OutputFormat.TEXT);
        assertTrue(result.isSuccess(), "Conversion failed: " + result.getErrorMessage());
        String tac = result.getPayload();
        assertTrue(tac.contains("PBN/A1B1C1D1"));
        assertTrue(tac.contains("REG/VNA678"));
        assertTrue(tac.contains("DOF/240513"));
    }

    @Test
    public void testFplValidationFailure() {
        String json = """
            {
              "messageType": "FPL",
              "departureIcao": "VVTS",
              "destinationIcao": "VVNB"
            }
            """;
        ConversionResult result = facade.convert(json, "FPL", OutputFormat.TEXT);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Aircraft Identification"));
    }

    @Test
    public void testSnowtamRendering() {
        String json = """
            {
              "messageType": "SNOWTAM",
              "stationIcao": "VVNB",
              "observationTime": "05130800",
              "aerodrome": "VVNB",
              "runwayDesignator": "11L",
              "clearedLength": "3200",
              "clearedWidth": "45"
            }
            """;
        ConversionResult result = facade.convert(json, "SNOWTAM", OutputFormat.TEXT);
        assertTrue(result.isSuccess());
        String tac = result.getPayload();
        assertTrue(tac.contains("A) VVNB"));
        assertTrue(tac.contains("B) 05130800"));
    }

    @Test
    public void testMetarMessage() {
        String json = """
            {
              "messageType": "METAR",
              "stationIcao": "VVTS",
              "observationTime": "131030Z",
              "wind": "24005KT",
              "visibility": "9999",
              "weather": ["-RA", "TS"],
              "cloud": "FEW020",
              "temperature": "32/25",
              "qnh": "Q1008"
            }
            """;
        ConversionResult result = facade.convert(json, "METAR", OutputFormat.TEXT);
        assertTrue(result.isSuccess());
        assertTrue(result.getPayload().contains("METAR VVTS 131030Z 24005KT 9999 -RA TS FEW020 32/25 Q1008="));
    }

    @Test
    public void testNotamMessage() {
        String json = """
            {
              "messageType": "NOTAMN",
              "notamId": "A0123/24",
              "fir": "VVHM",
              "notamCode": "QFAAH",
              "traffic": "IV",
              "purpose": "NBO",
              "scope": "A",
              "location": "VVNB",
              "validFrom": "2024-05-13T08:00:00Z",
              "validUntil": "2024-05-13T10:00:00Z",
              "text": "RWY 11R/29L CLSD DUE TO MAINT"
            }
            """;
        ConversionResult result = facade.convert(json, "NOTAM", OutputFormat.TEXT);
        assertTrue(result.isSuccess(), "NOTAM conversion failed: " + result.getErrorMessage());
        String tac = result.getPayload();
        // NOTAM output starts with (A0123/24 NOTAMN
        assertTrue(tac.contains("A0123/24 NOTAMN"), "NOTAM header should match");
        assertTrue(tac.contains("Q) VVHM/QFAAH"), "NOTAM Q-line should match");
    }

    @Test
    public void testSigmetMessage() {
        String json = """
            {
              "messageType": "SIGMET",
              "fir": "VVHM",
              "sequenceNumber": "A01",
              "validityPeriod": "130800/131200",
              "phenomenon": "TS",
              "location": "WI N10 E105 - N15 E110",
              "intensity": "NC"
            }
            """;
        ConversionResult result = facade.convert(json, "SIGMET", OutputFormat.TEXT);
        assertTrue(result.isSuccess());
        assertTrue(result.getPayload().endsWith("="));
    }

    @Test
    public void testCoordinationMessage() {
        String json = """
            {
              "messageType": "EST",
              "aircraftId": "HVN123",
              "departureIcao": "VVTS",
              "destinationIcao": "VVNB",
              "eobt": "1600",
              "boundaryPoint": "PANTO",
              "boundaryTime": "1630",
              "clearedLevel": "F350"
            }
            """;
        ConversionResult result = facade.convert(json, "EST", OutputFormat.TEXT);
        assertTrue(result.isSuccess(), "EST conversion failed: " + result.getErrorMessage());
        String tac = result.getPayload();
        assertTrue(tac.contains("-HVN123-VVTS1600-PANTO/1630F350-VVNB"), "EST format should be correct");
    }

    @Test
    public void testAirepMessage() {
        String json = """
            {
              "messageType": "ARP",
              "aircraftId": "HVN123",
              "position": "PANTO",
              "time": "1630",
              "level": "F350",
              "meteorologicalInfo": "MS52 270/85KT"
            }
            """;
        ConversionResult result = facade.convert(json, "ARP", OutputFormat.TEXT);
        assertTrue(result.isSuccess());
        assertTrue(result.getPayload().contains("ARP HVN123 PANTO 1630 F350"));
    }
}
