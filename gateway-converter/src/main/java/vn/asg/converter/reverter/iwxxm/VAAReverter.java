package vn.asg.converter.reverter.iwxxm;

import vn.asg.converter.reverter.entity.VAA;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author ThanhNk
 */
public class VAAReverter extends Reverter<NodeList, VAA> {

    private final Common common = new Common();
    private final VAA vaaEnt = new VAA();
//    private boolean TypeOfReport = false;
    private boolean TimeOrigin = false;
    private boolean NameOfTCAC = false;
    private boolean NameOfVolcano = false;
    private boolean LocationOfVolcan = false;
    private boolean StateOrRegion = false;
    private boolean SummitElevation = false;
    private boolean AdvisoryNumber = false;
    private boolean InformationSource = false;
    private boolean ColourCode = false;
    private boolean EruptionDetails = false;
    private boolean TimeOfObservation = false;
    private boolean ObservedOrEstimated = false;
    private boolean ForecastHeightAndPosition6 = false;
    private boolean ForecastHeightAndPosition12 = false;
    private boolean ForecastHeightAndPosition18 = false;
    private boolean Remark = false;
    private boolean NextAdvisory = false;
    private Node nodeSearch = null;

    @Override
    public String convertToString(String content) throws IWXXMParsingException {
        VAA vaa = convert(content);
        return vaa == null ? null : vaa.toString();
    }

    @Override
    public VAA convert(NodeList node) throws IWXXMParsingException {
        if (node == null) {
            return null;
        }
        visitChildNodes(node);
        return vaaEnt;
    }

    @Override
    public VAA convert(String content) throws IWXXMParsingException {
        NodeList vaa = null;
        try {
            vaa = revert(content, "iwxxm:VolcanicAshAdvisory");
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new IWXXMParsingException("Không đọc được định dạng điện văn.", ex);
        }
        return convert(vaa);
    }

