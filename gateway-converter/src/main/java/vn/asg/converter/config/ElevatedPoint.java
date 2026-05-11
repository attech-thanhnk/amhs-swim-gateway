/*
 */
package vn.asg.converter.config;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author ThanhNk
 */
@XmlRootElement(name = "ElevatedPoint")
@XmlAccessorType(XmlAccessType.NONE)
public class ElevatedPoint {

   
    @XmlElement(name = "srsDimension")
    private BigInteger srsDimension;

    @XmlElement(name = "srsName")
    private String srsName;

    @XmlElement(name = "axisLabel")
    private List<String> axisLabel;

    private List<Double> position;

    @XmlElement(name = "elevationUOM")
    private String elevationUOM;

    @XmlElement(name = "elevationValue")
    private String elevationValue;

    @XmlElement(name = "verticalDatum")
    private String verticalDatum;

    @XmlElement(name = "pos")
    public String getPosString() {
        if (position == null || position.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Double d : position) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(d);
        }
        return sb.toString();
    }

    public void setPosString(String pos) {
        if (pos == null || pos.isBlank()) return;
        this.position = new ArrayList<>();
        for (String s : pos.trim().split("\\s+")) {
            this.position.add(Double.parseDouble(s));
        }
    }

    public ElevatedPoint() {
        axisLabel = new ArrayList<>();
        position = new ArrayList<>();

    }

    //<editor-fold defaultstate="collapsed" desc=" Class properties ">
    /**
     * @return the srsDimension
     */
    public BigInteger getSrsDimension() {
        return srsDimension;
    }

    /**
     * @param srsDimension the srsDimension to set
     */
    public void setSrsDimension(BigInteger srsDimension) {
        this.srsDimension = srsDimension;
    }

    /**
     * @return the srsName
     */
    public String getSrsName() {
        return srsName;
    }

    /**
     * @param srsName the srsName to set
     */
    public void setSrsName(String srsName) {
        this.srsName = srsName;
    }

    /**
     * @return the axisLabel
     */
    public List<String> getAxisLabel() {
        return axisLabel;
    }

    /**
     * @param axisLabel the axisLabel to set
     */
    public void setAxisLabel(List<String> axisLabel) {
        this.axisLabel = axisLabel;
    }

    /**
     * @return the position
     */
    public List<Double> getPosition() {
        return position;
    }

    /**
     * @param position the position to set
     */
    public void setPosition(List<Double> position) {
        this.position = position;
    }

    /**
     * @return the elevationUOM
     */
    public String getElevationUOM() {
        return elevationUOM;
    }

    /**
     * @param elevationUOM the elevationUOM to set
     */
    public void setElevationUOM(String elevationUOM) {
        this.elevationUOM = elevationUOM;
    }

    /**
     * @return the elevationValue
     */
    public String getElevationValue() {
        return elevationValue;
    }

    /**
     * @param elevationValue the elevationValue to set
     */
    public void setElevationValue(String elevationValue) {
        this.elevationValue = elevationValue;
    }

    /**
     * @return the verticalDatum
     */
    public String getVerticalDatum() {
        return verticalDatum;
    }

    /**
     * @param verticalDatum the verticalDatum to set
     */
    public void setVerticalDatum(String verticalDatum) {
        this.verticalDatum = verticalDatum;
    }

    //</editor-fold>
    
    @Override
    public int hashCode() {

        return this.srsName.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ElevatedPoint)) {
            return false;
        }
        ElevatedPoint other = (ElevatedPoint) object;
        if ((this.srsName == null && other.getSrsName() != null) || (this.srsName != null && !this.srsName.equals(other.getSrsName()))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ElevatedPoint[ name=" + this.srsName + " ]";
    }
}

