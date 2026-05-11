package vn.asg.converter.iwxxm;

import vn.asg.converter.tac.SIGMETTacMessage;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

import org.gamc.gis.model.GTCalculatedRegion;
import org.gamc.gis.model.GTCoordPoint;
import org.gamc.gis.model.GTDirectionFromLine;
import org.gamc.gis.service.GeoServiceException;
import org.gamc.spmi.iwxxmConverter.common.CoordPoint;
import org.gamc.spmi.iwxxmConverter.common.DirectionFromLine;
import org.gamc.spmi.iwxxmConverter.exceptions.ParsingException;
import org.gamc.spmi.iwxxmConverter.iwxxmenums.ANGLE_UNITS;
import org.gamc.spmi.iwxxmConverter.sigmetconverter.SigmetHorizontalPhenomenonLocation;
import org.gamc.spmi.iwxxmConverter.sigmetconverter.SigmetVerticalPhenomenonLocation;
import org.gamc.spmi.iwxxmConverter.wmo.WMONilReasonRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMORegister.WMORegisterException;
import org.joda.time.DateTime;

import _int.icao.iwxxm._2023_1.AbstractTimeObjectPropertyType;
import _int.icao.iwxxm._2023_1.AeronauticalSignificantWeatherPhenomenonType;
import _int.icao.iwxxm._2023_1.AirspacePropertyType;
import _int.icao.iwxxm._2023_1.AirspaceVolumePropertyType;
import _int.icao.iwxxm._2023_1.AnalysisAndForecastPositionAnalysisType;
import _int.icao.iwxxm._2023_1.AngleWithNilReasonType;
import _int.icao.iwxxm._2023_1.PermissibleUsageReasonType;
import _int.icao.iwxxm._2023_1.PermissibleUsageType;
import _int.icao.iwxxm._2023_1.ReportStatusType;
import _int.icao.iwxxm._2023_1.SIGMETEvolvingConditionCollectionPropertyType;
import _int.icao.iwxxm._2023_1.SIGMETEvolvingConditionCollectionType;
import _int.icao.iwxxm._2023_1.SIGMETEvolvingConditionPropertyType;
import _int.icao.iwxxm._2023_1.SIGMETEvolvingConditionType;
import _int.icao.iwxxm._2023_1.SIGMETExpectedIntensityChangeType;
import _int.icao.iwxxm._2023_1.SIGMETPositionCollectionPropertyType;
import _int.icao.iwxxm._2023_1.SIGMETPositionCollectionType;
import _int.icao.iwxxm._2023_1.SIGMETPositionPropertyType;
import _int.icao.iwxxm._2023_1.SIGMETPositionType;
import _int.icao.iwxxm._2023_1.SIGMETType;
import _int.icao.iwxxm._2023_1.StringWithNilReasonType;
import _int.icao.iwxxm._2023_1.TimeIndicatorType;
import _int.icao.iwxxm._2023_1.UnitPropertyType;
import aero.aixm.schema._5_1.AirspaceTimeSlicePropertyType;
import aero.aixm.schema._5_1.AirspaceTimeSliceType;
import aero.aixm.schema._5_1.AirspaceType;
import aero.aixm.schema._5_1.AirspaceVolumeType;
import aero.aixm.schema._5_1.CodeAirspaceDesignatorType;
import aero.aixm.schema._5_1.CodeAirspaceType;
import aero.aixm.schema._5_1.CodeVerticalReferenceType;
import aero.aixm.schema._5_1.SurfacePropertyType;
import aero.aixm.schema._5_1.SurfaceType;
import aero.aixm.schema._5_1.TextNameType;
import aero.aixm.schema._5_1.UnitTimeSlicePropertyType;
import aero.aixm.schema._5_1.UnitTimeSliceType;
import aero.aixm.schema._5_1.UnitType;
import aero.aixm.schema._5_1.ValDistanceVerticalType;
import vn.asg.converter.config.Airport;
import vn.asg.converter.config.Airports;
import vn.asg.converter.config.App;
import net.opengis.gml.v_3_2_1.AbstractRingPropertyType;
import net.opengis.gml.v_3_2_1.AssociationRoleType;
import net.opengis.gml.v_3_2_1.CircleByCenterPointType;
import net.opengis.gml.v_3_2_1.CurvePropertyType;
import net.opengis.gml.v_3_2_1.CurveSegmentArrayPropertyType;
import net.opengis.gml.v_3_2_1.CurveType;
import net.opengis.gml.v_3_2_1.DirectPositionListType;
import net.opengis.gml.v_3_2_1.LengthType;
import net.opengis.gml.v_3_2_1.LinearRingType;
import net.opengis.gml.v_3_2_1.PolygonPatchType;
import net.opengis.gml.v_3_2_1.RingType;
import net.opengis.gml.v_3_2_1.SpeedType;
import net.opengis.gml.v_3_2_1.StringOrRefType;
import net.opengis.gml.v_3_2_1.SurfacePatchArrayPropertyType;
import net.opengis.gml.v_3_2_1.TimeInstantPropertyType;
import net.opengis.gml.v_3_2_1.TimePrimitivePropertyType;
import org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException;

public class SIGMETConverterV3 extends TacBaseConverter<SIGMETTacMessage, SIGMETType> {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(SIGMETConverterV3.class);
    protected String airTrafficUnit = "-FIC";
    protected String watchOfficeType = "-MWO";
    protected String firType = "-OTHER:FIR_UIR";
    protected String interpretation = "-SNAPSHOT";

    protected String dateTime = "";
    protected String dateTimePosition = "";
    protected SIGMETTacMessage translatedSigmet;

