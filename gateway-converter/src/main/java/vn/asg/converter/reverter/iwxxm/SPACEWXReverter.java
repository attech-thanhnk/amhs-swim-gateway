/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.AirspaceVolumePropertyType;
import _int.icao.iwxxm._2023_1.SpaceWeatherAdvisoryType;
import _int.icao.iwxxm._2023_1.SpaceWeatherAnalysisPropertyType;
import _int.icao.iwxxm._2023_1.SpaceWeatherLocationType;
import _int.icao.iwxxm._2023_1.SpaceWeatherPhenomenaType;
import _int.icao.iwxxm._2023_1.SpaceWeatherRegionPropertyType;
import aero.aixm.schema._5_1.AirspaceVolumeType;
import aero.aixm.schema._5_1.CodeUnitType;
import aero.aixm.schema._5_1.SurfacePropertyType;
import aero.aixm.schema._5_1.SurfaceType;
import aero.aixm.schema._5_1.TextNameType;
import aero.aixm.schema._5_1.UnitTimeSliceType;
import aero.aixm.schema._5_1.ValDistanceVerticalType;
import vn.asg.converter.reverter.entity.SPACEWX;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import net.opengis.gml.v_3_2_1.AbstractRingPropertyType;
import net.opengis.gml.v_3_2_1.AbstractRingType;
import net.opengis.gml.v_3_2_1.AbstractSurfacePatchType;
import net.opengis.gml.v_3_2_1.DirectPositionListType;
import net.opengis.gml.v_3_2_1.LinearRingType;
import net.opengis.gml.v_3_2_1.PolygonPatchType;
import net.opengis.gml.v_3_2_1.SurfacePatchArrayPropertyType;
import net.opengis.gml.v_3_2_1.TimeInstantType;

/**
 *
 * @author ThanhNk
 */
public class SPACEWXReverter extends Reverter<SpaceWeatherAdvisoryType, SPACEWX> {

    private int count;
    private final Common common = new Common();

    @Override
    public SPACEWX convert(String content) throws IWXXMParsingException {
        SpaceWeatherAdvisoryType vaa = null;
        try {
            vaa = revert(content, SpaceWeatherAdvisoryType.class);
        } catch (JAXBException ex) {
            throw new IWXXMParsingException("Không đọc được định dạng điện văn.", ex);
        }

        return convert(vaa);
    }

    @Override
    public SPACEWX convert(SpaceWeatherAdvisoryType spaceWeatherX) throws IWXXMParsingException {

        if (spaceWeatherX == null) {
            return null;
        }

        SPACEWX spacewx = new SPACEWX();

        // TODO: Triển khai hàm parse
        this.parse(spaceWeatherX, spacewx);

        return spacewx;

    }

    @Override
    public String convertToString(String content) throws IWXXMParsingException {
        SPACEWX tca = convert(content);
        return tca == null ? null : tca.toString();
    }

