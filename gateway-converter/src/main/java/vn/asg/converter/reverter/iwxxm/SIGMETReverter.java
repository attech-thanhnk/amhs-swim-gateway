/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.AbstractTimeObjectPropertyType;
import _int.icao.iwxxm._2023_1.AeronauticalSignificantWeatherPhenomenonType;
import _int.icao.iwxxm._2023_1.AirspacePropertyType;
import _int.icao.iwxxm._2023_1.AirspaceVolumePropertyType;
import _int.icao.iwxxm._2023_1.AnalysisAndForecastPositionAnalysisType;
import _int.icao.iwxxm._2023_1.AngleWithNilReasonType;
import _int.icao.iwxxm._2023_1.SIGMETEvolvingConditionCollectionPropertyType;
import _int.icao.iwxxm._2023_1.SIGMETExpectedIntensityChangeType;
import _int.icao.iwxxm._2023_1.SIGMETEvolvingConditionCollectionType;
import _int.icao.iwxxm._2023_1.SIGMETEvolvingConditionPropertyType;
import _int.icao.iwxxm._2023_1.SIGMETEvolvingConditionType;
import _int.icao.iwxxm._2023_1.SIGMETExpectedIntensityChangeType;
import _int.icao.iwxxm._2023_1.SIGMETPositionCollectionPropertyType;
import _int.icao.iwxxm._2023_1.SIGMETPositionCollectionType;
import _int.icao.iwxxm._2023_1.SIGMETPositionPropertyType;
import _int.icao.iwxxm._2023_1.SIGMETType;
import _int.icao.iwxxm._2023_1.SIGMETType.AnalysisCollection;
import _int.icao.iwxxm._2023_1.StringWithNilReasonType;
import _int.icao.iwxxm._2023_1.TimeIndicatorType;
import _int.icao.iwxxm._2023_1.UnitPropertyType;
import aero.aixm.schema._5_1.AirspaceTimeSlicePropertyType;
import aero.aixm.schema._5_1.AirspaceType;
import aero.aixm.schema._5_1.AirspaceVolumeType;
import aero.aixm.schema._5_1.CodeAirspaceDesignatorType;
import aero.aixm.schema._5_1.CodeAirspaceType;
import aero.aixm.schema._5_1.CodeOrganisationDesignatorType;
import aero.aixm.schema._5_1.SurfacePropertyType;
import aero.aixm.schema._5_1.SurfaceType;
import aero.aixm.schema._5_1.TextNameType;
import aero.aixm.schema._5_1.UnitTimeSlicePropertyType;
import aero.aixm.schema._5_1.UnitTimeSliceType;
import aero.aixm.schema._5_1.UnitType;
import aero.aixm.schema._5_1.ValDistanceVerticalType;
import vn.asg.converter.reverter.entity.SIGMET;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import net.opengis.gml.v_3_2_1.AbstractRingPropertyType;
import net.opengis.gml.v_3_2_1.AbstractRingType;
import net.opengis.gml.v_3_2_1.AbstractSurfacePatchType;
import net.opengis.gml.v_3_2_1.AbstractTimeObjectType;
import net.opengis.gml.v_3_2_1.AssociationRoleType;
import net.opengis.gml.v_3_2_1.DirectPositionListType;
import net.opengis.gml.v_3_2_1.LinearRingType;
import net.opengis.gml.v_3_2_1.PolygonPatchType;
import net.opengis.gml.v_3_2_1.SpeedType;
import net.opengis.gml.v_3_2_1.StringOrRefType;
import net.opengis.gml.v_3_2_1.SurfacePatchArrayPropertyType;
import net.opengis.gml.v_3_2_1.TimeInstantPropertyType;
import net.opengis.gml.v_3_2_1.TimeInstantType;
import net.opengis.gml.v_3_2_1.TimePeriodPropertyType;
import net.opengis.gml.v_3_2_1.TimePeriodType;
import net.opengis.gml.v_3_2_1.TimePositionType;
import static vn.asg.converter.reverter.iwxxm.Common.getSpeedUnlts;

/**
 *
 * @author ThanhNk
 */
public class SIGMETReverter extends Reverter<SIGMETType, SIGMET> {

    private final Common common = new Common();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("ddHHmm");//hhmm 
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//hhmm
    private final SimpleDateFormat instantTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");//dd/MM/yyyy       
    private String locationIndicatorMWO = "";

    @Override
    public SIGMET convert(String content) throws IWXXMParsingException {
        SIGMETType sigmet = null;
        try {
            sigmet = revert(content, SIGMETType.class);
        } catch (JAXBException ex) {
            throw new IWXXMParsingException("Không đọc được định dạng điện văn.", ex);
        }
        return convert(sigmet);
    }