    @Override
    public String convertTacToXML(String tac) throws ConvertingFailException {
        try {
            logger.info(String.format("Converting to XML"));
            SIGMETTacMessage sigmetMessage = new SIGMETTacMessage(tac);
            sigmetMessage.parseMessage();
            SIGMETType result = convertMessage(sigmetMessage);
            return marshallMessageToXML(result);
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    @Override
    public byte[] convertTacToXML(String tac, boolean zipped) throws ConvertingFailException {
        try {
            logger.info(String.format("Converting to File"));
            SIGMETTacMessage sigmetMessage = new SIGMETTacMessage(tac);
            SIGMETType result;
            sigmetMessage.parseMessage();
            result = convertMessage(sigmetMessage);
            return marshallMessageToByte(result, zipped);

        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    @Override
    public SIGMETType convertMessage(SIGMETTacMessage translatedMessage)
            throws DatatypeConfigurationException, UnsupportedEncodingException, JAXBException, ParsingException, WMORegisterException {

        interpretation = App.getInstance().getString("Interpretation");
        airTrafficUnit = App.getInstance().getString("SIGMET:AirTrafficUnit");
        watchOfficeType = App.getInstance().getString("SIGMET:WatchOfficeType");
        firType = App.getInstance().getString("SIGMET:FirType");

        this.translatedSigmet = translatedMessage;
        SIGMETType sigmetRootTag = (SIGMETType) iwxxmHelpers.getOfIWXXM().createSIGMETType();

        if (App.getInstance().getBoolean("AddTacContent", false)) {
            StringOrRefType refTacString = iwxxmHelpers.getOfGML().createStringOrRefType();
            refTacString.setValue(translatedMessage.getInitialTacString());
            sigmetRootTag.setDescription(refTacString);
        }
        
        dateTime = translatedMessage.getMessageIssueDateTime().toString(iwxxmHelpers.getDateTimeFormat()) + "Z";
        dateTimePosition = translatedMessage.getMessageIssueDateTime().toString(iwxxmHelpers.getDateTimeISOFormat());

        // Id with ICAO code and current timestamp
        sigmetRootTag.setId(iwxxmHelpers.generateUUIDv4());

        /*
        // sigmetRootTag.setAutomatedStation(true);
        // Set NON_OPERATIONAL and TEST properties.
        sigmetRootTag.setPermissibleUsage(PermissibleUsageType.NON_OPERATIONAL);
        sigmetRootTag.setPermissibleUsageReason(PermissibleUsageReasonType.TEST);

        // Some description
        sigmetRootTag.setPermissibleUsageSupplementary("SIGMET composing test using JAXB");
         */
        PermissibleUsageType permissibleUsageType = PermissibleUsageType.valueOf(App.getInstance().getString("PermissibleUsage"));
        sigmetRootTag.setPermissibleUsage(permissibleUsageType);
        if (permissibleUsageType == PermissibleUsageType.NON_OPERATIONAL) {
            // Non-Operational Reason
            sigmetRootTag.setPermissibleUsageReason(PermissibleUsageReasonType.valueOf(App.getInstance().getString("PermissibleUsageReason")));
            // Some description
            sigmetRootTag.setPermissibleUsageSupplementary(App.getInstance().getString("PermissibleUsageSupplementary"));
        }

        // COR, NIL, NORMAL
        switch (translatedSigmet.getMessageStatusType()) {
            case MISSING:
                sigmetRootTag.setReportStatus(null);
                break;

            case CORRECTION:
                sigmetRootTag.setReportStatus(ReportStatusType.CORRECTION);
                break;

            default:
                sigmetRootTag.setReportStatus(ReportStatusType.NORMAL);
        }
        sigmetRootTag = addTranslationCentreHeader(sigmetRootTag);
        TimeInstantPropertyType obsTimeType = iwxxmHelpers.createTimeInstantPropertyTypeForDateTime(translatedSigmet.getMessageIssueDateTime(), translatedSigmet.getIcaoCode(), "issue");
        sigmetRootTag.setIssueTime(obsTimeType);

        if (translatedSigmet.isNil()) {

            SIGMETEvolvingConditionCollectionPropertyType sigmetEvolvingCoditionCollectionPropertyType = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionCollectionPropertyType();
            sigmetEvolvingCoditionCollectionPropertyType.getNilReason().add(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_MISSING));

            AnalysisAndForecastPositionAnalysisType analysisAndForecastPositionAnalysisType = iwxxmHelpers.getOfIWXXM().createAnalysisAndForecastPositionAnalysisType();
            analysisAndForecastPositionAnalysisType.setAnalysis(sigmetEvolvingCoditionCollectionPropertyType);
            analysisAndForecastPositionAnalysisType.setId(iwxxmHelpers.generateUUIDv4());

            SIGMETType.AnalysisCollection anylysisCollection = iwxxmHelpers.getOfIWXXM().createSIGMETTypeAnalysisCollection();
            anylysisCollection.setAnalysisAndForecastPositionAnalysis(analysisAndForecastPositionAnalysisType);

            sigmetRootTag.getAnalysisCollection().add(anylysisCollection);
            sigmetRootTag.setIssuingAirTrafficServicesUnit(createUnitPropertyTypeNode(translatedSigmet.getIcaoCode(), translatedSigmet.getIcaoCode(), airTrafficUnit, interpretation));
            return sigmetRootTag;
        }

        // Setting SigmetNumber
        StringWithNilReasonType seq = iwxxmHelpers.getOfIWXXM().createStringWithNilReasonType();
        seq.setValue(translatedSigmet.getSigmetNumber());
        JAXBElement<StringWithNilReasonType> seqElement = iwxxmHelpers.getOfIWXXM().createSIGMETTypeSequenceNumber(seq); //iwxxmHelpers.getOfIWXXM().createStringWithNilReason(seq);
        sigmetRootTag.setSequenceNumber(seqElement);

        sigmetRootTag.setIssuingAirTrafficServicesUnit(createUnitPropertyTypeNode(translatedSigmet.getIcaoCode(), translatedSigmet.getIcaoCode(), airTrafficUnit, interpretation));
        sigmetRootTag.setIssuingAirTrafficServicesRegion(createAirspacePropertyTypeNode(translatedSigmet.getIcaoCode(), translatedSigmet.getFirName(), firType, interpretation));
        sigmetRootTag.setOriginatingMeteorologicalWatchOffice(createUnitPropertyTypeNode(translatedSigmet.getIcaoCode(), translatedSigmet.getWatchOffice(), watchOfficeType, interpretation));
        sigmetRootTag.setValidPeriod(iwxxmHelpers.createTimePeriod(translatedSigmet.getIcaoCode(), translatedSigmet.getValidFrom(), translatedSigmet.getValidTo()));

        switch (translatedMessage.getMessageStatusType()) {
            case CANCEL:
                sigmetRootTag.setIsCancelReport(true);
                sigmetRootTag.setCancelledReportSequenceNumber(translatedSigmet.getCancelSigmetNumber());
                sigmetRootTag.setCancelledReportValidPeriod(iwxxmHelpers.createTimePeriod(translatedSigmet.getIcaoCode(), translatedSigmet.getCancelSigmetDateTimeFrom(), translatedSigmet.getCancelSigmetDateTimeTo()));
                break;

            default:
                sigmetRootTag.setPhenomenon(setAeronauticalSignificantWeatherPhenomenonType());

                // SIGMETEvolvingConditionCollectionType
                SIGMETEvolvingConditionCollectionType sigmetEvolvingConditionCollectionType = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionCollectionType();
                setAssociationRoleType(sigmetEvolvingConditionCollectionType);

                // SIGMETEvolvingConditionCollectionPropertyType
                SIGMETEvolvingConditionCollectionPropertyType sigmetEvolvingCoditionCollectionPropertyType = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionCollectionPropertyType();
                sigmetEvolvingCoditionCollectionPropertyType.setSIGMETEvolvingConditionCollection(sigmetEvolvingConditionCollectionType);

                // AnalysisAndForecastPositionAnalysisType
                AnalysisAndForecastPositionAnalysisType analysisAndForecastPositionAnalysisType = iwxxmHelpers.getOfIWXXM().createAnalysisAndForecastPositionAnalysisType();
                analysisAndForecastPositionAnalysisType.setAnalysis(sigmetEvolvingCoditionCollectionPropertyType);
                analysisAndForecastPositionAnalysisType.setId(iwxxmHelpers.generateUUIDv4());

                // AnalysisCollection
                SIGMETType.AnalysisCollection anylysisCollection = iwxxmHelpers.getOfIWXXM().createSIGMETTypeAnalysisCollection();
                anylysisCollection.setAnalysisAndForecastPositionAnalysis(analysisAndForecastPositionAnalysisType);

                // Add to collection
                sigmetRootTag.getAnalysisCollection().add(anylysisCollection);

                // sigmetRootTag.getAnalysis().add(setAssociationRoleType());
                if (translatedSigmet.getPhenomenonDescription().getForecastSection() != null) {
                    // SIGMETPositionCollectionType
                    SIGMETPositionCollectionType sigmetPositionCollectionType = iwxxmHelpers.getOfIWXXM().createSIGMETPositionCollectionType();
                    setForecastAssociationRoleType(sigmetPositionCollectionType);

                    // SIGMETPositionCollectionPropertyType
                    SIGMETPositionCollectionPropertyType sigmetPositinCollectionPropertyType = iwxxmHelpers.getOfIWXXM().createSIGMETPositionCollectionPropertyType();
                    sigmetPositinCollectionPropertyType.setSIGMETPositionCollection(sigmetPositionCollectionType);

                    // AnalysisAndForecastPositionAnalysisType
                    AnalysisAndForecastPositionAnalysisType positionForecastAnalysisType = iwxxmHelpers.getOfIWXXM().createAnalysisAndForecastPositionAnalysisType();
                    positionForecastAnalysisType.setForecastPositionAnalysis(sigmetPositinCollectionPropertyType);
                    positionForecastAnalysisType.setId(iwxxmHelpers.generateUUIDv4());

                    // AnalysisCollection
                    SIGMETType.AnalysisCollection positionAnalysisCollection = iwxxmHelpers.getOfIWXXM().createSIGMETTypeAnalysisCollection();
                    positionAnalysisCollection.setAnalysisAndForecastPositionAnalysis(positionForecastAnalysisType);

                    // Add to collection
                    sigmetRootTag.getAnalysisCollection().add(positionAnalysisCollection);
                }
                break;
        }
        return sigmetRootTag;
    }

    /**
     * created main analysis section for phenomena description
     *
     * @return
     * @throws WMORegisterException
     */
    public AssociationRoleType setAssociationRoleType()
            throws WMORegisterException {
        // ---------------Association Role----------------//
        AssociationRoleType asType = iwxxmHelpers.getOfGML().createAssociationRoleType();

        // ---------------SIGMETEvolvingConditionType(Time)----------------//
        AbstractTimeObjectPropertyType analysisTimeProperty = iwxxmHelpers.createAbstractTimeObject(translatedSigmet.getPhenomenonDescription().getPhenomenonTimeStamp(), translatedSigmet.getIcaoCode());
        // ---------------SIGMETEvolvingConditionType----------------//
        SIGMETEvolvingConditionPropertyType evolvingTypeProp = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionPropertyType();
        // ---------------SIGMETEvolvingConditionType----------------//
        SIGMETEvolvingConditionType evolvingType = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionType();

        SIGMETEvolvingConditionCollectionType sicol = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionCollectionType();
        JAXBElement<SIGMETEvolvingConditionCollectionType> evolvingAr = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionCollection(sicol);

        // ---------------AirspaceVolumePropertyType----------------//
        AirspaceVolumePropertyType air = iwxxmHelpers.getOfIWXXM().createAirspaceVolumePropertyType();

        if (this.translatedSigmet.getHorizontalLocation().isSectionFilled() || this.translatedSigmet.getVerticalLocation().isSectionFilled()) {
            // Create WGS84 coordinate
            List<GTCalculatedRegion> listCoord = getGTCalculatedRegions(this.translatedSigmet.getHorizontalLocation());
            air.setAirspaceVolume(createAirSpaceVolumeSection(listCoord, this.translatedSigmet.getHorizontalLocation(), this.translatedSigmet.getVerticalLocation()));

        } else {
            air = createInapplicablePosition();
        }

        evolvingType.setGeometry(air);
        sicol.setId(iwxxmHelpers.generateUUIDv4(String.format("collection-%s-ts", translatedSigmet.getIcaoCode())));

        // ---------------SIGMETEvolvingConditionType(PhenomenonObservation)----------------//
        if (translatedSigmet.getPhenomenonDescription().getPhenomenonObservation().name().equals("FCST")) {
            sicol.setTimeIndicator(TimeIndicatorType.FORECAST);
        } else if (translatedSigmet.getPhenomenonDescription().getPhenomenonObservation().name().equals("OBS")) {
            sicol.setTimeIndicator(TimeIndicatorType.OBSERVATION);
        }

        evolvingTypeProp.setSIGMETEvolvingCondition(evolvingType);
        sicol.getMember().add(evolvingTypeProp);
        sicol.setPhenomenonTime(analysisTimeProperty);
        asType.setAny(evolvingAr);

        // ---------------SIGMETEvolvingConditionType(Speed-Motion-Id-Intencity)----------------//
        SpeedType speedType = iwxxmHelpers.getOfGML().createSpeedType();
        if (translatedSigmet.getPhenomenonDescription().getMovingSection() != null && translatedSigmet.getPhenomenonDescription().getMovingSection().isMoving()) {

            AngleWithNilReasonType motion = iwxxmHelpers.getOfIWXXM().createAngleWithNilReasonType();
            JAXBElement<AngleWithNilReasonType> dirMo = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionTypeDirectionOfMotion(motion);
            motion.setUom(ANGLE_UNITS.DEGREES.getStringValue());
            motion.setValue(translatedSigmet.getPhenomenonDescription().getMovingSection().getMovingDirection().getDoubleValue());
            evolvingType.setDirectionOfMotion(dirMo);

            if (translatedSigmet.getPhenomenonDescription().getMovingSection().getSpeedUnits() != null && translatedSigmet.getPhenomenonDescription().getMovingSection().getMovingSpeed() > 0) {
                speedType.setUom(translatedSigmet.getPhenomenonDescription().getMovingSection().getSpeedUnits().getStringValue());
                speedType.setValue(translatedSigmet.getPhenomenonDescription().getMovingSection().getMovingSpeed());
                evolvingType.setSpeedOfMotion(speedType);
            }
        }
        evolvingType.setId(iwxxmHelpers.generateUUIDv4(String.format("type-%s-ts", translatedSigmet.getIcaoCode())));
        switch (translatedSigmet.getPhenomenonDescription().getIntencity().name()) {
            case "INTSF":
                evolvingType.setIntensityChange(SIGMETExpectedIntensityChangeType.INTENSIFY);
                break;
            case "WKN":
                evolvingType.setIntensityChange(SIGMETExpectedIntensityChangeType.WEAKEN);
                break;
            case "NC":
                evolvingType.setIntensityChange(SIGMETExpectedIntensityChangeType.NO_CHANGE);
                break;
            default:
                break;
        }
        return asType;
    }

    public void setAssociationRoleType(SIGMETEvolvingConditionCollectionType sigmetEvolvingConditionCollectionType) throws WMORegisterException {

        sigmetEvolvingConditionCollectionType.setId(iwxxmHelpers.generateUUIDv4(String.format("collection-%s-ts", translatedSigmet.getIcaoCode())));

        // ---------------SIGMETEvolvingConditionType(PhenomenonObservation)----------------//
        if (translatedSigmet.getPhenomenonDescription().getPhenomenonObservation().name().equals("FCST")) {
            sigmetEvolvingConditionCollectionType.setTimeIndicator(TimeIndicatorType.FORECAST);
        } else if (translatedSigmet.getPhenomenonDescription().getPhenomenonObservation().name().equals("OBS")) {
            sigmetEvolvingConditionCollectionType.setTimeIndicator(TimeIndicatorType.OBSERVATION);
        }

        // ---------------Association Role----------------//
        // AssociationRoleType asType = iwxxmHelpers.getOfGML().createAssociationRoleType();
        // ---------------SIGMETEvolvingConditionType(Time)----------------//
        AbstractTimeObjectPropertyType analysisTimeProperty = iwxxmHelpers.createAbstractTimeObject(translatedSigmet.getPhenomenonDescription().getPhenomenonTimeStamp(), translatedSigmet.getIcaoCode());
        sigmetEvolvingConditionCollectionType.setPhenomenonTime(analysisTimeProperty);

        // SIGMETEvolvingConditionCollectionType sicol = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionCollectionType();
        // JAXBElement<SIGMETEvolvingConditionCollectionType> evolvingAr = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionCollection(sicol);
        // ---------------AirspaceVolumePropertyType----------------//
        AirspaceVolumePropertyType air = iwxxmHelpers.getOfIWXXM().createAirspaceVolumePropertyType();

        if (this.translatedSigmet.getHorizontalLocation().isSectionFilled() || this.translatedSigmet.getVerticalLocation().isSectionFilled()) {
            // Create WGS84 coordinate
            List<GTCalculatedRegion> listCoord = getGTCalculatedRegions(this.translatedSigmet.getHorizontalLocation());
            air.setAirspaceVolume(createAirSpaceVolumeSection(listCoord, this.translatedSigmet.getHorizontalLocation(), this.translatedSigmet.getVerticalLocation()));

        } else {
            air = createInapplicablePosition();
        }

        // ---------------SIGMETEvolvingConditionType----------------//
        SIGMETEvolvingConditionType evolvingType = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionType();
        evolvingType.setGeometry(air);

        // asType.setAny(evolvingAr);
        // ---------------SIGMETEvolvingConditionType(Speed-Motion-Id-Intencity)----------------//
        SpeedType speedType = iwxxmHelpers.getOfGML().createSpeedType();
        if (translatedSigmet.getPhenomenonDescription().getMovingSection() != null && translatedSigmet.getPhenomenonDescription().getMovingSection().isMoving()) {

            AngleWithNilReasonType motion = iwxxmHelpers.getOfIWXXM().createAngleWithNilReasonType();
            JAXBElement<AngleWithNilReasonType> dirMo = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionTypeDirectionOfMotion(motion);
            motion.setUom(ANGLE_UNITS.DEGREES.getStringValue());
            motion.setValue(translatedSigmet.getPhenomenonDescription().getMovingSection().getMovingDirection().getDoubleValue());
            evolvingType.setDirectionOfMotion(dirMo);

            if (translatedSigmet.getPhenomenonDescription().getMovingSection().getSpeedUnits() != null && translatedSigmet.getPhenomenonDescription().getMovingSection().getMovingSpeed() > 0) {
                speedType.setUom(translatedSigmet.getPhenomenonDescription().getMovingSection().getSpeedUnits().getStringValue());
                speedType.setValue(translatedSigmet.getPhenomenonDescription().getMovingSection().getMovingSpeed());
                evolvingType.setSpeedOfMotion(speedType);
            }
        }
        evolvingType.setId(iwxxmHelpers.generateUUIDv4(String.format("type-%s-ts", translatedSigmet.getIcaoCode())));
        if (translatedSigmet.getPhenomenonDescription().getIntencity().name().equals("INTSF")) {
            evolvingType.setIntensityChange(SIGMETExpectedIntensityChangeType.INTENSIFY);
        } else if (translatedSigmet.getPhenomenonDescription().getIntencity().name().equals("WKN")) {
            evolvingType.setIntensityChange(SIGMETExpectedIntensityChangeType.WEAKEN);
        } else if (translatedSigmet.getPhenomenonDescription().getIntencity().name().equals("NC")) {
            evolvingType.setIntensityChange(SIGMETExpectedIntensityChangeType.NO_CHANGE);
        }

        // ---------------SIGMETEvolvingConditionType----------------//
        SIGMETEvolvingConditionPropertyType evolvingTypeProp = iwxxmHelpers.getOfIWXXM().createSIGMETEvolvingConditionPropertyType();
        evolvingTypeProp.setSIGMETEvolvingCondition(evolvingType);
        sigmetEvolvingConditionCollectionType.getMember().add(evolvingTypeProp);
        // return asType;
    }

    /**
     * * creates SIGMETPositionSection with nilReason if position is unknown
     *
     * @return
     * @throws WMORegisterException
     */
    public AirspaceVolumePropertyType createInapplicablePosition()
            throws WMORegisterException {

        AirspaceVolumePropertyType air = iwxxmHelpers.getOfIWXXM().createAirspaceVolumePropertyType();
        air.getNilReason().add(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_INAPPLICABLE));
        return air;
    }

    /**
     * creates forecast section for phenomena
     *
     * @return
     * @throws WMORegisterException
     */
    public AssociationRoleType setForecastAssociationRoleType()
            throws WMORegisterException {
        AssociationRoleType asType = iwxxmHelpers.getOfGML().createAssociationRoleType();

        // ---------------AirspaceVolumePropertyType----------------//
        // ---------------SIGMETEvolvingConditionType(Time)----------------//
        AbstractTimeObjectPropertyType analysisTimeProperty = iwxxmHelpers.createAbstractTimeObject(
                translatedSigmet.getPhenomenonDescription().getPhenomenonTimeStamp(), translatedSigmet.getIcaoCode());
        // ---------------SIGMETEvolvingConditionType----------------//
        SIGMETPositionPropertyType evolvingTypeProp = iwxxmHelpers.getOfIWXXM().createSIGMETPositionPropertyType();
        // ---------------SIGMETEvolvingConditionType----------------//
        SIGMETPositionType forecastPositionType = iwxxmHelpers.getOfIWXXM().createSIGMETPositionType();

        SIGMETPositionCollectionType sicol = iwxxmHelpers.getOfIWXXM().createSIGMETPositionCollectionType();
        JAXBElement<SIGMETPositionCollectionType> evolvingAr = iwxxmHelpers.getOfIWXXM()
                .createSIGMETPositionCollection(sicol);

        AirspaceVolumePropertyType air = iwxxmHelpers.getOfIWXXM().createAirspaceVolumePropertyType();

        if (translatedSigmet.getPhenomenonDescription().getForecastSection().getHorizontalLocation().isSectionFilled()
                || translatedSigmet.getPhenomenonDescription().getForecastSection().getVerticalLocation()
                        .isSectionFilled()) {

            List<GTCalculatedRegion> listCoord = getGTCalculatedRegions(
                    translatedSigmet.getPhenomenonDescription().getForecastSection().getHorizontalLocation());

            air.setAirspaceVolume(createAirSpaceVolumeSection(listCoord,
                    translatedSigmet.getPhenomenonDescription().getForecastSection().getHorizontalLocation(),
                    translatedSigmet.getPhenomenonDescription().getForecastSection().getVerticalLocation()));
        } else {

            air = createInapplicablePosition();
        }

        forecastPositionType.setGeometry(air);

        sicol.setId(iwxxmHelpers
                .generateUUIDv4(String.format("collection-%s-forecast-ts", translatedSigmet.getIcaoCode())));

        evolvingTypeProp.setSIGMETPosition(forecastPositionType);
        sicol.getMember().add(evolvingTypeProp);
        sicol.setPhenomenonTime(analysisTimeProperty);

        forecastPositionType.setId(
                iwxxmHelpers.generateUUIDv4(String.format("type-%s-forecast-ts", translatedSigmet.getIcaoCode())));

        asType.setAny(evolvingAr);
        return asType;

    }

    public void setForecastAssociationRoleType(SIGMETPositionCollectionType sicol) throws WMORegisterException {

        sicol.setId(iwxxmHelpers.generateUUIDv4(String.format("collection-%s-forecast-ts", translatedSigmet.getIcaoCode())));

        // AssociationRoleType asType = iwxxmHelpers.getOfGML().createAssociationRoleType();
        // ---------------AirspaceVolumePropertyType----------------//
        // ---------------SIGMETEvolvingConditionType(Time)----------------//
        AbstractTimeObjectPropertyType analysisTimeProperty = iwxxmHelpers.createAbstractTimeObject(translatedSigmet.getPhenomenonDescription().getPhenomenonTimeStamp(), translatedSigmet.getIcaoCode());
        sicol.setPhenomenonTime(analysisTimeProperty);

        // SIGMETPositionCollectionType sicol = iwxxmHelpers.getOfIWXXM().createSIGMETPositionCollectionType();
        AirspaceVolumePropertyType air = iwxxmHelpers.getOfIWXXM().createAirspaceVolumePropertyType();

        if (translatedSigmet.getPhenomenonDescription().getForecastSection().getHorizontalLocation().isSectionFilled()
                || translatedSigmet.getPhenomenonDescription().getForecastSection().getVerticalLocation()
                        .isSectionFilled()) {

            List<GTCalculatedRegion> listCoord = getGTCalculatedRegions(
                    translatedSigmet.getPhenomenonDescription().getForecastSection().getHorizontalLocation());

            air.setAirspaceVolume(createAirSpaceVolumeSection(listCoord,
                    translatedSigmet.getPhenomenonDescription().getForecastSection().getHorizontalLocation(),
                    translatedSigmet.getPhenomenonDescription().getForecastSection().getVerticalLocation()));
        } else {

            air = createInapplicablePosition();
        }

        // ---------------SIGMETEvolvingConditionType----------------//
        SIGMETPositionType forecastPositionType = iwxxmHelpers.getOfIWXXM().createSIGMETPositionType();
        forecastPositionType.setGeometry(air);
        forecastPositionType.setId(iwxxmHelpers.generateUUIDv4(String.format("type-%s-forecast-ts", translatedSigmet.getIcaoCode())));

        // ---------------SIGMETEvolvingConditionType----------------//
        SIGMETPositionPropertyType evolvingTypeProp = iwxxmHelpers.getOfIWXXM().createSIGMETPositionPropertyType();
        evolvingTypeProp.setSIGMETPosition(forecastPositionType);
        sicol.getMember().add(evolvingTypeProp);

        // JAXBElement<SIGMETPositionCollectionType> evolvingAr = iwxxmHelpers.getOfIWXXM().createSIGMETPositionCollection(sicol);
        // asType.setAny(evolvingAr);
        // return asType;
    }

    /*
	 * private AirspaceVolumeType setAirspaceVolume() { // ---------------DIR
	 * Position----------------// DirectPositionListType postDir =
	 * iwxxmHelpers.getOfGML().createDirectPositionListType(); for
	 * (DirectionFromLine dir :
	 * translatedSigmet.getHorizontalLocation().getDirectionsFromLines()) {
	 * postDir.getValue().add(dir.getDirection().getDoubleValue()); } //
	 * ---------------Linear Ring----------------// LinearRingType ringAb =
	 * iwxxmHelpers.getOfGML().createLinearRingType(); ringAb.setPosList(postDir);
	 * JAXBElement<LinearRingType> ringAbAr =
	 * iwxxmHelpers.getOfGML().createLinearRing(ringAb); AbstractRingPropertyType
	 * exType = iwxxmHelpers.getOfGML().createAbstractRingPropertyType();
	 * exType.setAbstractRing(ringAbAr); // ---------------Patches----------------//
	 * PolygonPatchType surfacePach =
	 * iwxxmHelpers.getOfGML().createPolygonPatchType();
	 * surfacePach.setExterior(exType); JAXBElement<PolygonPatchType> arrSurf =
	 * iwxxmHelpers.getOfGML().createPolygonPatch(surfacePach);
	 * SurfacePatchArrayPropertyType surPachAr =
	 * iwxxmHelpers.getOfGML().createSurfacePatchArrayPropertyType();
	 * surPachAr.getAbstractSurfacePatch().add(arrSurf); //
	 * ---------------Surface----------------//
	 * JAXBElement<SurfacePatchArrayPropertyType> pathPol =
	 * iwxxmHelpers.getOfGML().createPolygonPatches(surPachAr); BigInteger intDim =
	 * BigInteger.valueOf(2); SurfaceType typeSyr =
	 * iwxxmHelpers.getOfAIXM().createSurfaceType(); typeSyr.setPatches(pathPol);
	 * typeSyr.setSrsDimension(intDim);
	 * typeSyr.setId(iwxxmHelpers.generateUUIDv4(String.format("unit-%d-%s", 1,
	 * translatedSigmet.getIcaoCode()))); typeSyr.getAxisLabels().add("Lat Long");
	 * typeSyr.setSrsName(""); JAXBElement<SurfaceType> surAr =
	 * iwxxmHelpers.getOfAIXM().createSurface(typeSyr); SurfacePropertyType srfType
	 * = iwxxmHelpers.getOfAIXM().createSurfacePropertyType();
	 * srfType.setSurface(surAr); JAXBElement<SurfacePropertyType> surArHor =
	 * iwxxmHelpers.getOfAIXM()
	 * .createAirspaceVolumeTypeHorizontalProjection(srfType); //
	 * ---------------Distance Vertical----------------// ValDistanceVerticalType
	 * valDisUp = iwxxmHelpers.getOfAIXM().createValDistanceVerticalType();
	 * valDisUp.setUom("FL");
	 * valDisUp.setValue(String.valueOf(translatedSigmet.getVerticalLocation().
	 * getTopFL().get())); JAXBElement<ValDistanceVerticalType> valDisJaxUp =
	 * iwxxmHelpers.getOfAIXM() .createAirspaceLayerTypeUpperLimit(valDisUp); //
	 * ---------------Vertical Refeence----------------// CodeVerticalReferenceType
	 * valueCode = iwxxmHelpers.getOfAIXM().createCodeVerticalReferenceType();
	 * valueCode.setValue("STD"); JAXBElement<CodeVerticalReferenceType>
	 * vertCodeType = iwxxmHelpers.getOfAIXM()
	 * .createAirspaceLayerTypeUpperLimitReference(valueCode); //
	 * ---------------Airspace Volume----------------// AirspaceVolumeType airS =
	 * iwxxmHelpers.getOfAIXM().createAirspaceVolumeType();
	 * airS.setUpperLimit(valDisJaxUp); airS.setHorizontalProjection(surArHor);
	 * airS.setUpperLimitReference(vertCodeType); // ---------------Curve
	 * Property----------------// CurvePropertyType curvePropType =
	 * iwxxmHelpers.getOfAIXM().createCurvePropertyType();
	 * JAXBElement<CurvePropertyType> curveProp = iwxxmHelpers.getOfAIXM()
	 * .createAirspaceVolumeTypeCentreline(curvePropType);
	 * airS.setCentreline(curveProp); return airS; }
     */
    /**
     * Coordinates calculation from horrisontal location
     *
     * @param location
     * @return
     */
    public List<GTCalculatedRegion> getGTCalculatedRegions(SigmetHorizontalPhenomenonLocation location) {

        try {
            // Sigmet phenomena within polygon (WI)
            if (location.isInPolygon()) {
                LinkedList<GTCoordPoint> listPolygonPoints = new LinkedList<>();
                location.getPolygonPoints().stream().forEach((CoordPoint arg0) -> {
                    listPolygonPoints.add(arg0.toGTCoordPoint());
                });
                if (!listPolygonPoints.isEmpty()) {
                    return iwxxmHelpers.calculateWGS84Point(location.getPolygonPoints());
                    // return iwxxmHelpers.getGeoService().recalcFromPolygon(translatedSigmet.getFirCode(), listPolygonPoints);
                }
            }

            if (location.isEntireFIR()) {
                return iwxxmHelpers.getGeoService().recalcEntireFir(translatedSigmet.getFirCode());
            }

            if (location.isSinglePoint()) {
                List<GTCalculatedRegion> result = new LinkedList<>();
                result.add(iwxxmHelpers.getGeoService().recalcFromSinglePoint(location.getPoint().toGTCoordPoint()));
                return result;

            }

            if (location.isWithinCorridor()) {
                throw new IllegalArgumentException("NOT IMPLEMENTED YET");
            }

            if (location.isWithinRadius()) {
                List<GTCalculatedRegion> result = new LinkedList<>();
                result.add(iwxxmHelpers.getGeoService().recalcFromSinglePoint(location.getPoint().toGTCoordPoint()));
                return result;
            }

            LinkedList<GTDirectionFromLine> listLines = new LinkedList<>();
            location.getDirectionsFromLines().stream().forEach((DirectionFromLine arg0) -> {
                listLines.add(arg0.toGTDirectionFromLine());
            });
            if (!listLines.isEmpty()) {
                return iwxxmHelpers.getGeoService().recalcFromLines(translatedSigmet.getFirCode(), listLines);
            }

        } catch (URISyntaxException e) {
            logger.error("Unable to calculate coordinates", e);
        } catch (GeoServiceException e) {
            logger.error("Unable to calculate coordinates", e);
        }

        return new LinkedList<>();

    }

    /**
     * returns section for airspaceVolumeDescription
     *
     * @param coordsRegion
     * @param horizontalLocation
     * @param verticalLocation
     * @return
     * @throws WMORegisterException
     */
    public AirspaceVolumeType createAirSpaceVolumeSection(List<GTCalculatedRegion> coordsRegion, SigmetHorizontalPhenomenonLocation horizontalLocation, SigmetVerticalPhenomenonLocation verticalLocation)
            throws WMORegisterException {

        AirspaceVolumeType airspaceVolumeType = iwxxmHelpers.getOfAIXM().createAirspaceVolumeType();
        airspaceVolumeType.setId(iwxxmHelpers.generateUUIDv4("airspace-" + translatedSigmet.getIcaoCode()));

        // lower limit if exists and check if on surface
        if (verticalLocation.getBottomFL().isPresent()) {
            ValDistanceVerticalType bottomFlType = iwxxmHelpers.getOfAIXM().createValDistanceVerticalType();

            bottomFlType.setUom("FL");
            bottomFlType.setValue(String.valueOf(verticalLocation.getBottomFL().get()));
            JAXBElement<ValDistanceVerticalType> bottomFlSection = iwxxmHelpers.getOfAIXM()
                    .createAirspaceLayerTypeLowerLimit(bottomFlType);
            airspaceVolumeType.setLowerLimit(bottomFlSection);

            CodeVerticalReferenceType valueCode = iwxxmHelpers.getOfAIXM().createCodeVerticalReferenceType();
            valueCode.setValue("STD");
            JAXBElement<CodeVerticalReferenceType> vertCodeType = iwxxmHelpers.getOfAIXM()
                    .createAirspaceLayerTypeLowerLimitReference(valueCode);
            airspaceVolumeType.setLowerLimitReference(vertCodeType);
        }

        // upper limit if exists
        if (verticalLocation.getTopFL().isPresent()) {

            ValDistanceVerticalType topFlType = iwxxmHelpers.getOfAIXM().createValDistanceVerticalType();
            topFlType.setUom("FL");

            if (verticalLocation.isTopMarginAboveFl()) {

                ValDistanceVerticalType unknownType = iwxxmHelpers.getOfAIXM().createValDistanceVerticalType();
                unknownType.setNilReason(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_UNKNOWN));
                JAXBElement<ValDistanceVerticalType> unknownSection = iwxxmHelpers.getOfAIXM()
                        .createAirspaceLayerTypeUpperLimit(unknownType);
                airspaceVolumeType.setMaximumLimit(unknownSection);
                topFlType.setValue(String.valueOf(verticalLocation.getTopFL().get()));
            } // Below top fl - set max as top fl, set upper as unknown
            else if (verticalLocation.isTopMarginBelowFl()) {

                ValDistanceVerticalType topMaxType = iwxxmHelpers.getOfAIXM().createValDistanceVerticalType();
                topMaxType.setValue(String.valueOf(verticalLocation.getTopFL().get()));

                JAXBElement<ValDistanceVerticalType> topMaxSection = iwxxmHelpers.getOfAIXM()
                        .createAirspaceLayerTypeUpperLimit(topMaxType);
                airspaceVolumeType.setMaximumLimit(topMaxSection);

                topFlType.setNilReason(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_UNKNOWN));
            } else {
                topFlType.setValue(String.valueOf(verticalLocation.getTopFL().get()));
            }

            JAXBElement<ValDistanceVerticalType> topFlSection = iwxxmHelpers.getOfAIXM()
                    .createAirspaceLayerTypeUpperLimit(topFlType);
            airspaceVolumeType.setUpperLimit(topFlSection);

            CodeVerticalReferenceType valueCode = iwxxmHelpers.getOfAIXM().createCodeVerticalReferenceType();
            valueCode.setValue("STD");
            JAXBElement<CodeVerticalReferenceType> vertCodeType = iwxxmHelpers.getOfAIXM()
                    .createAirspaceLayerTypeUpperLimitReference(valueCode);
            airspaceVolumeType.setUpperLimitReference(vertCodeType);

        }

        // if height is observed in feet or meters and low bound is on surface
        if (verticalLocation.isBottomMarginOnSurface() && verticalLocation.getTopMarginMeters().isPresent()) {
            ValDistanceVerticalType bottomFlType = iwxxmHelpers.getOfAIXM().createValDistanceVerticalType();
            bottomFlType.setUom(verticalLocation.getUnits().getStringValue());
            bottomFlType.setValue("0");
            JAXBElement<ValDistanceVerticalType> bottomFlSection = iwxxmHelpers.getOfAIXM()
                    .createAirspaceLayerTypeUpperLimit(bottomFlType);
            airspaceVolumeType.setLowerLimit(bottomFlSection);

            ValDistanceVerticalType topHeightType = iwxxmHelpers.getOfAIXM().createValDistanceVerticalType();
            topHeightType.setUom(verticalLocation.getUnits().getStringValue());
            topHeightType.setValue(String.valueOf(verticalLocation.getTopMarginMeters().get()));
            JAXBElement<ValDistanceVerticalType> bottomHeightSection = iwxxmHelpers.getOfAIXM()
                    .createAirspaceLayerTypeUpperLimit(topHeightType);
            airspaceVolumeType.setLowerLimit(bottomHeightSection);

            // set flag from SURFACE - see aixm 5.1.1
            CodeVerticalReferenceType valueCodeSfc = iwxxmHelpers.getOfAIXM().createCodeVerticalReferenceType();
            valueCodeSfc.setValue("SFC");
            JAXBElement<CodeVerticalReferenceType> vertCodeTypeSfc = iwxxmHelpers.getOfAIXM()
                    .createAirspaceLayerTypeUpperLimitReference(valueCodeSfc);

            CodeVerticalReferenceType valueCodeStd = iwxxmHelpers.getOfAIXM().createCodeVerticalReferenceType();
            valueCodeSfc.setValue("STD");
            JAXBElement<CodeVerticalReferenceType> vertCodeTypeStd = iwxxmHelpers.getOfAIXM()
                    .createAirspaceLayerTypeUpperLimitReference(valueCodeStd);

            airspaceVolumeType.setUpperLimitReference(vertCodeTypeStd);
            airspaceVolumeType.setLowerLimitReference(vertCodeTypeSfc);
        }

        // create projection
        SurfacePropertyType surfaceSection = iwxxmHelpers.getOfAIXM().createSurfacePropertyType();
        SurfaceType sfType = iwxxmHelpers.getOfAIXM().createSurfaceType();
        sfType.getAxisLabels().add("Lat");
        sfType.getAxisLabels().add("Long");
        sfType.setSrsDimension(BigInteger.valueOf(2));

        // TODO: FUCKING SHIT
        // sfType.setSrsName("http://www.opengis.net/def/crs/EPSG/0/4326");
        Airport airport = App.getInstance().getAirportDefine(translatedSigmet.getIcaoCode());
        if (airport != null) {
            sfType.setSrsName(airport.getElevatedPoint().getSrsName());
        } else {
            sfType.setSrsName(App.getInstance().getString("DefaultSourceName"));
        }

        sfType.setId(iwxxmHelpers.generateUUIDv4("surface-" + translatedSigmet.getIcaoCode()));

        // add gml patches
        SurfacePatchArrayPropertyType patchArray = iwxxmHelpers.getOfGML().createSurfacePatchArrayPropertyType();

        // Create patch for all coordinate regions
        if (horizontalLocation.isInPolygon()) {

            // create polygon
            PolygonPatchType patchType = iwxxmHelpers.getOfGML().createPolygonPatchType();
            LinearRingType linearRingType = iwxxmHelpers.getOfGML().createLinearRingType();

            // fill polygon with coords
            DirectPositionListType dpListType = iwxxmHelpers.getOfGML().createDirectPositionListType();
            for (GTCalculatedRegion gtCoordsRegion : coordsRegion) {
                dpListType.getValue().addAll(gtCoordsRegion.getCoordinates());
            }
//            dpListType.getValue().addAll(coords);
//            dpListType.setCount(BigInteger.valueOf(coords.size()));
            linearRingType.setPosList(dpListType);

            // put polygon in the envelope
            JAXBElement<LinearRingType> lrPt = iwxxmHelpers.getOfGML().createLinearRing(linearRingType);
            AbstractRingPropertyType ringType = iwxxmHelpers.getOfGML().createAbstractRingPropertyType();
            ringType.setAbstractRing(lrPt);
            patchType.setExterior(ringType);

            JAXBElement<PolygonPatchType> patch = iwxxmHelpers.getOfGML().createPolygonPatch(patchType);
            patchArray.getAbstractSurfacePatch().add(patch);
            JAXBElement<SurfacePatchArrayPropertyType> pta = iwxxmHelpers.getOfGML().createPatches(patchArray);
            sfType.setPatches(pta);

        }

        for (GTCalculatedRegion gtCoordsRegion : coordsRegion) {
            LinkedList<Double> coords = gtCoordsRegion.getCoordinates();

            /*
            if (horizontalLocation.isInPolygon()) {

                // create polygon
                PolygonPatchType patchType = iwxxmHelpers.getOfGML().createPolygonPatchType();
                LinearRingType linearRingType = iwxxmHelpers.getOfGML().createLinearRingType();

                // fill polygon with coords
                DirectPositionListType dpListType = iwxxmHelpers.getOfGML().createDirectPositionListType();
                dpListType.getValue().addAll(coords);
                dpListType.setCount(BigInteger.valueOf(coords.size()));
                linearRingType.setPosList(dpListType);

                // put polygon in the envelope
                JAXBElement<LinearRingType> lrPt = iwxxmHelpers.getOfGML().createLinearRing(linearRingType);
                AbstractRingPropertyType ringType = iwxxmHelpers.getOfGML().createAbstractRingPropertyType();
                ringType.setAbstractRing(lrPt);
                patchType.setExterior(ringType);

                JAXBElement<PolygonPatchType> patch = iwxxmHelpers.getOfGML().createPolygonPatch(patchType);

                patchArray.getAbstractSurfacePatch().add(patch);

                JAXBElement<SurfacePatchArrayPropertyType> pta = iwxxmHelpers.getOfGML().createPatches(patchArray);

                sfType.setPatches(pta);

            }
             */
            if (horizontalLocation.isWithinRadius()) {
                // create ring with radius
                PolygonPatchType patchType = iwxxmHelpers.getOfGML().createPolygonPatchType();
                RingType ringType = iwxxmHelpers.getOfGML().createRingType();

                CurvePropertyType curveProperty = iwxxmHelpers.getOfGML().createCurvePropertyType();
                CurveType curve = iwxxmHelpers.getOfGML().createCurveType();
                curve.setId(iwxxmHelpers.generateUUIDv4("curve-" + translatedSigmet.getIcaoCode()));

                CurveSegmentArrayPropertyType segmentsArray = iwxxmHelpers.getOfGML().createCurveSegmentArrayPropertyType();

                CircleByCenterPointType centerCircle = iwxxmHelpers.getOfGML().createCircleByCenterPointType();

                //center
                DirectPositionListType dpListType = iwxxmHelpers.getOfGML().createDirectPositionListType();
                dpListType.getValue().addAll(coords);
                dpListType.setCount(BigInteger.valueOf(coords.size()));
                centerCircle.setPosList(dpListType);

                //radius
                LengthType radius = iwxxmHelpers.getOfGML().createLengthType();
                radius.setUom(horizontalLocation.getWidenessUnits().getStringValue());
                radius.setValue(horizontalLocation.getWideness());
                centerCircle.setRadius(radius);

                JAXBElement<CircleByCenterPointType> centerCircleElement = iwxxmHelpers.getOfGML().createCircleByCenterPoint(centerCircle);
                segmentsArray.getAbstractCurveSegment().add(centerCircleElement);
                curve.setSegments(segmentsArray);

                JAXBElement<CurveType> curveElement = iwxxmHelpers.getOfGML().createCurve(curve);

                curveProperty.setAbstractCurve(curveElement);
                ringType.getCurveMember().add(curveProperty);

                // put polygon in the envelope
                JAXBElement<RingType> lrPt = iwxxmHelpers.getOfGML().createRing(ringType);
                AbstractRingPropertyType abstractRingType = iwxxmHelpers.getOfGML().createAbstractRingPropertyType();
                abstractRingType.setAbstractRing(lrPt);
                patchType.setExterior(abstractRingType);

                JAXBElement<PolygonPatchType> patch = iwxxmHelpers.getOfGML().createPolygonPatch(patchType);

                patchArray.getAbstractSurfacePatch().add(patch);

                JAXBElement<SurfacePatchArrayPropertyType> pta = iwxxmHelpers.getOfGML().createPolygonPatches(patchArray);

                sfType.setPatches(pta);
            }

        }

        JAXBElement<SurfaceType> syrfaceElement = iwxxmHelpers.getOfAIXM().createSurface(sfType);
        surfaceSection.setSurface(syrfaceElement);

        JAXBElement<SurfacePropertyType> spt = iwxxmHelpers.getOfAIXM()
                .createAirspaceVolumeTypeHorizontalProjection(surfaceSection);
        // create aixm:horizontalProjection
        airspaceVolumeType.setHorizontalProjection(spt);

        return airspaceVolumeType;

    }

