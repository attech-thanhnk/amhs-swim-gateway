package vn.asg.converter.reverter.aixm;

import vn.asg.converter.model.NotamMessage;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.text.ParseException;
import java.util.Date;

/**
 * Đọc file XML AIXM (do Gateway tạo ra) và dựng lại bản tin NOTAM dạng Text.
 */
public class NotamReverter {

    public String revert(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlContent)));

        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xpath = xPathFactory.newXPath();
        
        // Setup namespace context cho XPath
        xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                if ("aixm".equals(prefix)) return "urn:aero:aixm:5.1.1";
                if ("gml".equals(prefix)) return "http://www.opengis.net/gml/3.2";
                return javax.xml.XMLConstants.NULL_NS_URI;
            }
            public String getPrefix(String namespaceURI) { return null; }
            public java.util.Iterator<String> getPrefixes(String namespaceURI) { return null; }
        });

        // Đọc các giá trị bằng XPath
        NotamMessage notam = new NotamMessage();
        notam.setNotamId(xpath.evaluate("//aixm:name", doc).trim());
        notam.setType(xpath.evaluate("//aixm:encoding", doc).trim());
        notam.setPurpose(xpath.evaluate("//aixm:purpose", doc).trim());
        notam.setText(xpath.evaluate("//aixm:note", doc).trim());
        
        // Cố gắng lấy Q-line info nếu có (giả định cấu trúc mở rộng)
        notam.setFir(xpath.evaluate("//aixm:fir", doc).trim());
        notam.setNotamCode(xpath.evaluate("//aixm:notamCode", doc).trim());
        notam.setTraffic(xpath.evaluate("//aixm:traffic", doc).trim());
        notam.setScope(xpath.evaluate("//aixm:scope", doc).trim());
        notam.setLowerLimit(xpath.evaluate("//aixm:lowerLimit", doc).trim());
        notam.setUpperLimit(xpath.evaluate("//aixm:upperLimit", doc).trim());
        notam.setLocation(xpath.evaluate("//aixm:location", doc).trim());

        String beginPos = xpath.evaluate("//gml:beginPosition", doc).trim();
        String endPos = xpath.evaluate("//gml:endPosition", doc).trim();

        if (!beginPos.isEmpty() && !beginPos.equals("unknown")) {
             notam.setValidFrom(parseIsoDate(beginPos));
        }

        if (!endPos.isEmpty()) {
             if (endPos.equals("unknown")) {
                 notam.setPermanent(true);
             } else {
                 notam.setValidUntil(parseIsoDate(endPos));
             }
        }

        return notam.toString();
    }

    private org.joda.time.DateTime parseIsoDate(String isoDate) {
        try {
            return org.joda.time.format.DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZoneUTC().parseDateTime(isoDate);
        } catch (Exception e) {
            return null;
        }
    }
}