    public void parse(SpaceWeatherAdvisoryType objectType, SPACEWX swx) throws IWXXMParsingException {
        if (objectType == null) {
            return;
        }
        count = 0;
        if (objectType.getIssueTime().getTimeInstant().getTimePosition() != null) {
            swx.setTimeOfOrigin(common.getTime(objectType.getIssueTime().getTimeInstant().getTimePosition().getValue().get(0), "yyyyMMdd/HHmm") + "Z");
        }
        UnitTimeSliceType tmp = objectType.getIssuingSpaceWeatherCentre().getUnit().getTimeSlice().get(0).getUnitTimeSlice();
        if (tmp != null) {
            tmp.getRest().forEach((item) -> {
                switch (item.getName().getLocalPart().toUpperCase()) {
                    case "NAME":
                        swx.setNameOfSWXC(String.format("%s", ((TextNameType) item.getValue()).getValue()));
                        break;
                    case "TYPE":
                        swx.setTypeOfReport(String.format("%s", ((CodeUnitType) item.getValue()).getValue()));
                        break;
                }
            });
        }
        swx.setTypeOfReport("SWX ADVISORY");
        swx.setAdvisoryNumber(String.format("%s", objectType.getAdvisoryNumber() != null ? objectType.getAdvisoryNumber().getValue() : ""));
        swx.setNumberOfAdvisoryBeingReplaced(String.format("%s", objectType.getReplacedAdvisoryNumber() != null ? objectType.getReplacedAdvisoryNumber() : ""));
        if (objectType.getPhenomenon() != null && objectType.getPhenomenon().size() > 0) {
            String effect = "";
            for (SpaceWeatherPhenomenaType t : objectType.getPhenomenon()) {
                if (t.getHref() != null) {
                    effect += (t.getHref().substring(t.getHref().lastIndexOf("/") + 1)).replaceAll("_", " ") + " AND ";
                }
            }
            if (effect.endsWith(" AND ")) {
                effect = effect.substring(0, effect.length() - 5);
            }
            swx.setSpaceWeatherEffectIntensity(effect);
        }
        swx.setRemarks(String.format("%s", objectType.getRemarks() != null ? objectType.getRemarks().getValue() : ""));
        TimeInstantType tt = objectType.getNextAdvisoryTime().getTimeInstant();
        swx.setNextAdvisory(String.format("%s", tt != null ? common.getTime(tt.getTimePosition().getValue().get(0), "yyyyMMdd/HHmm") + "Z" : "NO FURTHER ADVISORIES"));
        getForecast(objectType, swx);
    }

    private void getForecast(SpaceWeatherAdvisoryType objectType, SPACEWX swx) {
        if (!objectType.isSetAnalysis()) {
            return;
        }
        List<SpaceWeatherAnalysisPropertyType> list = objectType.getAnalysis();
        String sys = "";
        for (int i = 0; i < list.size(); i++) {
            String pos = "";
            String time = "";
            count++;
            SpaceWeatherAnalysisPropertyType swapt = list.get(i);
            TimeInstantType timeInstantType = (TimeInstantType) swapt.getSpaceWeatherAnalysis().getPhenomenonTime().getAbstractTimeObject().getValue();
            if (timeInstantType != null) {
                time = (timeInstantType.getTimePosition().getValue().get(0) + " ");
                if (!time.isEmpty()) {
                    time = common.getTime(time, "dd/HHmm") + "Z ";
                }
            }
            pos = getAnalysis(swapt);
            if (pos.isEmpty()) {
                pos = time + sys; 
            } else {
                sys = pos;
                pos = time + pos;
            }
            switch (count) {
                case 1:
                    swx.setObservedOrExpectedSpaceWeatherPhenomena(pos);
                    break;
                case 2:
                    swx.setForecastOfThePhenomena6(pos);
                    break;
                case 3:
                    swx.setForecastOfThePhenomena12(pos);
                    break;
                case 4:
                    swx.setForecastOfThePhenomena18(pos);
                    break;
                case 5:
                    swx.setForecastOfThePhenomena24(pos);
                    break;
                default:
                    break;
            }
        }
    }

    private String getAnalysis(SpaceWeatherAnalysisPropertyType item) {
        String out = "";
        try {
            for (SpaceWeatherRegionPropertyType pType : item.getSpaceWeatherAnalysis().getRegion()) {
                String indicator = "";
                String pos = "";
                String airspaceVolume = "";
                if (pType.getSpaceWeatherRegion() != null) {
                    for (JAXBElement<?> element : pType.getSpaceWeatherRegion().getRest()) {
                        switch (element.getName().getLocalPart().toUpperCase()) {
                            case "LOCATION":
                                AirspaceVolumePropertyType v = (AirspaceVolumePropertyType) element.getValue();
                                String strP = getPos(v);
                                if (pos.isEmpty()) {
                                    pos = strP;
                                } else if (!pos.equals(strP)) {
                                    pos = pos + " " + strP;
                                }
                                String Air = getAirspaceVolume(v);
                                if (pos.isEmpty()) {
                                    airspaceVolume = Air;
                                } else if (!airspaceVolume.equals(Air)) {
                                    airspaceVolume = airspaceVolume + " " + Air;
                                }

                                break;
                            case "LOCATIONINDICATOR":
                                SpaceWeatherLocationType swlType = (SpaceWeatherLocationType) element.getValue();
                                if (swlType.getHref() != null) {
                                    String strIn = swlType.getHref().substring(swlType.getHref().lastIndexOf("/") + 1).replaceAll("_", " ");
                                    if (indicator.isEmpty()) {
                                        indicator = strIn;
                                    } else if (!indicator.equals(strIn)) {
                                        indicator = indicator + " " + strIn;
                                    }
                                }
                                break;
                        }
                    }
                } else {
                    if (pType.getNilReason() != null && pType.getNilReason().size() > 0) {
                        indicator += "NO SWX EXP";
                    }
                }
                if (!indicator.isEmpty()) {
                    indicator = indicator.trim() + " ";
                }
                if (!pos.isEmpty()) {
                    pos = pos.trim() + " ";
                }
                if (!airspaceVolume.isEmpty()) {
                    airspaceVolume = airspaceVolume.trim() + " ";
                }
                if (!out.equals(indicator + pos + airspaceVolume)) {
                    out = out + indicator + pos + airspaceVolume;
                }
            }
            out = out.trim();
        } catch (Exception e) {
            Logger.getLogger(SPACEWXReverter.class.getName()).log(Level.SEVERE, null, e);
        }
        return out;
    }

