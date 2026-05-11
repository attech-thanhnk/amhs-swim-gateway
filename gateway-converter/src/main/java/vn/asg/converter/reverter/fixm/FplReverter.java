package vn.asg.converter.reverter.fixm;

import vn.asg.converter.model.FplMessage;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

/**
 * Đọc file XML FIXM 4.2 và dựng lại bản tin Kế hoạch bay (FPL) dạng Text.
 */
public class FplReverter {

    public String revert(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlContent)));

        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xpath = xPathFactory.newXPath();
        
        xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                if ("fixm".equals(prefix)) return "http://www.fixm.aero/flight/4.2";
                if ("base".equals(prefix)) return "http://www.fixm.aero/base/4.2";
                return javax.xml.XMLConstants.NULL_NS_URI;
            }
            public String getPrefix(String namespaceURI) { return null; }
            public java.util.Iterator<String> getPrefixes(String namespaceURI) { return null; }
        });

        // Đọc các giá trị bằng XPath
        FplMessage fpl = new FplMessage();
        fpl.setAircraftId(xpath.evaluate("//fixm:flightIdentification/fixm:aircraftIdentification", doc).trim());
        fpl.setAircraftType(xpath.evaluate("//fixm:aircraft/fixm:aircraftType/base:icaoModelIdentifier", doc).trim());
        fpl.setWakeTurbulence(xpath.evaluate("//fixm:aircraft/fixm:wakeTurbulence", doc).trim());
        fpl.setDepartureIcao(xpath.evaluate("//fixm:departure/fixm:aerodrome/base:locationIndicator", doc).trim());
        
        String depTimeIso = xpath.evaluate("//fixm:departure/fixm:estimatedOffBlockTime/base:time", doc).trim();
        if (!depTimeIso.isEmpty() && depTimeIso.contains("T")) {
            // Extract HHmm from 2024-05-06T09:00:00Z
            String timePart = depTimeIso.substring(depTimeIso.indexOf("T") + 1, depTimeIso.indexOf("T") + 6);
            fpl.setDepartureTime(timePart.replace(":", ""));
        }

        fpl.setDestinationIcao(xpath.evaluate("//fixm:destination/fixm:aerodrome/base:locationIndicator", doc).trim());
        fpl.setRoute(xpath.evaluate("//fixm:enRoute/fixm:routeTrajectoryGroup/fixm:routeTrajectory/fixm:routeText", doc).trim());

        return fpl.toString();
    }
}
