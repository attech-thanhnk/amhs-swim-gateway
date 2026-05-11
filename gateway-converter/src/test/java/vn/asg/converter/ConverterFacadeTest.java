package vn.asg.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.asg.converter.core.ConversionResult;

import static org.junit.jupiter.api.Assertions.*;

public class ConverterFacadeTest {

    private ConverterFacade converter;

    @BeforeEach
    public void setUp() throws Exception {
        converter = new ConverterFacade();

        // Mock App singleton for testing
        vn.asg.converter.config.App mockApp = new vn.asg.converter.config.App();
        mockApp.getProperties().add(new vn.asg.converter.config.Property("Interpretation", "BASELINE"));
        mockApp.getProperties().add(new vn.asg.converter.config.Property("CentreName", "VATM"));
        mockApp.getProperties().add(new vn.asg.converter.config.Property("CentreIcaoDesignator", "VVNB"));
        mockApp.getProperties().add(new vn.asg.converter.config.Property("PermissibleUsage", "NON_OPERATIONAL"));
        mockApp.getProperties().add(new vn.asg.converter.config.Property("PermissibleUsageReason", "TEST"));
        mockApp.getProperties().add(new vn.asg.converter.config.Property("PermissibleUsageSupplementary", "TEST"));
        
        java.lang.reflect.Field instanceField = vn.asg.converter.config.App.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, mockApp);

        // Mock Airports singleton for testing
        vn.asg.converter.config.Airports mockAirports = new vn.asg.converter.config.Airports();
        vn.asg.converter.config.Airport airport = new vn.asg.converter.config.Airport();
        airport.setIcaoCode("VVNB");
        airport.setLocationIndicator("VVNB");
        airport.setName("TEST AIRPORT");
        mockAirports.getAirports().add(airport);
        
        java.lang.reflect.Field airportsInstanceField = vn.asg.converter.config.Airports.class.getDeclaredField("instance");
        airportsInstanceField.setAccessible(true);
        airportsInstanceField.set(null, mockAirports);
    }

    @Test
    public void testConvertMetar() {
        String tac = "METAR VVNB 291200Z 09008KT 9999 FEW020 28/24 Q1010=";
        ConversionResult result = converter.convert(tac);
        
        assertTrue(result.isSuccess(), "Conversion should succeed");
        assertEquals("IWXXM", result.getOutputType(), "Output type should be IWXXM");
        assertNotNull(result.getXml(), "XML content should not be null");
        assertTrue(result.getXml().contains("VVNB"), "XML should contain ICAO code");
    }

    @Test
    public void testConvertFpl() {
        String tac = "(FPL-HVN123-IS\n-A321/M-SDFG/C\n-VVNB0100\n-N0450F330 DCT\n-VVTS0200\n-0)";
        ConversionResult result = converter.convert(tac);
        
        assertTrue(result.isSuccess(), "Conversion should succeed");
        assertEquals("FIXM", result.getOutputType(), "Output type should be FIXM");
        assertNotNull(result.getXml(), "XML content should not be null");
        assertTrue(result.getXml().contains("HVN123"), "XML should contain callsign");
    }

    @Test
    public void testConvertNotam() {
        String tac = "(A0123/24 NOTAMN\nQ) VVVV/QXXXX/IV/NBO/A/000/999/2101N10548E005\nA) VVNB B) 2401010000 C) PERM\nE) TEST NOTAM)";
        ConversionResult result = converter.convert(tac);
        
        assertTrue(result.isSuccess(), "Conversion should succeed");
        assertEquals("AIXM", result.getOutputType(), "Output type should be AIXM");
        assertNotNull(result.getXml(), "XML content should not be null");
    }

    @Test
    public void testConvertTaf() {
        String tac = "TAF VVTS 071100Z 0712/0818 24010KT 9999 SCT020\n" +
                     "TEMPO 0714/0718 3000 TSRA BKN015CB\n" +
                     "BECMG 0800/0802 18005KT=";
        ConversionResult result = converter.convert(tac);

        assertTrue(result.isSuccess(), "TAF conversion should succeed");
        assertEquals("IWXXM", result.getOutputType(), "Output type should be IWXXM");
        assertNotNull(result.getXml(), "XML content should not be null");
        assertTrue(result.getXml().contains("VVTS"), "XML should contain aerodrome");
        assertTrue(result.getXml().contains("baseForecast"), "XML should contain base forecast");
        assertTrue(result.getXml().contains("changeForecast"), "XML should contain change forecast");
    }

    @Test
    public void testConvertEmpty() {
        ConversionResult result = converter.convert("   ");
        assertFalse(result.isSuccess(), "Conversion should fail for empty input");
        assertNotNull(result.getErrorMessage(), "Should provide error message");
    }
}