    @Override
    public String convertToString(String content) throws IWXXMParsingException {
        SIGMET sigmet = convert(content);
        return sigmet == null ? null : sigmet.toString();
    }

    @Override
    public SIGMET convert(SIGMETType objectType) throws IWXXMParsingException {
        if (objectType == null) {
            return null;
        }

        SIGMET sigmet = new SIGMET();

        this.parse(objectType, sigmet);

        return sigmet;

    }

    public void parse(SIGMETType objectType, SIGMET sigmet) throws IWXXMParsingException {
        if (objectType == null) {
            return;
        }

        Date issiuedDate = getDate(objectType.getIssueTime());
//        this.CreateDate = this.tacDateFormat.format(issiuedDate);
//        sigmet.setCreateDate(setCreateDate(objectType)); 
        sigmet.setCreateDate(this.dateTimeFormat.format(issiuedDate));
        //REPORT TYPE 
//        sigmet.setTypeofReport(getTypeofReport());
        //getLocationIndicatorOfFir
        sigmet.setLocationIndicatorOfFir(getLocationIndicatorOfFir(objectType.getIssuingAirTrafficServicesUnit(), false));

        sigmet.setNumberIdentification(getNumberIdentification(objectType.getSequenceNumber().getValue()));//        /Validity period  (M) 

        sigmet.setValidityPeriod(getValidPeriod(objectType.getValidPeriod()));
        //Location indicator of  MWO:
        sigmet.setLocationIndicatorMWO(getLocationIndicatorOfFir(objectType.getOriginatingMeteorologicalWatchOffice(), true));//getLocationIndicatorMWO(objectType.getOriginatingMeteorologicalWatchOffice());    
        this.locationIndicatorMWO = sigmet.getLocationIndicatorMWO();
        //Time from 
        sigmet.setValidFrom(getFrom(objectType.getValidPeriod()));
        //Time to 
        sigmet.setValidTo(getTo(objectType.getValidPeriod()));
        //Name of the FIR/CTA or aircraft identification (M)
        sigmet.setFir(getFir(objectType.getIssuingAirTrafficServicesRegion()));
        // Cancel 
//        if (objectType.isSetCancelledReportValidPeriod()) { 
//            sigmet.setCancellation(objectType);
////            return;
//        }

        //Phenomenon :
        sigmet.setPhenomenon(getPhenomenon(objectType.getPhenomenon()));
        //Date of OBS, Localtion; Levell; Movement; Intensity 

        List<AnalysisCollection> analysisCollections = objectType.getAnalysisCollection();
        if (analysisCollections != null && !analysisCollections.isEmpty()) {
            for (AnalysisCollection analysisItem : analysisCollections) {
                AnalysisAndForecastPositionAnalysisType analysisAndForecasePositionAnalysisType = analysisItem.getAnalysisAndForecastPositionAnalysis();
                if (analysisAndForecasePositionAnalysisType == null) {
                    continue;
                }
                SIGMETEvolvingConditionCollectionPropertyType sigmetEvolvingConditionCollectionPropertyType = analysisAndForecasePositionAnalysisType.getAnalysis();
                if (sigmetEvolvingConditionCollectionPropertyType != null) {
                    proAssociationRoleType(sigmetEvolvingConditionCollectionPropertyType, sigmet);
                    continue;
                }

                SIGMETPositionCollectionPropertyType sigmetPositionCollectionPropertyType = analysisAndForecasePositionAnalysisType.getForecastPositionAnalysis();
                if (sigmetPositionCollectionPropertyType != null) {
                    sigmet.setObsfcst(getObsfcst(sigmetPositionCollectionPropertyType));
                    continue;
                }
            }
        }

        // TODO: CHECKING LATER
        // proAssociationRoleType(objectType.getAnalysis(), sigmet);
        //OBS/FCST :      
        // TODO: CHECKING LATER
        // sigmet.setObsfcst(getObsfcst(objectType.getForecastPositionAnalysis()));
        //RAWW
        sigmet.setRaw(getRaw(objectType.getDescription()));
        //Cancellation : 
        sigmet.setCancellation(getCancellation(objectType));
//        return sigmet;
    }

    private Date getDate(TimeInstantPropertyType timeInstanct) {

        if (timeInstanct == null || !timeInstanct.isSetTimeInstant()) {
            return new Date();
        }

        TimeInstantType timeInstanceType = timeInstanct.getTimeInstant();
        if (!timeInstanceType.isSetTimePosition()) {
            return new Date();
        }

        TimePositionType timePosition = timeInstanceType.getTimePosition();
        if (!timePosition.isSetValue()) {
            return new Date();
        }

        List<String> strList = timePosition.getValue();
        String time = strList.get(0);

        try {

            return instantTimeFormat.parse(time);

        } catch (ParseException ex) {
            System.out.print("Convert date time " + time + " fail." + ex.getMessage());
            return new Date();
        }

    }

