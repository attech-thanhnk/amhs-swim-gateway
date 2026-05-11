package vn.asg.converter.reverter.iwxxm;

import vn.asg.converter.reverter.entity.TCA;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import static vn.asg.converter.reverter.iwxxm.Common.getSpeedUnlts;

/**
 *
 * @author ThanhNk
 */
public class TCAReverter extends Reverter<NodeList, TCA> {

    private final Common common = new Common();
    private Node nodeSearch = null;
    private final TCA tcaEnt = new TCA();
    private int count = 0;

    @Override
    public String convertToString(String content) throws IWXXMParsingException {
        TCA tca = convert(content);
        return tca == null ? null : tca.toString();
    }

    @Override
    public TCA convert(NodeList node) throws IWXXMParsingException {
        if (node == null) {
            return null;
        }
        visitChildNodes(node);
        return tcaEnt;
    }

    @Override
    public TCA convert(String content) throws IWXXMParsingException {
        NodeList tca = null;
        try {
            tca = revert(content, "iwxxm:TropicalCycloneAdvisory");
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new IWXXMParsingException("Không đọc được định dạng điện văn.", ex);
        }
        return convert(tca);
    }

    private void visitChildNodes(NodeList nList) {
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node node = nList.item(temp);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                switch (node.getNodeName().trim().toLowerCase()) {
                    case "iwxxm:issuetime":
                        if (!node.getTextContent().isEmpty()) {
                            this.tcaEnt.setTimeOfOrigin(common.getTime(String.format("%s", node.getTextContent().trim()), "yyyyMMdd/HHmm") + "Z");
                        }
                        break;
                    case "aixm:designator":
                        if (!node.getTextContent().isEmpty()) {
                            this.tcaEnt.setNameOfTCAC(String.format("%s", node.getTextContent().trim()));
                        }
                        break;
                    case "iwxxm:tropicalcyclonename":
                        if (!node.getTextContent().isEmpty()) {
                            this.tcaEnt.setNameOfTropicalCyclone(String.format("%s", node.getTextContent().trim()));
                        } else {
                            this.tcaEnt.setNameOfTropicalCyclone(String.format("%s", "UNNAMED"));
                        }
                        break;
                    case "iwxxm:advisorynumber":
                        if (!node.getTextContent().isEmpty()) {
                            this.tcaEnt.setAdvisoryNumber(String.format("%s", node.getTextContent().trim()));
                        }
                        break;
                    case "iwxxm:remarks":
                        if (node.getTextContent().trim().isEmpty()) {
                            this.tcaEnt.setRemarks(String.format("%s", "NIL"));
                        } else {
                            this.tcaEnt.setRemarks(String.format("%s", node.getTextContent().trim()));
                        }
                        break;
                    case "iwxxm:nextadvisorytime":
                        this.tcaEnt.setNextAdvisory(String.format("%s", (node.getTextContent().trim().isEmpty()) ? "NO MSG EXP" : common.getTime(node.getTextContent().trim(), "yyyyMMdd/HHmm") + "Z"));
                        break;
                    case "iwxxm:tropicalcycloneposition":
                        tropicalCyclonePosition(node);
                        break;
                    case "iwxxm:movementdirection":
                        if (node.getParentNode().getNodeName().equalsIgnoreCase("iwxxm:TropicalCycloneObservedConditions")) {
                            this.tcaEnt.setMovement(getMovement(String.format("%s", node.getTextContent().trim())));
                        }
                        break;
                    case "iwxxm:movementspeed":
                        this.tcaEnt.setMovementSpeed(getSpeedUnltsTca(getContentNode(node, "iwxxm:TropicalCycloneObservedConditions")));
                        break;
                    case "gml:circlebycenterpoint":
                        circleyyCenterPoin(node);
                        break;
                    case "iwxxm:centralpressure":
                        this.tcaEnt.setCenterPressure(getContentNode(node, "iwxxm:TropicalCycloneObservedConditions").toUpperCase().replace(" ", ""));
                        break;
                    case "iwxxm:maximumsurfacewindspeed":
                        if (node.getParentNode().getNodeName().equalsIgnoreCase("iwxxm:TropicalCycloneObservedConditions")) {
                            this.tcaEnt.setMaximumSurfaceWind(getSpeedUnltsTca(getContentNode(node, "iwxxm:TropicalCycloneObservedConditions")));
                        }
                        break;
                    case "iwxxm:tropicalcycloneforecastconditions":
                        tropicalcyCloneForecastConditions(node);
                        break;
                }
                if (node.hasChildNodes()) {
                    //We got more childs; Let's visit them as well
                    visitChildNodes(node.getChildNodes());
                }

            }
        }
    }

    private void tropicalCyclonePosition(Node node) {
        Node parent = node.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("iwxxm:TropicalCycloneObservedConditions")) {
            return;
        }
        parent = parent.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("iwxxm:observation")) {
            return;
        }
        this.tcaEnt.setPositionOfTheCenter(String.format("%s", (node.getTextContent().trim().isEmpty()) ? "" : common.getPos(node.getTextContent().trim())));
    }

    //circleyycenterpoin
    private void circleyyCenterPoin(Node node) {
        Node parent = node.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("gml:segments")) {
            return;
        }
        parent = parent.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("gml:Curve")) {
            return;
        }
        parent = parent.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("gml:curveMember")) {
            return;
        }
        parent = parent.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("gml:Ring")) {
            return;
        }
        parent = parent.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("gml:exterior")) {
            return;
        }
        parent = parent.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("gml:PolygonPatch")) {
            return;
        }
        parent = parent.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("gml:polygonPatches")) {
            return;
        }
        parent = parent.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("aixm:Surface")) {
            return;
        }
        parent = parent.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("aixm:horizontalProjection")) {
            return;
        }
        parent = parent.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("aixm:AirspaceVolume")) {
            return;
        }
        parent = parent.getParentNode();
        if (!parent.getNodeName().equalsIgnoreCase("iwxxm:cumulonimbusCloudLocation")) {
            return;
        }
        NodeList nods = node.getChildNodes();
        if (nods.getLength() < 1) {
            return;
        }
        for (int i = 0; i < nods.getLength(); i++) {
            String radiusValue = "";
            String pos = "";
            Node nodeItem = nods.item(i);
            if (nodeItem.getNodeName().equalsIgnoreCase("gml:pos")) {
                pos = nodeItem.getTextContent();
                if (pos != null) {
                    if (!pos.isEmpty()) {
                        this.tcaEnt.setCircleByCenterPointDirection(common.getPos(pos));
                    }
                }
            }
            if (nodeItem.getNodeName().equalsIgnoreCase("gml:radius")) {
                radiusValue = "";
                String radiusType = "";
                if (nodeItem.getTextContent() == null) {
                    continue;
                }
                if (nodeItem.getTextContent().isEmpty()) {
                    continue;
                }
                radiusValue = nodeItem.getTextContent();
                if (node.hasAttributes()) {
                    NamedNodeMap nodeMap = nodeItem.getAttributes();
                    for (int ij = 0; ij < nodeMap.getLength(); ij++) {
                        Node tempNode = nodeMap.item(ij);
                        if (tempNode.getNodeName().toLowerCase().equals("uom")) {
                            radiusType = tempNode.getNodeValue();
                            if (!radiusType.isEmpty()) {
                                radiusType = getIwxxmEnum(radiusType);
                            }
                        }
                    }
                }
                radiusValue = radiusValue + radiusType;
                if (!radiusValue.isEmpty()) {
                    this.tcaEnt.setCircleByCenterPointSpeed(radiusValue);
                }
            }
        }
    }

    private String getContentNode(Node node, String nameNode) {
        if (!node.getParentNode().getNodeName().equalsIgnoreCase(nameNode)) {
            return "";
        }
        if (node.getTextContent().isEmpty()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        buffer.append(node.getTextContent().trim());
//        this.tcaEnt.setMaximumSurfaceWind(String.format("%s", node.getTextContent().trim()));
        if (node.hasAttributes()) {
            NamedNodeMap nodeMap = node.getAttributes();
            for (int i = 0; i < nodeMap.getLength(); i++) {
                Node tempNode = nodeMap.item(i);
                if (tempNode.getNodeName().toLowerCase().equals("uom")) {
                    if (!tempNode.getNodeValue().isEmpty()) {
                        buffer.append(" ").append(getSpeedUn(tempNode.getNodeValue()));
//                        buffer.append(getSpeedUn(tempNode.getNodeValue()));
                    }
                }
            }
        }
        return buffer.toString();
    }

    private void tropicalcyCloneForecastConditions(Node node) {
        if (!node.getParentNode().getNodeName().equalsIgnoreCase("iwxxm:forecast")) {
            return;
        }
        String time = "";
        String pos = "";
        String win = "";
        String winType = "";
        count++;
        Node n1 = getNode(node, "iwxxm:phenomenonTime");
        if (n1 != null) {
            Node n11 = getNode(n1, "gml:timePosition");
            if (n11 != null) {
                time = n11.getTextContent();
                if (!time.isEmpty()) {
                    time = common.getTime(time.trim(), "dd/HHmm") + "Z";
                }
            }
        }
        Node n2 = getNode(node, "iwxxm:tropicalCyclonePosition");
        if (n2 != null) {
            Node n22 = getNode(n2, "gml:pos");
            if (n22 != null) {
                pos = n2.getTextContent();
                if (!pos.isEmpty()) {
                    pos = " " + common.getPos(pos);
                }
            }
        }
        Node n3 = getNode(node, "iwxxm:maximumSurfaceWindSpeed");
        if (n3 != null) {
            win = n3.getTextContent();
            NamedNodeMap nodeMap = n3.getAttributes();
            for (int ij = 0; ij < nodeMap.getLength(); ij++) {
                Node tempNode = nodeMap.item(ij);
                if (tempNode.getNodeName().toLowerCase().equals("uom")) {
                    winType = tempNode.getNodeValue();
                    if (!winType.isEmpty()) {
                        winType = getSpeedUnlts(winType);
                    }
                }
            }
            if (!win.isEmpty()) {
                if (!winType.isEmpty()) {
                    win = win + winType;
                }
            }
        }
//        win = addValueToContent(win);
        switch (count) {
            case 1:
                this.tcaEnt.setForecastOfThePhenomena6(time + pos);
                this.tcaEnt.setForecastOfThePhenomenaWin6(win);
                break;
            case 2:
                this.tcaEnt.setForecastOfThePhenomena12(time + pos);
                this.tcaEnt.setForecastOfThePhenomenaWin12(win);
                break;
            case 3:
                this.tcaEnt.setForecastOfThePhenomena18(time + pos);
                this.tcaEnt.setForecastOfThePhenomenaWin18(win);
                break;
            case 4:
                this.tcaEnt.setForecastOfThePhenomena24(time + pos);
                this.tcaEnt.setForecastOfThePhenomenaWin24(win);
                break;
            default:
                break;
        }
    }

    private Node getNode(Node n, String name2) {
        name2 = name2.toLowerCase();
        if (n.hasChildNodes()) {
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
        }
        return nodeSearch;
    }

    //MPS("m/s"), KT("[kn_i]"),KMH("km/h");
    public String getSpeedUnltsTca(String str1) {
        String out = "";
        if (str1 != null && !str1.isEmpty()) {
            String a[] = str1.split(" ");
            if (a.length > 0) {
                String str = a[a.length - 1];
                out = str1.substring(0, str.length() - 1).trim() + str;//getSpeedUn(str);
            }
        }
        return out;
    }

    private String getMovement(String str) {
        StringBuilder buffer = new StringBuilder();
        try {
            if (!str.isEmpty()) {
                double v = Double.parseDouble(str);
                buffer.append(getdirectionOfMotion(v)).append(" ");
            }
        } catch (NumberFormatException ex) {
            Logger.getLogger(TCAReverter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return buffer.toString();
    }
}


