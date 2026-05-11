package vn.asg.converter.builder;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import vn.asg.converter.model.NotamMessage;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * Tạo XML AIXM 5.1 từ đối tượng NotamMessage
 */
public class AixmBuilder {

    private static final String NS_AIXM = "http://www.aixm.aero/schema/5.1.1";
    private static final String NS_GML = "http://www.opengis.net/gml/3.2";
    private static final String NS_XLINK = "http://www.w3.org/1999/xlink";
    private static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZoneUTC();

    public String buildNotam(NotamMessage msg) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();

            String id = msg.getNotamId().replace("/", "-").replace(" ", "_");

            // Root element: AIXMBasicMessage
            Element root = doc.createElementNS(NS_AIXM, "aixm:AIXMBasicMessage");
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:aixm", NS_AIXM);
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:gml", NS_GML);
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xlink", NS_XLINK);
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", NS_XSI);
            root.setAttributeNS(NS_GML, "gml:id", "msg-" + id);
            doc.appendChild(root);

            // aixm:hasMember
            Element hasMember = doc.createElementNS(NS_AIXM, "aixm:hasMember");
            root.appendChild(hasMember);

            // aixm:Event
            Element event = doc.createElementNS(NS_AIXM, "aixm:Event");
            event.setAttributeNS(NS_GML, "gml:id", "event-" + id);
            hasMember.appendChild(event);

            // aixm:timeSlice
            Element timeSlice = doc.createElementNS(NS_AIXM, "aixm:timeSlice");
            event.appendChild(timeSlice);

            // aixm:EventTimeSlice
            Element eventTimeSlice = doc.createElementNS(NS_AIXM, "aixm:EventTimeSlice");
            eventTimeSlice.setAttributeNS(NS_GML, "gml:id", "ts-" + id);
            timeSlice.appendChild(eventTimeSlice);

            // gml:validTime
            Element validTime = doc.createElementNS(NS_GML, "gml:validTime");
            eventTimeSlice.appendChild(validTime);

            Element timePeriod = doc.createElementNS(NS_GML, "gml:TimePeriod");
            timePeriod.setAttributeNS(NS_GML, "gml:id", "tp-" + id);
            validTime.appendChild(timePeriod);

            Element beginPos = doc.createElementNS(NS_GML, "gml:beginPosition");
            if (msg.getValidFrom() != null) {
                beginPos.setTextContent(msg.getValidFrom().toString(ISO_FORMATTER));
            } else {
                beginPos.setAttribute("indeterminatePosition", "unknown");
            }
            timePeriod.appendChild(beginPos);

            Element endPos = doc.createElementNS(NS_GML, "gml:endPosition");
            if (msg.isPermanent()) {
                endPos.setAttribute("indeterminatePosition", "unknown");
            } else if (msg.getValidUntil() != null) {
                endPos.setTextContent(msg.getValidUntil().toString(ISO_FORMATTER));
            } else {
                endPos.setAttribute("indeterminatePosition", "unknown");
            }
            timePeriod.appendChild(endPos);

            // aixm:featureLifetime
            Element featureLifetime = doc.createElementNS(NS_AIXM, "aixm:featureLifetime");
            eventTimeSlice.appendChild(featureLifetime);
            Element ltPeriod = doc.createElementNS(NS_GML, "gml:TimePeriod");
            ltPeriod.setAttributeNS(NS_GML, "gml:id", "lt-" + id);
            featureLifetime.appendChild(ltPeriod);
            Element ltBegin = doc.createElementNS(NS_GML, "gml:beginPosition");
            if (msg.getValidFrom() != null)
                ltBegin.setTextContent(msg.getValidFrom().toString(ISO_FORMATTER));
            ltPeriod.appendChild(ltBegin);
            Element ltEnd = doc.createElementNS(NS_GML, "gml:endPosition");
            if (msg.getValidUntil() != null) {
                ltEnd.setTextContent(msg.getValidUntil().toString(ISO_FORMATTER));
            } else {
                ltEnd.setAttribute("indeterminatePosition", "unknown");
            }
            ltPeriod.appendChild(ltEnd);

            // aixm:name, aixm:encoding, aixm:purpose
            Element name = doc.createElementNS(NS_AIXM, "aixm:name");
            name.setTextContent(msg.getNotamId());
            eventTimeSlice.appendChild(name);

            Element encoding = doc.createElementNS(NS_AIXM, "aixm:encoding");
            encoding.setTextContent(msg.getType());
            eventTimeSlice.appendChild(encoding);

            Element purpose = doc.createElementNS(NS_AIXM, "aixm:purpose");
            purpose.setTextContent(msg.getPurpose());
            eventTimeSlice.appendChild(purpose);

            // aixm:text (LinguisticNote)
            Element text = doc.createElementNS(NS_AIXM, "aixm:text");
            eventTimeSlice.appendChild(text);
            Element linguisticNote = doc.createElementNS(NS_AIXM, "aixm:LinguisticNote");
            linguisticNote.setAttributeNS(NS_GML, "gml:id", "note-" + id);
            text.appendChild(linguisticNote);
            Element note = doc.createElementNS(NS_AIXM, "aixm:note");
            note.setTextContent(msg.getText());
            linguisticNote.appendChild(note);

            // Serialize
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();

        } catch (Exception e) {
            throw new RuntimeException("Error building AIXM XML", e);
        }
    }
}