    private String getNumberIdentification(StringWithNilReasonType objectType) {
        if (objectType == null) {
            return "";
        }
        if (!objectType.isSetValue()) {
            return "";
        }
        return objectType.getValue();

    }

    private String getLocationIndicatorOfFir(UnitPropertyType objectType, boolean check) {

        if (objectType == null || !objectType.isSetUnit()) {
            return "";
        }
        UnitType unitType = objectType.getUnit();
        if (!unitType.isSetTimeSlice()) {
            return "";
        }
        UnitTimeSlicePropertyType unitSlice = unitType.getTimeSlice().get(0);
        if (!unitSlice.isSetUnitTimeSlice()) {
            return "";
        }
        UnitTimeSliceType unitTimeSlice = unitSlice.getUnitTimeSlice(); //UnitTimeSlice

        List<JAXBElement<?>> jaxbElements = unitTimeSlice.getRest();
        StringBuilder buffer = new StringBuilder();
        for (JAXBElement<?> jaxbElement : jaxbElements) {
            if (!(jaxbElement.getValue() instanceof aero.aixm.schema._5_1.CodeOrganisationDesignatorType)) {
                continue;
            }
            CodeOrganisationDesignatorType code = (CodeOrganisationDesignatorType) jaxbElement.getValue();
            buffer.append(code.getValue());

        }
        if (buffer.toString().isEmpty()) {
            return "";
        }
        if (check) {
            return buffer.toString() + "- \n";
        } else {
            return buffer.toString();
        }
    }

    private String getValidPeriod(TimePeriodPropertyType objectType) {
        if (objectType == null || !objectType.isSetTimePeriod()) {
            return "";
        }
        TimePeriodType periodType = objectType.getTimePeriod();
        StringBuilder buffer = new StringBuilder();
        if (periodType.isSetBeginPosition()) {
            buffer.append(String.format("VALID %s", dateFormat.format(common.parseDatetime(periodType.getBeginPosition()))));
        }
        if (periodType.isSetEndPosition()) {
            buffer.append(String.format("/%s", dateFormat.format(common.parseDatetime(periodType.getEndPosition()))));
        }
        return buffer.toString();
    }

    private String getFrom(TimePeriodPropertyType objectType) {
        if (objectType == null || !objectType.isSetTimePeriod()) {
            return "";
        }
        TimePeriodType periodType = objectType.getTimePeriod();
        if (periodType.isSetBeginPosition()) {
            return String.format("%s", dateTimeFormat.format(common.parseDatetime(periodType.getBeginPosition())));
        }
        return "";
    }

    //getTimeTos --ValidPeriod
    private String getTo(TimePeriodPropertyType objectType) {
        if (objectType == null || !objectType.isSetTimePeriod()) {
            return "";
        }
        TimePeriodType periodType = objectType.getTimePeriod();
        if (periodType.isSetEndPosition()) {
            return String.format("%s", dateTimeFormat.format(common.parseDatetime(periodType.getEndPosition())));
        }
        return "";
    }

    private String getFir(AirspacePropertyType objectType) {
        if (objectType == null || !objectType.isSetAirspace()) {
            return "";
        }
        AirspaceType airspaceType = objectType.getAirspace();
        if (!airspaceType.isSetTimeSlice()) {
            return "";
        }
        List<AirspaceTimeSlicePropertyType> list = airspaceType.getTimeSlice();
        if (list.size() < 1) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
//        List<String> ls = new ArrayList<>(); 
        for (AirspaceTimeSlicePropertyType item : list) {
            if (!item.isSetAirspaceTimeSlice()) {
                continue;
            }
            List<JAXBElement<?>> jaxbElements = item.getAirspaceTimeSlice().getRest();
            String strName = "";
            String strType = "";
            String strDesign = "";
            for (JAXBElement<?> jaxbElement : jaxbElements) {
                if ((jaxbElement.getValue() instanceof aero.aixm.schema._5_1.CodeAirspaceDesignatorType)) {
                    CodeAirspaceDesignatorType code = (CodeAirspaceDesignatorType) jaxbElement.getValue();
                    strDesign = code.getValue();
                    if (strDesign.equals(this.locationIndicatorMWO)) {
                        strDesign = "";
                    } else {
                        strDesign += " ";
                    }
                } else if ((jaxbElement.getValue() instanceof aero.aixm.schema._5_1.TextNameType)) {
                    TextNameType code = (TextNameType) jaxbElement.getValue();
                    strName = code.getValue() + " ";
                } else if ((jaxbElement.getValue() instanceof aero.aixm.schema._5_1.CodeAirspaceType)) {
                    CodeAirspaceType code = (CodeAirspaceType) jaxbElement.getValue();
//                    buffer.append(code.getValue());
                    strType = code.getValue();
                    if (strType.contains("FIR")) {
                        strType = "FIR ";
                    } else if (strType.contains("CTA")) {
                        strType = "CTA ";
                    }
                }
//                else {
//                    continue;
//                }
            }
            if (strName.contains(strType.trim())) {
                strType = "";
            }
            buffer.append(strDesign).append(strName).append(strType);
//            buffer.append(strName);
//            buffer.append(strType);
        }
        return buffer.toString();
    }

