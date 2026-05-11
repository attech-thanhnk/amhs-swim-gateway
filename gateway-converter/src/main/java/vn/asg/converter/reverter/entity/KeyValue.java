/*
 */
package vn.asg.converter.reverter.entity;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author ThanhNk
 */
@XmlRootElement(name = "Property")
@XmlAccessorType(XmlAccessType.NONE)
public class KeyValue {

    @XmlAttribute(name = "name")
    private String key;

    @XmlAttribute(name = "value")
    private String value;

    public KeyValue() {
    }

    public KeyValue(String key, String val) {
        this.key = key;
        this.value = val;
    }

    public boolean equalsKey(String item) {
        if (item == null || item.isEmpty()) {
            return false;
        }

        return this.key.equalsIgnoreCase(item);
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
        return this.key.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof KeyValue)) {
            return false;
        }
        KeyValue other = (KeyValue) object;
        if ((this.key == null && other.key != null) || (this.key != null && !this.key.equalsIgnoreCase(other.key))) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return String.format("%s:%s", this.key, this.value);
    }
}

