/*
 * To change this template, choose Tools | Templates
 */
package vn.asg.converter.common;

import com.sun.xml.bind.marshaller.CharacterEscapeHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 *
 * @author ThanhNk
 */
public class XmlUtil {

    public static void serialize(String location, Object value) throws FileNotFoundException, JAXBException {
        FileOutputStream os = new FileOutputStream(location);
        try {
//            os = new FileOutputStream(location);
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
        } finally {
            try {
                os.close();
            } catch (IOException ex) {
            }
        }
    }

    public static Object deserialize(String location, Class<?> cls) throws JAXBException, FileNotFoundException {
        JAXBContext context = JAXBContext.newInstance(cls);
        Unmarshaller m = context.createUnmarshaller();
        FileInputStream stream = new FileInputStream(new File(location));
        try {
            return m.unmarshal(stream);
        } finally {
            try {
                stream.close();
            } catch (IOException ex) {
            }
        }
    }
    
    public static Object deserialize(File location, Class<?> cls) throws JAXBException, FileNotFoundException {
        JAXBContext context = JAXBContext.newInstance(cls);
        Unmarshaller m = context.createUnmarshaller();
        FileInputStream stream = new FileInputStream(location);
        try {
            return m.unmarshal(stream);
        } finally {
            try {
                stream.close();
            } catch (IOException ex) {
            }
        }
    }

}

