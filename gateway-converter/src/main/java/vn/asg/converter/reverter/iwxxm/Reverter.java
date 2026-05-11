/*
 */
package vn.asg.converter.reverter.iwxxm;

import vn.asg.converter.reverter.entity.KeyStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author ThanhNk
 * @param <IWXXMType>
 * @param <TEntity>
 */
public abstract class Reverter<IWXXMType, TEntity> {

    protected KeyStore weatherCode = KeyStore.load("resources/weather-codes.xml");
    protected KeyStore cloudCode = KeyStore.load("resources/cloud-codes.xml");
    protected Common common = new Common();

    public abstract TEntity convert(String content) throws IWXXMParsingException;

    public abstract TEntity convert(IWXXMType type) throws IWXXMParsingException;

    public abstract String convertToString(String content) throws IWXXMParsingException;

    protected IWXXMType revert(String iwxxmcontent, Class<IWXXMType> cls) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(cls);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        InputStream inputStream = new ByteArrayInputStream(iwxxmcontent.getBytes());
        JAXBElement<IWXXMType> metarType = (JAXBElement<IWXXMType>) unmarshaller.unmarshal(inputStream);
        return metarType.getValue();
    }

    protected NodeList revert(String iwxxmcontent, String nameNode) throws ParserConfigurationException, SAXException, IOException {
        NodeList nList = null;
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        InputStream inputStream = new ByteArrayInputStream(iwxxmcontent.getBytes());
        Document doc = dBuilder.parse(inputStream);
        doc.getDocumentElement().normalize();
        Element root = doc.getDocumentElement();
        if (root != null) {
            nList = doc.getElementsByTagName(nameNode);
        }
        return nList;
    }

    protected String getSpeedUn(String str) {
        String out = "";
        str = str.toLowerCase();
        if (str != null && !str.isEmpty()) {
            switch (str) {
                case "m/s":
                    out = "MPS";
                    break;
                case "hpa":
                    out = "HPA";
                    break;
                case "[kn_i]":
                    out = "KT";
                    break;
                case "km/h":
                    out = "KMH";
                    break;
                default:
                    out = "KMH";
                    break;
            }
        }
        return out;
    }

    protected String getIwxxmEnum(String str) {
        String out = "";
        if (str != null) {
            switch (str) {
                case "[nmi_i]":
                    out = "NM";
                    break;
                case "SM":
                    out = "SM";
                    break;
                case "[ft_i]":
                    out = "FT";
                    break;
                case "km":
                    out = "KM";
                    break;
                case "m":
                    out = "M";
                    break;
                case "mm":
                    out = "MM";
                    break;
                default:
                    break;
            }
        }
        return out;
    }
    //Sigmet

    protected String getdirectionOfMotion(double v) {
        String out = "";
        if (v == 90) {
            out = "E";
        } else if (v == 180) {
            out = "S";
        } else if (v == 270) {
            out = "W";
        } else if (v == 360) {
            out = "N";
        } else if ((v < 90) & (0 < v)) {
            out = "NE";
        } else if ((v < 180) & (90 < v)) {
            out = "SE";
        } else if ((v < 270) & (180 < v)) {
            out = "SW";
        } else if ((v < 360) & (270 < v)) {
            out = "NW";
        }
        return out;
    }
}