    /**
     * Get link for WMO register record for the phenomena
     *
     * @return
     * @throws WMORegisterException
     */
    public AeronauticalSignificantWeatherPhenomenonType setAeronauticalSignificantWeatherPhenomenonType() throws WMORegisterException {
        AeronauticalSignificantWeatherPhenomenonType typePhen = iwxxmHelpers.getOfIWXXM().createAeronauticalSignificantWeatherPhenomenonType();
        String link = iwxxmHelpers.getSigWxPhenomenaRegister().getWMOUrlByCode(translatedSigmet.getPhenomenonDescription().getPhenomenonForLink());
        typePhen.setHref(link);
        return typePhen;

    }

    public UnitPropertyType createUnitPropertyTypeNode(String icaoCode, String firname, String type, String interpretation) {
        UnitPropertyType pt = iwxxmHelpers.getOfIWXXM().createUnitPropertyType();

        UnitType ut = iwxxmHelpers.getOfAIXM().createUnitType();
        ut.setId(iwxxmHelpers.generateUUIDv4(String.format("unit-%s-%s", icaoCode, firname)));

        pt.setUnit(ut);

        UnitTimeSlicePropertyType tspt = iwxxmHelpers.getOfAIXM().createUnitTimeSlicePropertyType();
        UnitTimeSliceType tst = iwxxmHelpers.getOfAIXM().createUnitTimeSliceType();
        tst.setId(iwxxmHelpers.generateUUIDv4(String.format("unit-%s-%s-ts", icaoCode, firname)));
        tst.setInterpretation(interpretation);

        // TODO: Ask the team if it is nessessary
        TimePrimitivePropertyType emptyTime = iwxxmHelpers.getOfGML().createTimePrimitivePropertyType();
        tst.setValidTime(emptyTime);

        tspt.setUnitTimeSlice(tst);

        /**
         * <aixm:interpretation>SNAPSHOT</aixm:interpretation>
         * <aixm:type>OTHER:FIR_UIR</aixm:type> <aixm:designator>YUDD</aixm:designator>
         * <aixm:name>SHANLON FIR/UIR</aixm:name>
         *
         *
         */
        // add name
        TextNameType nType = iwxxmHelpers.getOfAIXM().createTextNameType();
        nType.setValue(firname + " " + type);
        JAXBElement<TextNameType> ntType = iwxxmHelpers.getOfAIXM().createAirspaceTimeSliceTypeName(nType);
        tst.getRest().add(ntType);

        // add type
        CodeAirspaceType asType = iwxxmHelpers.getOfAIXM().createCodeAirspaceType();
        asType.setValue(type);
        JAXBElement<CodeAirspaceType> astType = iwxxmHelpers.getOfAIXM().createAirspaceTimeSliceTypeType(asType);
        tst.getRest().add(astType);

        // add designator
        CodeAirspaceDesignatorType cadType = iwxxmHelpers.getOfAIXM().createCodeAirspaceDesignatorType();
        cadType.setValue(firname);
        JAXBElement<CodeAirspaceDesignatorType> cast = iwxxmHelpers.getOfAIXM()
                .createAirspaceTimeSliceTypeDesignator(cadType);
        tst.getRest().add(cast);

        ut.getTimeSlice().add(tspt);

        return pt;
    }

