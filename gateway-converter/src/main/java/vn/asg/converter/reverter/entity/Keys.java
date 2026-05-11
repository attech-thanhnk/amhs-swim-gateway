/*
 */
package vn.asg.converter.reverter.entity;

import java.util.Dictionary;
import java.util.Hashtable;

/**
 *
 * @author ThanhNk
 */
public class Keys {

    private static Keys instance;

    private final Dictionary<String, String> keys = new Hashtable<>();

    private Keys() {
        keys.put("http://codes.wmo.int/common/nil/noSignificantChange", "NOSIG");
        keys.put("http://codes.wmo.int/common/nil/notDetectedByAutoSystem", "NCD");
        keys.put("http://codes.wmo.int/common/nil/nothingOfOperationalSignificance", "NSW");
        keys.put("http://codes.wmo.int/bufr4/codeflag/0-20-012/9", "CB");
        keys.put("http://codes.wmo.int/49-2/SigConvectiveCloudType/CB", "CB");
        
    }
    
    public String get(String key) {
        return this.keys.get(key);
    }

    public static Keys getInstance() {
        if (instance == null) {
            instance = new Keys();
        }
        return instance;
    }
}

