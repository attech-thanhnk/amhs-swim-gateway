/*
 */
package vn.asg.converter.config;

import vn.asg.converter.common.XmlUtil;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author ThanhNk
 */
@XmlRootElement(name = "Airports")
@XmlAccessorType(XmlAccessType.NONE)
public class Airports {

    private static final String file = "resources/airports.xml";
    
    private static Airports instance;
    
    @XmlElement(name = "Airport")
    private List<Airport> airports;

    public Airports() {
        airports = new ArrayList<>();
    }
    
    public static void load() throws JAXBException, FileNotFoundException {
        instance = (Airports) XmlUtil.deserialize(file, Airports.class);
    }
    
    public static Airports getInstance() {
        return instance;
    }

    public Airport getAirportDefine(String icaoDesignator) {
        for (Airport airportDefine : this.airports) {
            if (!airportDefine.getIcaoCode().equalsIgnoreCase(icaoDesignator)) {
                continue;
            }
            return airportDefine;
        }

        return null;
    }

    /**
     * @return the airports
     */
    public List<Airport> getAirports() {
        return airports;
    }

    /**
     * @param airports the airports to set
     */
    public void setAirports(List<Airport> airports) {
        this.airports = airports;
    }
    
    public void setAirports(Airport airports) {
        this.airports.add(airports);
    }
    
    public static void main(String [] args) throws FileNotFoundException, JAXBException {
        Airports airport = new Airports();
        Airport airportDefine = new Airport();
        airportDefine.setIcaoCode("VVNB");
        airportDefine.setLocationIndicator("VVNB");
        airportDefine.setName("HANOI INTERNATIONAL AIRPORT");
//        airportDefine.setInterpretation("BASELINE");
//        airportDefine.setType("INTERNATIONAL");
        
        ElevatedPoint elevatedPoint = new ElevatedPoint();
        elevatedPoint.getAxisLabel().addAll(Arrays.asList("Lat", "Long"));
        elevatedPoint.getPosition().addAll(Arrays.asList(21.1318, 105.4820));
        elevatedPoint.setElevationUOM("M");
        elevatedPoint.setElevationValue("12.3");
        elevatedPoint.setSrsDimension(new BigInteger("2"));
        elevatedPoint.setSrsName("http://www.opengis.net/def/crs/EPSG/0/4756");
        elevatedPoint.setVerticalDatum("EGM_96");
        
        airportDefine.setElevatedPoint(elevatedPoint);
        airport.setAirports(airportDefine);
        XmlUtil.serialize("airports.xml", airport);
        
        Airports airportss = (Airports) XmlUtil.deserialize("airports.xml", Airports.class);
        
    }

}