    /**
     * Airspace for issuing center
     *
     * @param icaoCode
     * @param firname
     * @param type
     * @param interpretation
     * @return
     */
    public AirspacePropertyType createAirspacePropertyTypeNode(String icaoCode, String firname, String type, String interpretation) {

        AirspacePropertyType pt = iwxxmHelpers.getOfIWXXM().createAirspacePropertyType();
        AirspaceType ast = iwxxmHelpers.getOfAIXM().createAirspaceType();
        ast.setId(iwxxmHelpers.generateUUIDv4(String.format("airspace-%s", icaoCode)));
        pt.setAirspace(ast);

        AirspaceTimeSlicePropertyType tsp = iwxxmHelpers.getOfAIXM().createAirspaceTimeSlicePropertyType();
        AirspaceTimeSliceType ts = iwxxmHelpers.getOfAIXM().createAirspaceTimeSliceType();
        ts.setId(iwxxmHelpers.generateUUIDv4(String.format("airspace-%s-ts", icaoCode)));

        // TODO: Ask the team if it is nessessary
        TimePrimitivePropertyType emptyTime = iwxxmHelpers.getOfGML().createTimePrimitivePropertyType();
        ts.setValidTime(emptyTime);

        ts.setInterpretation(interpretation);

        /**
         * <aixm:interpretation>SNAPSHOT</aixm:interpretation>
         * <aixm:type>OTHER:FIR_UIR</aixm:type> <aixm:designator>YUDD</aixm:designator>
         * <aixm:name>SHANLON FIR/UIR</aixm:name>
         *
         *
         */
        // add type
        CodeAirspaceType asType = iwxxmHelpers.getOfAIXM().createCodeAirspaceType();
        asType.setValue(type);
        JAXBElement<CodeAirspaceType> astType = iwxxmHelpers.getOfAIXM().createAirspaceTimeSliceTypeType(asType);
        ts.getRest().add(astType);

        // add designator
        CodeAirspaceDesignatorType cadType = iwxxmHelpers.getOfAIXM().createCodeAirspaceDesignatorType();
        cadType.setValue(icaoCode);
        JAXBElement<CodeAirspaceDesignatorType> cast = iwxxmHelpers.getOfAIXM()
                .createAirspaceTimeSliceTypeDesignator(cadType);
        ts.getRest().add(cast);

        // add name
        TextNameType nType = iwxxmHelpers.getOfAIXM().createTextNameType();
        nType.setValue(firname);
        JAXBElement<TextNameType> ntType = iwxxmHelpers.getOfAIXM().createAirspaceTimeSliceTypeName(nType);
        ts.getRest().add(ntType);

        tsp.setAirspaceTimeSlice(ts);

        ast.getTimeSlice().add(tsp);

        return pt;
    }

    @Override
    public SIGMETType addTranslationCentreHeader(SIGMETType report) throws DatatypeConfigurationException {
        // report.setTranslationFailedTAC("");

        /*
        return iwxxmHelpers.addTranslationCentreHeaders(report, DateTime.now(), DateTime.now(),
                UUID.randomUUID().toString(), "UUWV", "Moscow, RU");
         */
        report = iwxxmHelpers.addTranslationCentreHeaders(report,
                DateTime.now(),
                DateTime.now(),
                UUID.randomUUID().toString(),
                App.getInstance().getString("CentreDesignator"),
                App.getInstance().getString("CentreName"));
        return report;

    }

    @Override
    public String getIdentifier() {
        return this.translatedSigmet.getIdentifier();
    }

    @Override
    protected JAXBElement<SIGMETType> createJaxbElement(SIGMETType r) {
        return iwxxmHelpers.getOfIWXXM().createSIGMET(r);
    }

}


