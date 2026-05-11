/*
 */
package vn.asg.converter.config;

import java.util.HashSet;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author ThanhNk
 */
@XmlRootElement(name = "Airport")
@XmlAccessorType(XmlAccessType.NONE)
public class Airport {

    @XmlAttribute(name = "icaoDesignator")
    private String icaoCode;

    @XmlAttribute(name = "locationIndicator")
    private String locationIndicator;

    @XmlAttribute(name = "name")
    private String name;

    @XmlElement(name = "ElevatedPoint")
    private ElevatedPoint elevatedPoint;

    @XmlElement(name = "Runway")
    private java.util.List<Runway> runways;

    public Airport() {
        this.runways = new java.util.ArrayList<>();
    }
    

    //<editor-fold defaultstate="collapsed" desc=" Class properties ">
    /**
     * @return the icaoCode
     */
    public String getIcaoCode() {
        return icaoCode;
    }

    /**
     * @param icaoCode the icaoCode to set
     */
    public void setIcaoCode(String icaoCode) {
        this.icaoCode = icaoCode;
    }

    /**
     * @return the city
     */
    public String getName() {
        return name;
    }

    /**
     * @param city the city to set
     */
    public void setName(String city) {
        this.name = city;
    }

    /**
     * @return the elevatedPoint
     */
    public ElevatedPoint getElevatedPoint() {
        return elevatedPoint;
    }

    /**
     * @param elevatedPoint the elevatedPoint to set
     */
    public void setElevatedPoint(ElevatedPoint elevatedPoint) {
        this.elevatedPoint = elevatedPoint;
    }

    /**
     * @return the locationIndicator
     */
    public String getLocationIndicator() {
        return locationIndicator;
    }

    /**
     * @param locationIndicator the locationIndicator to set
     */
    public void setLocationIndicator(String locationIndicator) {
        this.locationIndicator = locationIndicator;
    }

    /**
     * @return the runways
     */
    public java.util.List<Runway> getRunways() {
        return runways;
    }

    /**
     * @param runways the runways to set
     */
    public void setRunways(java.util.List<Runway> runways) {
        this.runways = runways;
    }

    public Runway getRunways(String code) {
        for (Runway runway : this.runways) {
            if (runway.getCode().equalsIgnoreCase(code)) {
                return runway;
            }
        }

        return null;
    }

    //</editor-fold>
    
    
    @Override
    public int hashCode() {
        return this.icaoCode != null ? this.icaoCode.toLowerCase().hashCode() : 0;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Airport)) {
            return false;
        }
        Airport other = (Airport) object;
        if (this.icaoCode == null || other.icaoCode == null) {
            return this.icaoCode == other.icaoCode;
        }
        return this.icaoCode.equalsIgnoreCase(other.icaoCode);
    }

    @Override
    public String toString() {
        return "AirportDefine [ icaoCode=" + this.icaoCode + " ]";
    }

}

