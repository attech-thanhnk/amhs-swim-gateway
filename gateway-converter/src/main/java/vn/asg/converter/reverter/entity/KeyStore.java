/*
 */
package vn.asg.converter.reverter.entity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.HashSet;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.sun.xml.bind.marshaller.CharacterEscapeHandler;

/**
 *
 * @author ThanhNk
 */
@XmlRootElement(name = "Keys")
@XmlAccessorType(XmlAccessType.NONE)
public class KeyStore {

    // @XmlElementWrapper(name = "Keys")
    @XmlElement(name = "KeyValue")
    private HashSet<KeyValue> keys;

    public String getValue(String key) {
        

        if (key == null || key.isEmpty()) {
            return null;
        }

        for (KeyValue item : keys) {
            if (!item.equalsKey(key)) {
                continue;
            }

            return item.getValue();
        }

        return split(key);
    }
    
    private String split(String str) {
        if (str.lastIndexOf("/") < 0) return str;
        return str.substring(str.lastIndexOf("/"));
    }

    public KeyStore() {
        keys = new HashSet<>();
    }
    
    public void add(String key, String value) {
        this.keys.add(new KeyValue(key, value));
    }

    public static KeyStore load(String file) {
        try {
            return (KeyStore) deserialize(file, KeyStore.class);
        } catch (JAXBException | IOException ex) {
            ex.printStackTrace();
            return new KeyStore();
        }
    }
    
    public void save(String file) throws JAXBException, IOException {
        serialize(file, this);
    }

    public void serialize(String location, Object value) throws FileNotFoundException, JAXBException, IOException {
//        FileOutputStream os = new FileOutputStream(location);
        try (FileOutputStream os = new FileOutputStream(location)){
            JAXBContext context = JAXBContext.newInstance(value.getClass());
            Marshaller m = context.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            m.setProperty(CharacterEscapeHandler.class.getName(), new CharacterEscapeHandler() {
                @Override
                public void escape(char[] ac, int i, int j, boolean flag, Writer writer) throws IOException {
                    writer.write(ac, i, j);
                }
            });
            m.marshal(value, os);
        } 
//        finally {
//            try {
//                os.close();
//            } catch (IOException ex) {
//            }
//        }
    }

    public static Object deserialize(String location, Class<?> cls) throws JAXBException, FileNotFoundException, IOException {
        JAXBContext context = JAXBContext.newInstance(cls);
        Unmarshaller m = context.createUnmarshaller();

        // Try loading from classpath first
        InputStream stream = KeyStore.class.getClassLoader().getResourceAsStream(location);

        // If not found in classpath, try as file path
        if (stream == null) {
            File file = new File(location);
            if (file.exists()) {
                try (FileInputStream fileStream = new FileInputStream(file)) {
                    return m.unmarshal(fileStream);
                }
            } else {
                throw new FileNotFoundException("Resource not found in classpath or filesystem: " + location);
            }
        }

        try (stream) {
            return m.unmarshal(stream);
        }
    }

    /**
     * @return the keys
     */
    public HashSet<KeyValue> getKeys() {
        return keys;
    }

    /**
     * @param keys the keys to set
     */
    public void setKeys(HashSet<KeyValue> keys) {
        this.keys = keys;
    }
    
    public static void main(String [] args) {
        try {
            KeyStore keyStore = new KeyStore();
            keyStore.add("http://codes.wmo.int/common/nil/noSignificantChange", "NOSIG");
            keyStore.save("weathers-code.xml");
            
        } catch (JAXBException | IOException ex) {
            ex.printStackTrace();
        }
        
        
    }

}

