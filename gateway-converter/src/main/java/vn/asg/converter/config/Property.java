/*
 */
package vn.asg.converter.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

/**
 *
 * @author ThanhNk
 */
@XmlRootElement(name = "Property")
@XmlAccessorType(XmlAccessType.NONE)
public class Property {

    @XmlAttribute(name = "name")
    private String key;

    @XmlValue
    private String value;

    public Property() {
    }

    public Property(String key, String val) {
        this.key = key;
        this.value = val;
    }

    //<editor-fold defaultstate="collapsed" desc=" Class properties ">
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setBoolean(Boolean value) {
        this.value = value.toString();
    }

    public Boolean getBoolean() {
        return Boolean.valueOf(this.value);
    }

    public void setInteger(Integer value) {
        this.value = value.toString();
    }

    public Integer getInteger() {
        return Integer.parseInt(this.value);
    }

    public void setDouble(Double value) {
        this.value = value.toString();
    }

    public Double getDouble() {
        return Double.parseDouble(this.value);
    }
    //</editor-fold>

    @Override
    public int hashCode() {
        return this.key != null ? this.key.toLowerCase().hashCode() : 0;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Property)) {
            return false;
        }
        Property other = (Property) object;
        if (this.key == null || other.key == null) {
            return this.key == other.key;
        }
        return this.key.equalsIgnoreCase(other.key);
    }

    @Override
    public String toString() {
        return String.format("%s:%s", this.key, this.value);
    }
}