    private String getPos(AirspaceVolumePropertyType v) {
        String out = "";
        if (v == null) {
            return "";
        }
        if (!v.isSetAirspaceVolume()) {
            return "";
        }
        AirspaceVolumeType airspaceVolumeType = v.getAirspaceVolume();
        if (!airspaceVolumeType.isSetHorizontalProjection()) {
            return "";
        }
        JAXBElement<SurfacePropertyType> aXBElement = airspaceVolumeType.getHorizontalProjection();
        if (aXBElement.getValue() == null) {
            return "";
        }
        SurfacePropertyType propertyType = aXBElement.getValue();
        if (!propertyType.isSetSurface()) {
            return "";
        }
        JAXBElement<? extends SurfaceType> element = propertyType.getSurface();
        if (element.getValue() == null) {
            return "";
        }
        SurfaceType xBElement = element.getValue();
//        if (v.getAirspaceVolume().getHorizontalProjection().getValue().getSurface().getValue() != null) {
        if (!xBElement.isSetPatches()) {
            return "";
        }
        JAXBElement<SurfacePatchArrayPropertyType> bElement = xBElement.getPatches();
        if (bElement.getValue() == null) {
            return "";
        }
        SurfacePatchArrayPropertyType arrayPropertyType = bElement.getValue();
        if (!arrayPropertyType.isSetAbstractSurfacePatch()) {
            return "";
        }
        List<JAXBElement<? extends AbstractSurfacePatchType>> ob = arrayPropertyType.getAbstractSurfacePatch();
        int s = ob.size();
        for (int i = 0; i < s; i++) {
            if (ob.get(i).getValue() == null) {
                continue;
            }
            PolygonPatchType patchType = (PolygonPatchType) ob.get(i).getValue();
            if (patchType == null) {
                continue;
            }
            if (!patchType.isSetExterior()) {
                continue;
            }
            AbstractRingPropertyType abstractRingPropertyType = patchType.getExterior();
            if (!abstractRingPropertyType.isSetAbstractRing()) {
                continue;
            }
            JAXBElement<? extends AbstractRingType> jaxbe = abstractRingPropertyType.getAbstractRing();
            if (jaxbe.getValue() == null) {
                continue;
            }
            LinearRingType linearRingType = (LinearRingType) jaxbe.getValue();
            if (!linearRingType.isSetPosList()) {
                continue;
            }
            DirectPositionListType dplt = linearRingType.getPosList();
            if (!dplt.isSetValue()) {
                continue;
            }
            List<Double> list = dplt.getValue();
            if (list != null) {
                String lsPos = common.GetPos(list);
                if (!out.contains(lsPos)) {
                    out = out + lsPos;
                }
            }

        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    private String getAirspaceVolume(AirspaceVolumePropertyType v) {
        String out = "";
        if (v == null) {
            return "";
        }
        String valueUp = "";
        String oumUp = "";
        String valueLow = "";
        String oumLow = "";
        String top = "";
        String maximum = "";
        if (!v.isSetAirspaceVolume()) {
            return "";
        }
        AirspaceVolumeType avt = v.getAirspaceVolume();
        if (avt.isSetMaximumLimit()) {
            if (avt.getMaximumLimit().getValue() != null) {
                if (avt.getMaximumLimit().getValue().getValue() != null) {
                    maximum = avt.getMaximumLimit().getValue().getValue();
                }
                if (maximum.isEmpty()) {
                    if (avt.getMaximumLimit().getValue().getUom() != null) {
                        maximum = avt.getMaximumLimit().getValue().getUom();
                    }
                }
                if (maximum.isEmpty()) {
                    if (avt.getMaximumLimit().getValue().getNilReason() != null) {
                        maximum = avt.getMaximumLimit().getValue().getNilReason();
                    }
                }
            }
        }
        if (avt.isSetUpperLimit()) {
            if (avt.getUpperLimit().getValue() != null) {
                if (avt.getUpperLimit().getValue().getUom() != null) {
                    oumUp = avt.getUpperLimit().getValue().getUom();
                }
                if (avt.getUpperLimit().getValue().getNilReason() != null) {
                    top = avt.getUpperLimit().getValue().getNilReason();
                }
                if (avt.getUpperLimit().getValue().getValue() != null) {
                    valueUp = avt.getUpperLimit().getValue().getValue();
                    if (!valueUp.isEmpty()) {
                        if (oumUp.isEmpty()) {
                            oumUp = "FL";
                        }
                    }
                }
            }

        }
        if (avt.isSetLowerLimit()) {
            ValDistanceVerticalType mi = avt.getMinimumLimit().getValue();
            if (avt.getLowerLimit().getValue() != null) {
                if (avt.getLowerLimit().getValue().getUom() != null) {
                    oumLow = avt.getLowerLimit().getValue().getUom();
                }
                if (avt.getLowerLimit().getValue().getValue() != null) {
                    valueLow = avt.getLowerLimit().getValue().getValue();
                }
                if (avt.getLowerLimit().getValue().getNilReason() != null) {
                    top = avt.getLowerLimit().getValue().getNilReason();
                }
            }
        }
        if (valueUp.length() > 0) {
            int u = Integer.valueOf(valueUp);
            switch (oumUp) {
                case "FL":
                    valueUp = oumUp + formatDoubleToString(u, 3);
                    break;
                case "FT":
                    valueUp = formatDoubleToString(u, 5) + oumUp;
                    break;
                case "M":
                    valueUp = formatDoubleToString(u, 4) + oumUp;
                    break;
                case "SFC":
                    if (u == 0) {
                        valueUp = oumUp;
                    }
                    break;
                default:
                    break;
            }
        }
        if (valueLow.length() > 0) {
            int u = Integer.valueOf(valueLow);
            switch (oumLow) {
                case "FL":
                    valueLow = oumLow + formatDoubleToString(u, 3);
                    break;
                case "FT":
                    valueLow = formatDoubleToString(u, 5) + oumLow;
                    break;
                case "M":
                    valueLow = formatDoubleToString(u, 4) + oumLow;
                    break;
                case "SFC":
                    if (u == 0) {
                        valueLow = oumLow;
                    }
                    break;
                default:
                    break;
            }
        }
        if (top.contains("unknown")) {
            top = "BLW ";
        } else if (top.isEmpty()) {
            if (maximum.contains("unknown")) {
                top = "ABV ";
            } else {
                top = "TOP ";
            }
        }
        if (valueLow.length() > 0) {
            out = out + " " + top + valueUp + "/" + valueLow; //FL 480 van Thieu TOP
        } else if (valueUp.length() > 0) {
            out = out + " " + top + valueUp; //FL 480 van Thieu TOP                                        
        }
        if (!out.isEmpty()) {
            out = out + " ";
        }
        return out;
    }

    public String formatDoubleToString(double a, int s) {
        String out = "";
        try {
            int u = (int) a;
            String v = String.format("%0" + s + "d", u).trim();
            out = v.substring(0, s);
        } catch (Exception e) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, null, e);
        }
        return out;
    }
}