    private String getPhenomenon(AeronauticalSignificantWeatherPhenomenonType objectType) {
        if (objectType == null || !objectType.isSetHref()) {
            return "";
        }
        return Common.split(objectType.getHref().replace("_", " "));
    }

    private void proAssociationRoleType(List<AssociationRoleType> list, SIGMET sigmet) {
        if (list == null) {
            return;
        }
        if (list.size() < 1) {
            return;
        }
        StringBuilder dateOfObs = new StringBuilder();
        StringBuilder localtion = new StringBuilder();
        StringBuilder level = new StringBuilder();
        StringBuilder movement = new StringBuilder();
        StringBuilder intensity = new StringBuilder();
//        StringBuilder obsfcst = new StringBuilder();
        for (AssociationRoleType item : list) {
            JAXBElement<SIGMETEvolvingConditionCollectionType> obj = (JAXBElement<SIGMETEvolvingConditionCollectionType>) item.getAny();
            if (obj == null) {
                continue;
            }
            if (obj.getValue() == null) {
                continue;
            }
            dateOfObs.append(getDateOfObs(obj));
            if (!obj.getValue().isSetMember()) {
                continue;
            }
            List<SIGMETEvolvingConditionPropertyType> conditionPropertyTypes = obj.getValue().getMember();
            for (SIGMETEvolvingConditionPropertyType it : conditionPropertyTypes) {
                if (it.isSetNilReason()) {
                    //NO VA EXP - No volcanic ash expected
                    String str = it.getNilReason().get(0);
                    if (str.contains("nothingOfOperationalSignificance")) {
                        continue;
                    }
                }
                localtion.append(getLocaltion(it));
                level.append(getLevel(it));
                movement.append(getMovement(it));
                intensity.append(getIntensity(it));
//                obsfcst.append(getLocaltion(it));
            }
        }
        //Date of OBS :
        sigmet.setDateofObs(dateOfObs.toString());//da ok can kt. moi duoc OBS  thieu thoi gian AT 1210Z    
        //Localtion :
        sigmet.setLocaltion(localtion.toString());
        //Levell :
        sigmet.setLevell(level.toString());
        //Movement :
        sigmet.setMovement(movement.toString());
        //Intensity :
        sigmet.setIntensity(intensity.toString());
    }

    private void proAssociationRoleType(SIGMETEvolvingConditionCollectionPropertyType evolvingConditionCollectionPropertyType, SIGMET sigmet) {
        StringBuilder dateOfObs = new StringBuilder();
        StringBuilder localtion = new StringBuilder();
        StringBuilder level = new StringBuilder();
        StringBuilder movement = new StringBuilder();
        StringBuilder intensity = new StringBuilder();

        SIGMETEvolvingConditionCollectionType sigmetEvolvingConditionCollectionType = evolvingConditionCollectionPropertyType.getSIGMETEvolvingConditionCollection();
        dateOfObs.append(getDateOfObs(sigmetEvolvingConditionCollectionType));
        if (!sigmetEvolvingConditionCollectionType.isSetMember()) {
            return;
        }
        List<SIGMETEvolvingConditionPropertyType> conditionPropertyTypes = sigmetEvolvingConditionCollectionType.getMember();
        for (SIGMETEvolvingConditionPropertyType it : conditionPropertyTypes) {
            if (it.isSetNilReason()) {
                //NO VA EXP - No volcanic ash expected
                String str = it.getNilReason().get(0);
                if (str.contains("nothingOfOperationalSignificance")) {
                    continue;
                }
            }
            localtion.append(getLocaltion(it));
            level.append(getLevel(it));
            movement.append(getMovement(it));
            intensity.append(getIntensity(it));
//                 obsfcst.append(getLocaltion(it));
        }

//        for (AssociationRoleType item : list) {
//            JAXBElement<SIGMETEvolvingConditionCollectionType> obj = (JAXBElement<SIGMETEvolvingConditionCollectionType>) item.getAny();
//            if (obj == null) {
//                continue;
//            }
//            if (obj.getValue() == null) {
//                continue;
//            }
//            dateOfObs.append(getDateOfObs(obj));
//            if (!obj.getValue().isSetMember()) {
//                continue;
//            }
//            List<SIGMETEvolvingConditionPropertyType> conditionPropertyTypes = obj.getValue().getMember();
//            for (SIGMETEvolvingConditionPropertyType it : conditionPropertyTypes) {
//                if (it.isSetNilReason()) {
//                    //NO VA EXP - No volcanic ash expected
//                    String str = it.getNilReason().get(0);
//                    if (str.contains("nothingOfOperationalSignificance")) {
//                        continue;
//                    }
//                }
//                localtion.append(getLocaltion(it));
//                level.append(getLevel(it));
//                movement.append(getMovement(it));
//                intensity.append(getIntensity(it));
////                obsfcst.append(getLocaltion(it));
//            }
//        }
        //Date of OBS :
        sigmet.setDateofObs(dateOfObs.toString());//da ok can kt. moi duoc OBS  thieu thoi gian AT 1210Z    
        //Localtion :
        sigmet.setLocaltion(localtion.toString());
        //Levell :
        sigmet.setLevell(level.toString());
        //Movement :
        sigmet.setMovement(movement.toString());
        //Intensity :
        sigmet.setIntensity(intensity.toString());
    }

