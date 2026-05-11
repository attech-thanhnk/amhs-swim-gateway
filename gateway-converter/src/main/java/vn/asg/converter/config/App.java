/*
 */
package vn.asg.converter.config;

import vn.asg.converter.common.XmlUtil;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author ThanhNk
 */
@XmlRootElement(name = "App")
@XmlAccessorType(XmlAccessType.NONE)
public class App {

    private static final String DEFAULT_FILE = "resources/app.xml";
    private static final String EXTERNAL_FILE = "app.xml";
    
    private static App instance;

    @XmlElementWrapper(name = "Config")
    @XmlElement(name = "Property")
    private java.util.List<Property> properties;

    @XmlElementWrapper(name = "Airports")
    @XmlElement(name = "Airport")
    private java.util.List<Airport> airports;

    public App() {
        properties = new java.util.ArrayList<>();
        airports = new java.util.ArrayList<>();
    }

    /**
     * Loads configuration from app.xml. 
     * Prioritizes external file in current directory over bundled resource.
     */
    public static void load() {
        try {
            java.io.File external = new java.io.File(EXTERNAL_FILE);
            if (external.exists()) {
                instance = (App) XmlUtil.deserialize(EXTERNAL_FILE, App.class);
            } else {
                instance = (App) XmlUtil.deserialize(DEFAULT_FILE, App.class);
            }
        } catch (Exception e) {
            System.err.println("[App] Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            // Fallback to empty instance to avoid NullPointerException
            instance = new App();
        }
    }
    
    public static App getInstance () {
        if (instance == null) {
            load();
        }
        return instance;
    }

    //<editor-fold defaultstate="collapsed" desc=" Class properties ">
    /**
     * @return the properties
     */
    public java.util.List<Property> getProperties() {
        return properties;
    }

    /**
     * @param properties the properties to set
     */
    public void setProperties(java.util.List<Property> properties) {
        this.properties = properties;
    }

    /**
     * @return the airports
     */
    public java.util.List<Airport> getAirports() {
        return airports;
    }

    /**
     * @param airports the airports to set
     */
    public void setAirports(java.util.List<Airport> airports) {
        this.airports = airports;
    }

    //</editor-fold>
    
    public String getString(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        for (Property property : this.properties) {
            if (!property.getKey().equalsIgnoreCase(key)) {
                continue;
            }
            return property.getValue();
        }
        return null;
    }

    public Long getLong(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        for (Property property : this.properties) {
            if (!property.getKey().equalsIgnoreCase(key)) {
                continue;
            }
            return Long.parseLong(property.getValue());
        }
        return null;
    }
    
    public Boolean getBoolean(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        for (Property property : this.properties) {
            if (!property.getKey().equalsIgnoreCase(key)) {
                continue;
            }
            return Boolean.parseBoolean(property.getValue());
        }
        return null;
    }
    
    public Boolean getBoolean(String key, Boolean defaultValue) {
        if (key == null || key.isEmpty()) {
            return defaultValue;
        }
        for (Property property : this.properties) {
            if (!property.getKey().equalsIgnoreCase(key)) {
                continue;
            }
            return Boolean.parseBoolean(property.getValue());
        }
        return defaultValue;
    }
    
    public Integer getInteger(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        for (Property property : this.properties) {
            if (!property.getKey().equalsIgnoreCase(key)) {
                continue;
            }
            return Integer.parseInt(property.getValue());
        }
        return null;
    }

    public Airport getAirportDefine(String icaoDesignator) {
        for (Airport airportDefine : this.airports) {
            if (airportDefine.getIcaoCode().equalsIgnoreCase(icaoDesignator)) {
                return airportDefine;
            }
        }
        return null;
    }
}