    private void visitChildNodes(NodeList nList) {
        int count = 1;
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node node = nList.item(temp);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                //Check all attributes
                String name = node.getNodeName().trim().toLowerCase();
                switch (name) {
                    case "iwxxm:issuetime":
                        setStrTimeOrigin(node);//gml:TimeInstant
                        break;
                    case "iwxxm:issuingvolcanicashadvisorycentre":
                        setStrNameOfTCAC(node);
                        break;
                    case "iwxxm:volcano":
                        setStrNameOfVolcano(node);
                        setStrLocationOfVolcan(node);
                        break;
                    case "iwxxm:stateorregion":
                        setStateOrRegion(node);
                        break;
                    case "iwxxm:summitelevation":
                        setSummitElevation(node);
                        break;
                    case "iwxxm:advisorynumber":
                        setAdvisoryNumber(node);
                        break;
                    case "iwxxm:informationsource":
                        setInformationSource(node);
                        break;
                    case "iwxxm:colourcode":
                        setColourCode(node);
                        break;
                    case "iwxxm:eruptiondetails":
                        setEruptionDetails(node);
                        break;
                    case "iwxxm:observation":
                        setTimeOfObservation(node);
                        setObservedOrEstimated(node);
                        break;
                    case "iwxxm:forecast":
                        Node node1Nil = node;
                        if (node1Nil.hasAttributes()) {
                            NamedNodeMap nodeMap = node1Nil.getAttributes();
                            for (int ij = 0; ij < nodeMap.getLength(); ij++) {
                                Node tempNode = nodeMap.item(ij);
                                if (tempNode.getNodeName().toLowerCase().contains("nilreason")) {
                                    String radiusType = tempNode.getNodeValue();
                                    if (radiusType.contains("nothingOfOperationalSignificance")) {
                                        this.ForecastHeightAndPosition6 = true;
                                        this.ForecastHeightAndPosition12 = true;
                                        this.ForecastHeightAndPosition18 = true;
                                        break;
                                    }
                                }
                            }
                        }
                        switch (count) {
                            case 1:
                                count++;
                                setForecastHeightAndPosition6(node);
                                break;
                            case 2:
                                count++;
                                setForecastHeightAndPosition12(node);
                                break;
                            case 3:
                                setForecastHeightAndPosition18(node);
                                break;
                            default:
                                break;
                        }
                        break;

                    case "iwxxm:remarks":
                        setRemark(node);
                        break;
                    case "iwxxm:nextadvisorytime":
                        setNextAdvisory(node);
                        break;

                }
                //Check all attributes
                if (node.hasAttributes()) {
                    // get attributes names and values
                    NamedNodeMap nodeMap = node.getAttributes();
                    for (int i = 0; i < nodeMap.getLength(); i++) {
                        Node tempNode = nodeMap.item(i);
                    }
                    if (node.hasChildNodes()) {
                        //We got more childs; Let's visit them as well
                        visitChildNodes(node.getChildNodes());
                    }
                } else if (node.hasChildNodes()) {
                    visitChildNodes(node.getChildNodes());
                }
            }
        }
    }

    private void setObservedOrEstimated(Node node1) {
        if (this.ObservedOrEstimated) {
            return;
        }
        String strCont = "";//=(strAirspaceVolume + " " )+ strDirectionSpeedOfMotion
        String strAirspaceVolume = "";
        nodeSearch = null;
        Node n1 = getNode(node1, "iwxxm:VolcanicAshObservedOrEstimatedConditions");
        if (n1 == null) {
            return;
        }
        this.TimeOfObservation = true;
        if (!n1.hasChildNodes()) {
            return;
        }
        NodeList nList = n1.getChildNodes();
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node node = nList.item(temp);
            if (node.getNodeName().equals("iwxxm:ashCloud")) {
                nodeSearch = null;
                Node n2 = getNode(node, "iwxxm:VolcanicAshCloudObservedOrEstimated");
                if (n2 != null) {
                    String strDirectionSpeedOfMotion = getDirectionOfMotion(n2);
                    nodeSearch = null;
                    Node n3 = getNode(n2, "iwxxm:ashCloudExtent");
                    if (n3 != null) {
                        strAirspaceVolume = strAirspaceVolume + " " + getAirspaceVolume(n3) + " " + strDirectionSpeedOfMotion;
                        strAirspaceVolume = strAirspaceVolume.trim();
                        strAirspaceVolume = strAirspaceVolume + "\n";
                    }
                }
            }
        }

        if (!strAirspaceVolume.isEmpty()) {
            strAirspaceVolume = strAirspaceVolume.substring(0, strAirspaceVolume.length() - 1);
            strCont = strAirspaceVolume;
        }
        vaaEnt.setStrObservedOrEstimated(strCont);
        this.ObservedOrEstimated = true;
    }

    //TimeOfObservation
    private void setTimeOfObservation(Node node1) {
        try {
            if (this.TimeOfObservation) {
                return;
            }
            String strCont = "";
            nodeSearch = null;
            Node n1 = getNode(node1, "iwxxm:VolcanicAshObservedOrEstimatedConditions");
            if (n1 == null) {
                return;
            }
            this.EruptionDetails = true;
            nodeSearch = null;
            Node n2 = getNode(node1, "iwxxm:phenomenonTime");
            if (n2 == null) {
                return;
            }
            nodeSearch = null;
            Node n3 = getNode(node1, "gml:timePosition");
            if (n3 == null) {
                return;
            }
            strCont = strCont + " " + getValueOfNode(n3, "gml:timePosition");
            if (!strCont.isEmpty()) {
                strCont = strCont.trim();
                this.TimeOfObservation = true;
            }
            strCont = strCont.trim();
            if (strCont.isEmpty()) {
                return;
            }
            vaaEnt.setStrTimeOfObservation(common.getTime(strCont.replace("T", " "), "dd/HHmm") + "Z");
            this.TimeOfObservation = true;
        } catch (Exception e) {
            Logger.getLogger(VAAReverter.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    //set EruptionDetails
    private void setEruptionDetails(Node node1) {
        if (EruptionDetails) {
            return;
        }
        Node node1Nil = node1;
        if (node1Nil.hasAttributes()) {
            NamedNodeMap nodeMap = node1Nil.getAttributes();
            for (int ij = 0; ij < nodeMap.getLength(); ij++) {
                Node tempNode = nodeMap.item(ij);
                if (tempNode.getNodeName().toLowerCase().contains("nilreason")) {
                    String radiusType = tempNode.getNodeValue();
                    if (radiusType.contains("unknown")) {
                        this.ColourCode = true;
                        EruptionDetails = true;
                        vaaEnt.setStrEruptionDetails("UNKNOWN");
                        return;
                    }
                }
            }
        }
        String strCont = getValueOfNode(node1, "iwxxm:eruptionDetails");
        if (strCont.isEmpty()) {
            return;
        }
        this.ColourCode = true;
        EruptionDetails = true;
        vaaEnt.setStrEruptionDetails(strCont);
    }

    //setColourCode
    private void setColourCode(Node node1) {
        if (ColourCode) {
            return;
        }
        Node node1Nil = node1;
        if (node1Nil.hasAttributes()) {
            NamedNodeMap nodeMap = node1Nil.getAttributes();
            for (int ij = 0; ij < nodeMap.getLength(); ij++) {
                Node tempNode = nodeMap.item(ij);
                if (tempNode.getNodeName().toLowerCase().contains("nilreason")) {
                    String radiusType = tempNode.getNodeValue();
                    if (radiusType.contains("unknown")) {
                        this.InformationSource = true;
                        ColourCode = true;
                        vaaEnt.setStrColourCode("UNKNOWN");
                        return;
                    } else if (radiusType.contains("withheld")) {
                        this.InformationSource = true;
                        ColourCode = true;
                        vaaEnt.setStrColourCode("NOT GIVEN");
                        return;
                    } else if (radiusType.contains("missing")) {
                        this.InformationSource = true;
                        ColourCode = true;
                        vaaEnt.setStrColourCode("NIL");
                        return;
                    }
                }
            }
        }
        String strCont = getAttribute(node1, "xlink:href");
        if (strCont == null) {
            return;
        }
        if (strCont.isEmpty()) {
            return;
        }
        this.InformationSource = true;
        ColourCode = true;
        strCont = common.split(strCont);
        vaaEnt.setStrColourCode(strCont);
    }

    //set AdvisoryNumber
    private void setInformationSource(Node node1) {
        if (InformationSource) {
            return;
        }
        String strCont = getValueOfNode(node1, "iwxxm:informationSource");
        if (strCont.isEmpty()) {
            return;
        }
        this.AdvisoryNumber = true;
        InformationSource = true;
        vaaEnt.setStrInformationSource(strCont);
    }

    //set AdvisoryNumber
    private void setAdvisoryNumber(Node node1) {
        if (AdvisoryNumber) {
            return;
        }
        String strCont = getValueOfNode(node1, "iwxxm:advisoryNumber");
        if (strCont.isEmpty()) {
            return;
        }
        this.SummitElevation = true;
        AdvisoryNumber = true;
        vaaEnt.setStrAdvisoryNumber(strCont);
    }

    //setSummit elevation 
    private void setSummitElevation(Node node1) {
        if (SummitElevation) {
            return;
        }
        String strCont = getValueOfNode(node1, "iwxxm:summitElevation");
        if (strCont.isEmpty()) {
            return;
        }
        String strAttribute = getAttribute(node1, "uom");
        if (!strAttribute.isEmpty()) {
            strCont = strCont + strAttribute;
        }
        this.StateOrRegion = true;
        SummitElevation = true;
        vaaEnt.setStrSummitElevation(strCont);

    }

    //State or region
    private void setStateOrRegion(Node node1) {
        if (StateOrRegion) {
            return;
        }
        String strCont = getValueOfNode(node1, "iwxxm:stateOrRegion");
        if (strCont.isEmpty()) {
            return;
        }
        this.LocationOfVolcan = true;
        StateOrRegion = true;
        vaaEnt.setStrStateOrRegion(strCont);
    }

    //setStrLocationOfVolcan 
    private void setStrLocationOfVolcan(Node node1) {
        if (this.LocationOfVolcan) {
            return;
        }
        String strCont = "";
        String name = node1.getNodeName().toLowerCase();
        nodeSearch = null;
        Node node = getNode(node1, "metce:EruptingVolcano");
        if (node == null) {
            return;
        }
        Node node1Nil = getNode(node1, "gml:position");
        if (node1Nil.hasAttributes()) {
            NamedNodeMap nodeMap = node1Nil.getAttributes();
            for (int ij = 0; ij < nodeMap.getLength(); ij++) {
                Node tempNode = nodeMap.item(ij);
                if (tempNode.getNodeName().toLowerCase().contains("nilreason")) {
                    String radiusType = tempNode.getNodeValue();
                    if (radiusType.contains("unknown")) {
                        LocationOfVolcan = true;
                        vaaEnt.setStrLocationOfVolcan("UNKNOWN");
                    }
                }
            }
        }

        this.NameOfVolcano = true;
        nodeSearch = null;
        node = getNode(node1, "gml:pos");
        if (node == null) {
            return;
        }
        strCont = strCont + " " + getValueOfNode(node, "gml:pos");
        strCont = strCont.trim();
        if (!strCont.isEmpty()) {
            if (!strCont.equals("UNKNOWN") && !strCont.equals("UNNAMED")) {
                strCont = common.getPos(strCont);
            }
            LocationOfVolcan = true;
        }
        if (strCont.isEmpty()) {
            return;
        }
        vaaEnt.setStrLocationOfVolcan(strCont);
//            this.TimeOrigin = true; 
    }

    //setStrNameOfVolcano 
    private void setStrNameOfVolcano(Node node1) {
        if (this.NameOfVolcano) {
            return;
        }
        String strCont = "";
//            String name = node1.getNodeName().toLowerCase();
        nodeSearch = null;
        Node node = getNode(node1, "metce:EruptingVolcano");
        if (node == null) {
            return;
        }
        this.NameOfTCAC = true;
        nodeSearch = null;
        node = getNode(node1, "metce:name");
        if (node == null) {
            return;
        }
        strCont = strCont + " " + getValueOfNode(node, "metce:name");
        strCont = strCont.trim();
        if (!strCont.isEmpty()) {
            strCont = strCont.trim();
            NameOfVolcano = true;
        }
        strCont = strCont.trim();
        if (strCont.isEmpty()) {
            return;
        }
        vaaEnt.setStrNameOfVolcano(strCont);
//            this.TimeOrigin = true; 
    }
//setStrNameOfTCAC

    private void setStrNameOfTCAC(Node node1) {
        if (this.NameOfTCAC) {
            return;
        }
        String strCont = "";
//        String name = node1.getNodeName().toLowerCase();
        nodeSearch = null;
        Node node = getNode(node1, "aixm:UnitTimeSlice");
        if (node == null) {
            return;
        }
        this.TimeOrigin = true;
        nodeSearch = null;
        node = getNode(node1, "aixm:name");
//        if (node != null) {
//            return;
//        }
        strCont = strCont + " " + getValueOfNode(node, "aixm:name");
        strCont = strCont.trim();
        if (!strCont.isEmpty()) {
            strCont = strCont.trim();
            NameOfTCAC = true;
        }
        strCont = strCont.trim();
        if (strCont.isEmpty()) {
            return;
        }
        vaaEnt.setStrNameOfTCAC(strCont);
    }
//setStrTimeOrigin

    private void setStrTimeOrigin(Node node1) {
        if (this.TimeOrigin) {
            return;
        }
        String strCont = "";
        nodeSearch = null;
        Node node = getNode(node1, "gml:timePosition");
//        if (node == null) {
//            return;
//        }
        strCont = strCont + " " + getValueOfNode(node, "gml:timePosition");
        strCont = strCont.trim();
        if (!strCont.isEmpty()) {
            strCont = strCont.trim();
            TimeOrigin = true;
        }
        strCont = strCont.trim();
        if (strCont.isEmpty()) {
            return;
        }
        strCont = common.getTime(strCont, "yyyyMMdd/HHmm") + "Z";
        vaaEnt.setStrTimeOrigin(strCont);
    }

    //setNextAdvisory
    private void setNextAdvisory(Node node1) {
        if (NextAdvisory) {
            return;
        }
//        Node node1Nil = node1;
//        if (node1Nil.hasAttributes()) {
//            NamedNodeMap nodeMap = node1Nil.getAttributes();
//            for (int ij = 0; ij < nodeMap.getLength(); ij++) {
//                Node tempNode = nodeMap.item(ij);
//                if (tempNode.getNodeName().toLowerCase().contains("nilreason")) {
//                    String radiusType = tempNode.getNodeValue();
//                    if (radiusType.contains("inapplicable")) {
//                        vaaEnt.setStrNextAdvisory("NO FURTHER ADVISORIES");
//                        this.Remark = true;
//                        NextAdvisory = true;
//                        return;
//                    }
//                }
//            }
//        }
//        Node n = getNode(node1, "gml:timePosition");
//        if (n == null) {
//            return;
//        }
//        this.Remark = true;
//        String strCont = getValueOfNode(n, "gml:timePosition");
//        if (strCont.isEmpty()) {
//            return;
//        }
//        vaaEnt.setStrNextAdvisory(common.getTime(strCont.replace("T", " "), "yyyyMMdd/HHmm") + "Z");
        this.vaaEnt.setStrNextAdvisory(String.format("%s", (node1.getTextContent().trim().isEmpty()) ? "NO FURTHER ADVISORIES" : common.getTime(node1.getTextContent().trim().replace("T", " "), "yyyyMMdd/HHmm") + "Z"));
        this.Remark = true;
        NextAdvisory = true;
    }

    //setRemark 
    private void setRemark(Node node1) {
        if (Remark) {
            return;
        }
        Node node1Nil = getNode(node1, "iwxxm:remarks");
        if (node1Nil.hasAttributes()) {
            NamedNodeMap nodeMap = node1Nil.getAttributes();
            for (int ij = 0; ij < nodeMap.getLength(); ij++) {
                Node tempNode = nodeMap.item(ij);
                if (tempNode.getNodeName().toLowerCase().contains("nilreason")) {
                    String radiusType = tempNode.getNodeValue();
                    if (radiusType.contains("missing")) {
                        this.ForecastHeightAndPosition18 = true;
                        vaaEnt.setStrRemark("NIL");
                        Remark = true;
                        return;
                    }
                }
            }
        }
        String strCont = getValueOfNode(node1, "iwxxm:remarks");
        if (strCont.isEmpty()) {
            return;
        }
        this.ForecastHeightAndPosition18 = true;
//        strCont = common.getTime(strCont, "yyyyMMdd/HHmm") + "Z";
        vaaEnt.setStrRemark(strCont);
        Remark = true;
    }

    private void setForecastHeightAndPosition6(Node node1) {
        if (this.ForecastHeightAndPosition6) {
            return;
        }
        String strCont = setForecastHeightAndPosition(node1, ObservedOrEstimated);
//            strCont = common.addValueToContent(strCont);
        vaaEnt.setStrForecastHeightAndPosition6(strCont);
        this.ForecastHeightAndPosition6 = true;
    }

    private void setForecastHeightAndPosition12(Node node1) {
        if (ForecastHeightAndPosition12) {
            return;
        }
        String strCont = setForecastHeightAndPosition(node1, ForecastHeightAndPosition6);
//            strCont = addValueToContent(strCont);
        vaaEnt.setStrForecastHeightAndPosition12(strCont);
        this.ForecastHeightAndPosition12 = true;
    }

    private void setForecastHeightAndPosition18(Node node1) {
        if (ForecastHeightAndPosition18) {
            return;
        }
        String strCont = setForecastHeightAndPosition(node1, ForecastHeightAndPosition12);
//            strCont = addValueToContent(strCont);
        vaaEnt.setStrForecastHeightAndPosition18(strCont);
        ForecastHeightAndPosition18 = true;
    }

    private String setForecastHeightAndPosition(Node node1, boolean check) {
        String strCont = "";
        //=(strAirspaceVolume + " " )+ strDirectionSpeedOfMotion
        String strPhenomenonTime = "";
        String strAirspaceVolume = "";
        String strDirectionSpeedOfMotion = "";
        nodeSearch = null;
        Node n1 = getNode(node1, "iwxxm:VolcanicAshForecastConditions");
        if (n1 != null) {
            check = true;
            if (n1.hasChildNodes()) {
                NodeList nList = n1.getChildNodes();
                for (int temp = 0; temp < nList.getLength(); temp++) {
                    Node node = nList.item(temp);
                    String strName = node.getNodeName().toLowerCase();
                    if (strName.equals("iwxxm:phenomenontime")) {
                        nodeSearch = null;
                        Node phenomenonTime = getNode(node, "gml:timePosition");
                        if (phenomenonTime != null) {
                            strPhenomenonTime = getValueOfNode(phenomenonTime, "gml:timePosition");
                        }
                    }
                    if (node.getNodeName().equals("iwxxm:ashCloud")) {
                        nodeSearch = null;
                        if (node.hasChildNodes()) {
                            Node n2 = getNode(node, "iwxxm:VolcanicAshCloudForecast");
                            if (n2 != null) {
                                strDirectionSpeedOfMotion = getDirectionOfMotion(n2);
                                nodeSearch = null;
                                Node n3 = getNode(n2, "iwxxm:ashCloudExtent");
                                if (n3 != null) {
                                    if (n3.hasChildNodes()) {
                                        strAirspaceVolume = strAirspaceVolume + " " + getAirspaceVolume(n3);
                                        strAirspaceVolume = strAirspaceVolume.trim();
                                        strAirspaceVolume = strAirspaceVolume + "\n";
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!strPhenomenonTime.isEmpty()) {
            strCont = common.getTime(strPhenomenonTime, "dd/HHmm") + "Z";
        }
        strAirspaceVolume = strAirspaceVolume.trim();
        if (strAirspaceVolume.isEmpty()) {
            strAirspaceVolume = "NO VA EXP";
        }
        strCont = strCont + " " + strAirspaceVolume;
        strDirectionSpeedOfMotion = strDirectionSpeedOfMotion.trim();
        if (!strDirectionSpeedOfMotion.isEmpty()) {
            strCont = strCont + " " + strDirectionSpeedOfMotion;
        }
        return strCont;
    }

    //ObservedOrEstimated
    private String getdirectionOfMotion(String s) {
        String out = "";
        try {
            int v = Integer.parseInt(s);
            out = getdirectionOfMotion(v);
        } catch (NumberFormatException e) {
        }
        return out;
    }

    private String getDirectionOfMotion(Node node) {
        String strDirection = "";
        String strSpeedOfMotion = "";
        String strDirectionSpeedOfMotion = "";
        if (node == null) {
            return "";
        }
        Node directionOfMotion = getNode(node, "iwxxm:directionOfMotion");
        if (directionOfMotion != null) {
            strSpeedOfMotion = getValueOfNode(directionOfMotion, "iwxxm:directionOfMotion");
            if (!strSpeedOfMotion.isEmpty()) {
                strDirection = getdirectionOfMotion(strSpeedOfMotion);
            }
        }
        if (!strDirection.isEmpty()) {
            strDirectionSpeedOfMotion = strDirection;
        }
        Node speedOfMotion = getNode(node, "iwxxm:speedOfMotion");
        if (speedOfMotion != null) {
            strSpeedOfMotion = getValueOfNode(speedOfMotion, "iwxxm:speedOfMotion");
            if (!strSpeedOfMotion.isEmpty()) {
                strSpeedOfMotion = strSpeedOfMotion + "KT";
            }
        }
        if (!strSpeedOfMotion.isEmpty()) {
            strDirectionSpeedOfMotion = strDirectionSpeedOfMotion + " " + strSpeedOfMotion;
        }
        if (!strDirectionSpeedOfMotion.isEmpty()) {
            strDirectionSpeedOfMotion = "MOV " + strDirectionSpeedOfMotion;
        }
        return strDirectionSpeedOfMotion;
    }

    private String getAirspaceVolume(Node n3) {
        String strAirspaceVolume = "";
        String poit = "";
        if (n3 != null) {
            Node n4 = getNode(n3, "aixm:AirspaceVolume");
            if (n4 != null) {
                nodeSearch = null;
                Node upperLimit = getNode(n4, "aixm:upperLimit");
                String up = "";
                String lo = "";
                if (upperLimit != null) {
                    String strValue = getValueOfNode(upperLimit, "aixm:upperLimit");
                    if (!strValue.isEmpty()) {
                        up = strValue;
                    }
                    String strAttribute = getAttribute(upperLimit, "uom");
                    if (!strAttribute.isEmpty()) {
                        up = strAttribute + up;
                    } else {
                        Node vn = getNode(n4, "aixm:upperLimitReference");
                        String v = getValueOfNode(n4, "aixm:upperLimitReference");
                        if (!v.isEmpty()) {
                            up = v;
                        }
                    }
                }
                nodeSearch = null;
                Node lowerLimit = getNode(n4, "aixm:lowerLimit");
                if (lowerLimit != null) {
                    String strValue = getValueOfNode(lowerLimit, "aixm:lowerLimit");
                    if (!strValue.isEmpty()) {
                        lo = strValue;
                    }
                    String strAttribute = getAttribute(lowerLimit, "uom");
                    if (!strAttribute.isEmpty()) {
                        if (up.contains(strAttribute)) {
                            up = up.replace(strAttribute, "");
                        }
                        lo = strAttribute + lo;
                    } else {
                        Node vn = getNode(n4, "aixm:lowerLimitReference");
                        String v = getValueOfNode(vn, "aixm:lowerLimitReference");
                        if (!v.isEmpty()) {
                            lo = v;
                        }
                    }
                }
                if (!lo.isEmpty()) {
                    lo = lo + "/";
                }
                strAirspaceVolume = lo + up;
                nodeSearch = null;
                Node n5 = getNode(n4, "gml:posList");
                if (n5 != null) {
                    poit = getValueOfNode(n5, "gml:posList");
                }
                if (!poit.isEmpty()) {
                    poit = " " + common.getPos(poit);
                }
                strAirspaceVolume = strAirspaceVolume + poit;
            }
        }
        return strAirspaceVolume;
    }

    private Node getNode(Node n, String name2) {
        name2 = name2.toLowerCase();
        if (!n.hasChildNodes()) {
            return nodeSearch;
        }
        NodeList nList = n.getChildNodes();
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node node = nList.item(temp);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if (node.getNodeName().toLowerCase().equals(name2)) {
                    nodeSearch = node;
                    return nodeSearch;
                }
                if (node.hasChildNodes()) {
                    getNode(node, name2);
                }
            }
        }
        return nodeSearch;
    }

    private String getAttribute(Node node1, String name) {
        String out = "";
        Element eElement = (Element) node1;
        if (eElement.getAttribute(name) != null) {
            String oum = eElement.getAttribute(name);
            out = oum.toUpperCase();
        }
        return out;
    }

    private String getValueOfNode(Node node1, String name) {
        String strCont = "";
        name = name.toLowerCase();
        if (name == null) {
            return "";
        }
        String name1 = node1.getNodeName().toLowerCase();
        if (!name1.equals(name)) {
            return "";
        }
        strCont = node1.getTextContent();//node.getTextContent()
        if (!strCont.isEmpty()) {
            strCont = strCont.trim();
        }
        return strCont;
    }
}