    private String getDateOfObs(SIGMETEvolvingConditionCollectionType obj) {
//        String no="NO VA EXP";
        if (obj == null) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        if (!obj.isSetTimeIndicator()) {
            return "";
        }
        String timeIndicatorType = "";
        if (obj.getTimeIndicator().equals(TimeIndicatorType.OBSERVATION)) {
            timeIndicatorType = "OBS";
        } else if (obj.getTimeIndicator().equals(TimeIndicatorType.FORECAST)) {
            timeIndicatorType = "FCST";
        }
        if (timeIndicatorType.isEmpty()) {
            return "";
        }
        buffer.append(timeIndicatorType);
        if (!obj.isSetPhenomenonTime()) {
            return "";
        }
        AbstractTimeObjectPropertyType abstractTimeObjectPropertyType = obj.getPhenomenonTime();
        if (!abstractTimeObjectPropertyType.isSetAbstractTimeObject()) {
            return "";
        }
        JAXBElement<? extends AbstractTimeObjectType> aXBElement = abstractTimeObjectPropertyType.getAbstractTimeObject();
        TimeInstantType instantType = (TimeInstantType) aXBElement.getValue();
        if (instantType == null | !instantType.isSetTimePosition()) {
            return "";
        }
        List<String> l = instantType.getTimePosition().getValue();
        String t = "";

        for (String o : l) {
            t = t + String.format(" %s", common.getTime(o, "HHmm'Z'"));
        }
        t = t.trim();
        if (t.length() > 0) {
            t = " AT " + t;
        }
        buffer.append(t);
        return buffer.toString();
    }

    private String getDateOfObs(JAXBElement<SIGMETEvolvingConditionCollectionType> obj) {
//        String no="NO VA EXP";
        if (obj == null) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        if (!obj.getValue().isSetTimeIndicator()) {
            return "";
        }
        String timeIndicatorType = "";
        if (obj.getValue().getTimeIndicator().equals(TimeIndicatorType.OBSERVATION)) {
            timeIndicatorType = "OBS";
        } else if (obj.getValue().getTimeIndicator().equals(TimeIndicatorType.FORECAST)) {
            timeIndicatorType = "FCST";
        }
        if (timeIndicatorType.isEmpty()) {
            return "";
        }
        buffer.append(timeIndicatorType);
        if (!obj.getValue().isSetPhenomenonTime()) {
            return "";
        }
        AbstractTimeObjectPropertyType abstractTimeObjectPropertyType = obj.getValue().getPhenomenonTime();
        if (!abstractTimeObjectPropertyType.isSetAbstractTimeObject()) {
            return "";
        }
        JAXBElement<? extends AbstractTimeObjectType> aXBElement = abstractTimeObjectPropertyType.getAbstractTimeObject();
        TimeInstantType instantType = (TimeInstantType) aXBElement.getValue();
        if (instantType == null | !instantType.isSetTimePosition()) {
            return "";
        }
        List<String> l = instantType.getTimePosition().getValue();
        String t = "";

        for (String o : l) {
            t = t + String.format(" %s", common.getTime(o, "HHmm'Z'"));
        }
        t = t.trim();
        if (t.length() > 0) {
            t = " AT " + t;
        }
        buffer.append(t);
        return buffer.toString();
    }

