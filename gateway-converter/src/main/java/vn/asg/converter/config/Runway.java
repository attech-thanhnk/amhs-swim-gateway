/*
 */
package vn.asg.converter.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author ThanhNk
 */
@XmlRootElement(name = "Runway")
@XmlAccessorType(XmlAccessType.NONE)
public class Runway {

    @XmlAttribute(name = "code")
    private String code;

    @XmlAttribute(name = "trueBearing")
    private Integer trueBearing;

    /**
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * @param code the code to set
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * @return the trueBearing
     */
    public Integer getTrueBearing() {
        return trueBearing;
    }

    /**
     * @param trueBearing the trueBearing to set
     */
    public void setTrueBearing(Integer trueBearing) {
        this.trueBearing = trueBearing;
    }

    @Override
    public int hashCode() {
        return this.code.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Runway)) {
            return false;
        }
        Runway other = (Runway) object;
        if ((this.code == null && other.getCode() != null) || (this.code != null && !this.code.equals(other.getCode()))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("Runway [ code=%s ]", this.code);
    }

}

