/*
 */
package vn.asg.converter.reverter.entity;

/**
 *
 * @author ThanhNk
 */
public class SPECI extends METAR {

    public SPECI() {
    }

//    public SPECI(SPECIType speciType) throws IWXXMParsingException {
//        super.parse(speciType);
//    }

//    public void parse(MeteorologicalAerodromeObservationReportType speciType) {
//        
//    }

    @Override
    public String getTypeofReport() {
        return "SPECI";
    }
    
    @Override
    public String toString() {
        return super.toString();
    }
}