    private String getLocaltion(SIGMETEvolvingConditionPropertyType it) {
        StringBuilder buffer = new StringBuilder();
        if (!it.isSetSIGMETEvolvingCondition()) {
            return "";
        }
        SIGMETEvolvingConditionType conditionType = it.getSIGMETEvolvingCondition();
        if (!conditionType.isSetGeometry()) {
            return "";
        }
        AirspaceVolumePropertyType airspaceVolumePropertyType = conditionType.getGeometry();
        if (!airspaceVolumePropertyType.isSetAirspaceVolume()) {
            return "";
        }
        AirspaceVolumeType airspaceVolumeType = airspaceVolumePropertyType.getAirspaceVolume();
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
        JAXBElement<? extends SurfaceType> aXBElement1 = propertyType.getSurface();
        if (aXBElement1.getValue() == null) {
            return "";
        }
        //getPatches().getValue().getAbstractSurfacePatch();
        JAXBElement<SurfacePatchArrayPropertyType> aXBElement2 = aXBElement1.getValue().getPatches();
        if (aXBElement2 == null | !aXBElement2.getValue().isSetAbstractSurfacePatch()) {
            return "";
        }
        List<JAXBElement<? extends AbstractSurfacePatchType>> elements = aXBElement2.getValue().getAbstractSurfacePatch();
        for (JAXBElement<? extends AbstractSurfacePatchType> itt : elements) {
            if ((itt.getValue() instanceof net.opengis.gml.v_3_2_1.PolygonPatchType)) {
                PolygonPatchType patchType = (PolygonPatchType) itt.getValue();
                if (!patchType.isSetExterior()) {
                    continue;
                }
                AbstractRingPropertyType abstractRingPropertyType = patchType.getExterior();
                if (!abstractRingPropertyType.isSetAbstractRing()) {
                    continue;
                }
                JAXBElement<? extends AbstractRingType> jaxbe = abstractRingPropertyType.getAbstractRing();
                if ((jaxbe.getValue() instanceof net.opengis.gml.v_3_2_1.LinearRingType)) {
                    LinearRingType ringType = (LinearRingType) jaxbe.getValue();
                    if (!ringType.isSetPosList()) {
                        continue;
                    }
                    DirectPositionListType positionListType = ringType.getPosList();
                    if (positionListType.isSetValue()) {
                        buffer.append(common.GetPos(positionListType.getValue()));
                    }
                }
            }
        }
        String out = buffer.toString();
        if (!out.isEmpty()) {
            return "WI " + out + " ";
        }
        return out;
    }

    private String getLevel(SIGMETEvolvingConditionPropertyType obj) {
        if (obj == null) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        if (!obj.isSetSIGMETEvolvingCondition()) {
            return "";
        }
        SIGMETEvolvingConditionType conditionType = obj.getSIGMETEvolvingCondition();
        if (!conditionType.isSetGeometry()) {
            return "";
        }
        AirspaceVolumePropertyType airspaceVolumePropertyType = conditionType.getGeometry();
        if (!airspaceVolumePropertyType.isSetAirspaceVolume()) {
            return "";
        }
        AirspaceVolumeType airspaceVolumeType = airspaceVolumePropertyType.getAirspaceVolume();

        String valueUp = "";
        String oumUp = "";
        String nilReasonUp = "";
        String valueLow = "";
        String oumLow = "";
        String nilReasonLow = "";
        String maximum = "";
        String top = "";
        if (airspaceVolumeType.isSetMaximumLimit()) {
            JAXBElement<ValDistanceVerticalType> element = airspaceVolumeType.getMaximumLimit();
            if (element.getValue() != null) {
                if (element.getValue().isSetValue()) {
                    maximum = element.getValue().getValue();
                }

                if (maximum.isEmpty()) {
                    if (element.getValue().isSetUom()) {
                        maximum = element.getValue().getUom();
                    }
                }
                if (maximum.isEmpty()) {
                    if (element.getValue().isSetNilReason()) {
                        maximum = element.getValue().getNilReason();
                    }
                }
            }
        }
        if (airspaceVolumeType.isSetUpperLimit()) {
            JAXBElement<ValDistanceVerticalType> element = airspaceVolumeType.getUpperLimit();
            if (element.getValue() != null) {
                if (element.getValue().isSetValue()) {
                    valueUp = element.getValue().getValue();
                }
                if (element.getValue().isSetUom()) {
                    oumUp = element.getValue().getUom();
                }
                if (element.getValue().isSetNilReason()) {
                    nilReasonUp = element.getValue().getNilReason();
                }
            }
        }
        if (airspaceVolumeType.isSetLowerLimit()) {
            JAXBElement<ValDistanceVerticalType> element = airspaceVolumeType.getLowerLimit();
            if (element.getValue() != null) {
                if (element.getValue().isSetValue()) {
                    valueLow = element.getValue().getValue();
                }
                if (element.getValue().isSetUom()) {
                    oumLow = element.getValue().getUom();
                }
                if (element.getValue().isSetNilReason()) {
                    nilReasonLow = element.getValue().getNilReason();
                }
            }
        }
        if (valueUp.length() > 0) {
            int u = Integer.valueOf(valueUp);
            switch (oumUp) {
                case "FL":
                    valueUp = oumUp + common.formatDoubleToString(u, 3);
                    break;
                case "FT":
                    valueUp = common.formatDoubleToString(u, 5) + oumUp;
                    break;
                case "M":
                    valueUp = common.formatDoubleToString(u, 4) + oumUp;
                    break;
                case "SFC":
                    if (u == 0) {
                        valueUp = oumUp;
                    }
                    break;
                default:
                    oumUp = "FL";
                    valueUp = oumUp + common.formatDoubleToString(u, 3);
                    break;
            }
        }
        if (valueLow.length() > 0) {
            int u = Integer.valueOf(valueLow);
            switch (oumLow) {
                case "FT":
                    valueLow = common.formatDoubleToString(u, 5) + oumLow;
                    break;
                case "M":
                    valueLow = common.formatDoubleToString(u, 4) + oumLow;
                    break;
                case "SFC":
                    if (u == 0) {
                        valueLow = oumLow;
                    }
                    break;
                default:
                    oumLow = "FL";
                    valueLow = oumLow + common.formatDoubleToString(u, 3);
                    break;
            }
        }

        if (nilReasonUp.contains("unknown") | nilReasonLow.contains("unknown")) {
            top = "BLW ";
        } else if (top.isEmpty()) {
            if (maximum.contains("unknown")) {
                top = "ABV ";
            } else {
                top = "TOP ";
            }
        }
        if (valueLow.length() > 0) {
            buffer.append(top).append(valueUp).append("/").append(valueLow); //FL 480 van Thieu TOP
        } else if (valueUp.length() > 0) {
            buffer.append(top).append(valueUp); //FL 480 van Thieu TOP                                        
        }
        buffer.append(" ");

//            }
//        }
        return buffer.toString();
    }

