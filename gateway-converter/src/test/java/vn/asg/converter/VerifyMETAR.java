package vn.asg.converter;

import vn.asg.converter.core.ConversionResult;

public class VerifyMETAR {
    public static void main(String[] args) throws Exception {
        // Setup environment
        vn.asg.converter.config.App mockApp = new vn.asg.converter.config.App();
        mockApp.getProperties().add(new vn.asg.converter.config.Property("AddTacContent", "true"));
        mockApp.getProperties().add(new vn.asg.converter.config.Property("CentreName", "VATM"));
        mockApp.getProperties().add(new vn.asg.converter.config.Property("CentreDesignator", "VVNB"));
        mockApp.getProperties().add(new vn.asg.converter.config.Property("PermissibleUsage", "NON_OPERATIONAL"));
        mockApp.getProperties().add(new vn.asg.converter.config.Property("PermissibleUsageReason", "TEST"));
        mockApp.getProperties().add(new vn.asg.converter.config.Property("PermissibleUsageSupplementary", "TEST"));
        
        java.lang.reflect.Field instanceField = vn.asg.converter.config.App.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, mockApp);

        vn.asg.converter.config.Airports mockAirports = new vn.asg.converter.config.Airports();
        vn.asg.converter.config.Airport airport = new vn.asg.converter.config.Airport();
        airport.setIcaoCode("VVNB");
        airport.setLocationIndicator("VVNB");
        airport.setName("NOI BAI AIRPORT");
        mockAirports.getAirports().add(airport);
        
        java.lang.reflect.Field airportsInstanceField = vn.asg.converter.config.Airports.class.getDeclaredField("instance");
        airportsInstanceField.setAccessible(true);
        airportsInstanceField.set(null, mockAirports);

        // Test Conversion
        ConverterFacade converter = new ConverterFacade();
        String tac = "METAR VVNB 061000Z 09008G18KT 5000 R11L/1200U TSRA FEW020CB BKN030 28/24 Q1010 NOSIG=";
        
        System.out.println("Converting TAC: " + tac);
        ConversionResult result = converter.convert(tac);
        
        if (result.isSuccess()) {
            System.out.println("SUCCESS!");
            System.out.println("XML Output:\n");
            System.out.println(result.getXml());
        } else {
            System.out.println("FAILED!");
            System.out.println("Error: " + result.getErrorMessage());
        }
    }
}