    private String getMovement(SIGMETEvolvingConditionPropertyType obj) {
        if (obj == null) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        if (!obj.isSetSIGMETEvolvingCondition()) {
            return "";
        }
        SIGMETEvolvingConditionType conditionType = obj.getSIGMETEvolvingCondition();
        SpeedType speedOfMotion = null;
        JAXBElement<AngleWithNilReasonType> ang = null;
        if (conditionType.isSetSpeedOfMotion()) {
            speedOfMotion = conditionType.getSpeedOfMotion();
        }
        if (conditionType.isSetDirectionOfMotion()) {
            ang = obj.getSIGMETEvolvingCondition().getDirectionOfMotion();
        }
        if (ang != null) {
            String direction = common.getdirectionOfMotion(ang.getValue().getValue());
            buffer.append(direction).append(" ");
        }
        if (speedOfMotion != null) {
            double valueUp = speedOfMotion.getValue();
            String v = common.formatDoubleToString(valueUp, 2);
            String oum = speedOfMotion.getUom().replace("//", "");
            buffer.append(v).append(getSpeedUnlts(oum)).append(" ");
        } else {
            buffer.append("STNR");
        }
        String c = buffer.toString().trim();
        if (c.isEmpty()) {
            return "";
        } else if (!c.equals("STNR")) {
            return "MOV " + c + " ";
        } else {
            return c + " ";
        }
    }

    private String getIntensity(SIGMETEvolvingConditionPropertyType obj) {
        if (obj == null) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        if (!obj.isSetSIGMETEvolvingCondition()) {
            return "";
        }
        SIGMETEvolvingConditionType conditionType = obj.getSIGMETEvolvingCondition();
        if (!conditionType.isSetIntensityChange()) {
            return "";
        }
        SIGMETExpectedIntensityChangeType changeType = conditionType.getIntensityChange();
        buffer.append(getIntensityChangeType(changeType)).append(" ");
        return buffer.toString();
    }

    private String getIntensityChangeType(SIGMETExpectedIntensityChangeType type) {
        String out = "";
        switch (type) {
            case INTENSIFY:
                out = "INTSF";
                break;
            case WEAKEN:
                out = "WKN";
                break;
            case NO_CHANGE:
                out = "NC";
                break;
            default:
                break;
        }
        return out;
    }

    private String getObsfcst(List<AssociationRoleType> list) {
        if (list == null | list.size() < 1) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        for (AssociationRoleType item : list) {
            if (!item.isSetAny()) {
                continue;
            }
            JAXBElement<SIGMETPositionCollectionType> obj = (JAXBElement<SIGMETPositionCollectionType>) item.getAny();
            if (!obj.getValue().isSetMember()) {
                continue;
            }
            List<SIGMETPositionPropertyType> l = obj.getValue().getMember();
            for (SIGMETPositionPropertyType it : l) {
                List<JAXBElement<? extends AbstractSurfacePatchType>> ll = it.getSIGMETPosition().getGeometry().getAirspaceVolume().getHorizontalProjection().getValue().getSurface().getValue().getPatches().getValue().getAbstractSurfacePatch();
                if (ll == null) {
                    return "";
                }
                for (JAXBElement<? extends AbstractSurfacePatchType> itt : ll) {
                    PolygonPatchType a = (PolygonPatchType) itt.getValue();
                    if (a == null) {
                        continue;
                    }
                    if (!a.isSetExterior()) {
                        continue;
                    }
                    AbstractRingPropertyType abstractRingPropertyType = a.getExterior();
                    if (!abstractRingPropertyType.isSetAbstractRing()) {
                        continue;
                    }
                    JAXBElement<? extends AbstractRingType> b = abstractRingPropertyType.getAbstractRing();
                    if (!(b.getValue() instanceof net.opengis.gml.v_3_2_1.LinearRingType)) {
                        continue;
                    }
                    LinearRingType linearRingType = (LinearRingType) b.getValue();
                    if (linearRingType.isSetPosList()) {
                        List<Double> d = linearRingType.getPosList().getValue();
                        if (d != null) {
                            buffer.append(common.GetPos(d));
                        }
                    }
                }
            }
        }
        return buffer.toString();//
    }

    private String getObsfcst(SIGMETPositionCollectionPropertyType item) {
        if (item == null || item.isSetNilReason()) {
            return "";
        }

        StringBuilder buffer = new StringBuilder();

        SIGMETPositionCollectionType obj = (SIGMETPositionCollectionType) item.getSIGMETPositionCollection();
        if (!obj.isSetMember()) {
            return "";
        }
        
        List<SIGMETPositionPropertyType> l = obj.getMember();
        for (SIGMETPositionPropertyType it : l) {
            List<JAXBElement<? extends AbstractSurfacePatchType>> ll = it.getSIGMETPosition().getGeometry().getAirspaceVolume().getHorizontalProjection().getValue().getSurface().getValue().getPatches().getValue().getAbstractSurfacePatch();
            if (ll == null) {
                return "";
            }
            for (JAXBElement<? extends AbstractSurfacePatchType> itt : ll) {
                PolygonPatchType a = (PolygonPatchType) itt.getValue();
                if (a == null) {
                    continue;
                }
                if (!a.isSetExterior()) {
                    continue;
                }
                AbstractRingPropertyType abstractRingPropertyType = a.getExterior();
                if (!abstractRingPropertyType.isSetAbstractRing()) {
                    continue;
                }
                JAXBElement<? extends AbstractRingType> b = abstractRingPropertyType.getAbstractRing();
                if (!(b.getValue() instanceof net.opengis.gml.v_3_2_1.LinearRingType)) {
                    continue;
                }
                LinearRingType linearRingType = (LinearRingType) b.getValue();
                if (linearRingType.isSetPosList()) {
                    List<Double> d = linearRingType.getPosList().getValue();
                    if (d != null) {
                        buffer.append(common.GetPos(d));
                    }
                }
            }
        }
        return buffer.toString();
    }

//RAWW
    private String getRaw(StringOrRefType objectType) {
        if (objectType == null) {
            return "";
        }
        if (!objectType.isSetValue()) {
            return "";
        }
        return objectType.getValue();
    }

    private String getCancellation(SIGMETType obj) {
        TimePeriodPropertyType objectType = obj.getCancelledReportValidPeriod();
        if (objectType == null) {
            return "";
        }
        if (!objectType.isSetTimePeriod()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        TimePeriodType periodType = objectType.getTimePeriod();
        String from = "";
        String end = "";
        if (obj.isSetCancelledReportSequenceNumber()) {

            buffer.append("CNL SIGMET ").append(obj.getCancelledReportSequenceNumber());
        }
        if (periodType.isSetBeginPosition()) {
            from = String.format("%s", dateFormat.format(common.parseDatetime(periodType.getBeginPosition())));
        }
        if (periodType.isSetEndPosition()) {
            end = String.format("%s", dateFormat.format(common.parseDatetime(periodType.getEndPosition())));
        }
        if (from.isEmpty()) {
            if (!end.isEmpty()) {
                buffer.append(" ");
                buffer.append(end);
            }
        } else {
            if (end.isEmpty()) {
                buffer.append(" ");
                buffer.append(from);
            } else {
                buffer.append(" ");
                buffer.append(from).append("/").append(end);
            }
        }
        return buffer.toString();
    }

}


