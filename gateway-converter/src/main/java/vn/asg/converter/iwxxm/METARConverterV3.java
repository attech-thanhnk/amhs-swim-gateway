/**
 * Copyright (C) 2018 Dmitry Moryakov, Main aeronautical meteorological center, Moscow, Russia
 * moryakovdv[at]gmail[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vn.asg.converter.iwxxm;

import vn.asg.converter.tac.METARTacMessage;
import java.io.UnsupportedEncodingException;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

import org.gamc.spmi.iwxxmConverter.exceptions.ParsingException;
import org.gamc.spmi.iwxxmConverter.general.MetarForecastSection;
import org.gamc.spmi.iwxxmConverter.general.MetarForecastTimeSection;
import org.gamc.spmi.iwxxmConverter.iwxxmenums.ANGLE_UNITS;
import org.gamc.spmi.iwxxmConverter.iwxxmenums.LENGTH_UNITS;
import org.gamc.spmi.iwxxmConverter.iwxxmenums.TEMPERATURE_UNITS;
import org.gamc.spmi.iwxxmConverter.metarconverter.METARBecomingSection;
import org.gamc.spmi.iwxxmConverter.metarconverter.METARCloudSection;
import org.gamc.spmi.iwxxmConverter.metarconverter.METARRVRSection;
import org.gamc.spmi.iwxxmConverter.metarconverter.METARRunwayStateSection;
import org.gamc.spmi.iwxxmConverter.metarconverter.METARTempoSection;
import org.gamc.spmi.iwxxmConverter.metarconverter.METARTimedATSection;
import org.gamc.spmi.iwxxmConverter.metarconverter.METARTimedFMSection;
import org.gamc.spmi.iwxxmConverter.metarconverter.METARTimedTLSection;
import org.gamc.spmi.iwxxmConverter.metarconverter.MetarCommonWeatherSection;
import org.gamc.spmi.iwxxmConverter.wmo.WMOCloudRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMONilReasonRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMORegister.WMORegisterException;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import _int.icao.iwxxm._2023_1.AbstractTimeObjectPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeCloudForecastPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeCloudForecastType;
import _int.icao.iwxxm._2023_1.AerodromeCloudPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeCloudType;
import _int.icao.iwxxm._2023_1.AerodromeHorizontalVisibilityPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeHorizontalVisibilityType;
import _int.icao.iwxxm._2023_1.AerodromePresentWeatherType;
import _int.icao.iwxxm._2023_1.AerodromeRunwayStatePropertyType;
import _int.icao.iwxxm._2023_1.AerodromeRunwayStateType;
import _int.icao.iwxxm._2023_1.AerodromeRunwayVisualRangePropertyType;
import _int.icao.iwxxm._2023_1.AerodromeRunwayVisualRangeType;
import _int.icao.iwxxm._2023_1.AerodromeSeaConditionPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeSeaConditionType;
import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindTrendForecastPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindTrendForecastType;
import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindType;
import _int.icao.iwxxm._2023_1.AerodromeWindShearPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeWindShearType;
import _int.icao.iwxxm._2023_1.AirportHeliportPropertyType;
import _int.icao.iwxxm._2023_1.AngleWithNilReasonType;
import _int.icao.iwxxm._2023_1.CloudAmountReportedAtAerodromeType;
import _int.icao.iwxxm._2023_1.CloudLayerPropertyType;
import _int.icao.iwxxm._2023_1.CloudLayerType;
import _int.icao.iwxxm._2023_1.DistanceWithNilReasonType;
import _int.icao.iwxxm._2023_1.AerodromeForecastChangeIndicatorType;
import _int.icao.iwxxm._2023_1.ForecastChangeIndicatorType;
import _int.icao.iwxxm._2023_1.LengthWithNilReasonType;
import _int.icao.iwxxm._2023_1.METARType;
import _int.icao.iwxxm._2023_1.MeasureWithNilReasonType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationPropertyType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeTrendForecastPropertyType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeTrendForecastType;
import _int.icao.iwxxm._2023_1.PermissibleUsageReasonType;
import _int.icao.iwxxm._2023_1.PermissibleUsageType;
import _int.icao.iwxxm._2023_1.RelationalOperatorType;
import _int.icao.iwxxm._2023_1.ReportStatusType;
import _int.icao.iwxxm._2023_1.RunwayContaminationType;
import _int.icao.iwxxm._2023_1.RunwayDepositsType;
import _int.icao.iwxxm._2023_1.RunwayDirectionPropertyType;
import _int.icao.iwxxm._2023_1.RunwayFrictionCoefficientType;
import _int.icao.iwxxm._2023_1.SeaSurfaceStateType;
import _int.icao.iwxxm._2023_1.SigConvectiveCloudTypeType;
import _int.icao.iwxxm._2023_1.TrendForecastTimeIndicatorType;
import _int.icao.iwxxm._2023_1.VelocityWithNilReasonType;
import _int.icao.iwxxm._2023_1.VisualRangeTendencyType;
import aero.aixm.schema._5_1.RunwayDirectionType;
import vn.asg.converter.config.Airport;
import vn.asg.converter.config.Airports;
import vn.asg.converter.config.App;
import vn.asg.converter.config.Runway;
import net.opengis.gml.v_3_2_1.AngleType;
import net.opengis.gml.v_3_2_1.CodeType;
import net.opengis.gml.v_3_2_1.LengthType;
import net.opengis.gml.v_3_2_1.SpeedType;
import net.opengis.gml.v_3_2_1.StringOrRefType;
import net.opengis.gml.v_3_2_1.TimeInstantPropertyType;
import net.opengis.gml.v_3_2_1.TimePeriodPropertyType;
import net.opengis.gml.v_3_2_1.TimePeriodType;
import org.gamc.spmi.iwxxmConverter.common.CoreUtil;
import org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException;
import org.gamc.spmi.iwxxmConverter.iwxxmenums.SPEED_UNITS;
import org.gamc.spmi.iwxxmConverter.metarconverter.MERTARSeaStateSection;
import org.gamc.spmi.iwxxmConverter.wmo.WMORunWaySnowRegister;

/**
 * Base class to perform conversion of TAC into intermediate object {@link METARTacMessage} and further IWXXM conversion and validation
 */
public class METARConverterV3 extends TacBaseConverter<METARTacMessage, METARType> {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(METARConverterV3.class);
    // private IWXXM31Helpers iwxxmHelpers = new IWXXM31Helpers();
    private String identifier;
    private METARTacMessage translatedMetar;
    private TreeMap<String, String> createdRunways = new TreeMap<>();
    private String dateTime = "";
    private String dateTimePosition = "";

    @Override
    public String getIdentifier() {
        return this.translatedMetar.getIdentifier();
    }

    @Override
    public String convertTacToXML(String tac) throws ConvertingFailException {
        try {
            logger.info(String.format("Converting to XML"));
            createdRunways.clear();
            METARTacMessage metarMessage = new METARTacMessage(tac);
            METARType result;
            metarMessage.parseMessage();
            result = convertMessage(metarMessage);
            return marshallMessageToXML(result);
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    @Override
    public byte[] convertTacToXML(String tac, boolean zipped) throws ConvertingFailException {
        try {
            logger.info(String.format("Converting to File"));
            createdRunways.clear();
            METARTacMessage metarMessage = new METARTacMessage(tac);
            METARType result;
            metarMessage.parseMessage();
            result = convertMessage(metarMessage);
            this.identifier = translatedMetar.getIdentifier();
            if (zipped) {
                this.identifier += ".gz";
            }
            return marshallMessageToByte(result, zipped);
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    @Override
    protected JAXBElement<METARType> createJaxbElement(METARType r) {
        return iwxxmHelpers.getOfIWXXM().createMETAR(r);
    }

    @Override
    public METARType convertMessage(METARTacMessage translatedMessage)
            throws DatatypeConfigurationException, UnsupportedEncodingException, JAXBException, WMORegisterException, ParsingException {
        try {
            this.translatedMetar = translatedMessage;

            // <iwxxm:METAR> root tag
            METARType metarRootTag = iwxxmHelpers.getOfIWXXM().createMETARType();

            // Using desciption to quote the origin message for debuggin
            /**
             * <gml:description xlink:type="simple"></gml:description>
             */
            if (App.getInstance().getBoolean("AddTacContent", false)) {
                StringOrRefType refTacString = iwxxmHelpers.getOfGML().createStringOrRefType();
                refTacString.setValue(translatedMessage.getInitialTacString());
                metarRootTag.setDescription(refTacString);
            }

            /*
		 * BoundingShapeType shape = iwxxmHelpers.getOfGML().createBoundingShapeType();
		 * EnvelopeType env = iwxxmHelpers.getOfGML().createEnvelopeType();
		 * 
		 * shape.setEnvelope(iwxxmHelpers.getOfGML().createEnvelope(env));
		 * 
		 * metarRootTag.setBoundedBy(shape);
             */
            dateTime = translatedMessage.getMessageIssueDateTime().toString(iwxxmHelpers.getDateTimeFormat()) + "Z";
            dateTimePosition = translatedMessage.getMessageIssueDateTime().toString(iwxxmHelpers.getDateTimeISOFormat());

            // Id with ICAO code and current timestamp
            metarRootTag.setId(iwxxmHelpers.generateUUIDv4(String.format("metar-%s-%s", translatedMetar.getIcaoCode(), dateTime)));

            // metarRootTag.setAutomatedStation(true);
            // Set NON_OPERATIONAL and TEST properties.
            PermissibleUsageType permissibleUsageType = PermissibleUsageType.valueOf(App.getInstance().getString("PermissibleUsage"));
            metarRootTag.setPermissibleUsage(permissibleUsageType);
            if (permissibleUsageType == PermissibleUsageType.NON_OPERATIONAL) {
                // Non-Operational Reason
                metarRootTag.setPermissibleUsageReason(PermissibleUsageReasonType.valueOf(App.getInstance().getString("PermissibleUsageReason")));
                // Some description
                metarRootTag.setPermissibleUsageSupplementary(App.getInstance().getString("PermissibleUsageSupplementary"));
            }

            // COR, NIL, NORMAL
            switch (translatedMetar.getMessageStatusType()) {
                case MISSING:
                    metarRootTag.setReportStatus(null);
                    break;
                case CORRECTION:
                    metarRootTag.setReportStatus(ReportStatusType.CORRECTION);
                    break;
                default:
                    metarRootTag.setReportStatus(ReportStatusType.NORMAL);
            }

            metarRootTag.setAutomatedStation(translatedMetar.isAuto());

            // Translation centre info
            metarRootTag = addTranslationCentreHeader(metarRootTag);

            // Begining of the content
            // <iwxxm:aerodrome></iwxxm:aerodrome>
            metarRootTag.setAerodrome(createAirportDescriptionSectionTag());

            // <iwxxm:issueTime />
            TimeInstantPropertyType issueTimeType = iwxxmHelpers.createTimeInstantPropertyTypeForDateTime(translatedMetar.getMessageIssueDateTime(), translatedMetar.getIcaoCode(), "issue");
            metarRootTag.setIssueTime(issueTimeType);

            // <iwxxm:observationTime />
            TimeInstantPropertyType obsTimeType = iwxxmHelpers.createTimeInstantPropertyTypeForDateTime(translatedMetar.getMessageIssueDateTime(), translatedMetar.getIcaoCode(), "obs");
            metarRootTag.setObservationTime(obsTimeType);

            // METAR -> observation -> MeteorologicalAerodromeObservationType (which contains all data in this binding)
            MeteorologicalAerodromeObservationPropertyType observationProp = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeObservationPropertyType();
            MeteorologicalAerodromeObservationType omObservation = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeObservationType();
            omObservation.setId(iwxxmHelpers.generateUUIDv4(String.format("obs-%s-%s", translatedMetar.getIcaoCode(), dateTime)));
            // omObservation.setId is already set above
            // metarRootTag.setObservationTime already set above

            if (translatedMessage.isNIL()) {
                observationProp.getNilReason().add(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_MISSING));
            } else {
                // Populate meteorological data directly into omObservation
                populateObservationData(omObservation);
            }

            observationProp.setMeteorologicalAerodromeObservation(omObservation);
            metarRootTag.setObservation(iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeObservationReportTypeObservation(observationProp));

            AtomicInteger sectionIndex = new AtomicInteger(0);

            if (translatedMetar.isNoSignificantChanges()) {
                MeteorologicalAerodromeTrendForecastPropertyType nosigTrend = createNosigForecastSection();
                metarRootTag.getTrendForecast().add(nosigTrend);
            }

            // <iwxxm:trendForecast>
            //      <iwxxm:MeteorologicalAerodromeTrendForecast changeIndicator="BECOMING" />
            // </iwxxm:trendForecast>
            for (METARBecomingSection bcmgSection : translatedMetar.getBecomingSections()) {
                bcmgSection.parseSection();
                if (bcmgSection.getSectionType() == null) {
                    continue;
                }
                MeteorologicalAerodromeTrendForecastPropertyType omptBcmg = createTrendResultsSection(bcmgSection, sectionIndex.getAndIncrement());
                metarRootTag.getTrendForecast().add(omptBcmg);
            }

            // <iwxxm:trendForecast>
            //      <iwxxm:MeteorologicalAerodromeTrendForecast changeIndicator="TEMPO"/>
            // </iwxxm:trendForecast>
            for (METARTempoSection tempoSection : translatedMetar.getTempoSections()) {
                tempoSection.parseSection();
                MeteorologicalAerodromeTrendForecastPropertyType omptTempo = createTrendResultsSection(tempoSection, sectionIndex.getAndIncrement());
                metarRootTag.getTrendForecast().add(omptTempo);
            }

            // <iwxxm:trendForecast>
            //      <iwxxm:MeteorologicalAerodromeTrendForecast />
            // </iwxxm:trendForecast>
            for (MetarForecastTimeSection tSection : translatedMetar.getTimedSections()) {
                tSection.parseSection();
                MeteorologicalAerodromeTrendForecastPropertyType omptBcmg = createTrendResultsSection(tSection, sectionIndex.getAndIncrement());
                metarRootTag.getTrendForecast().add(omptBcmg);
            }

            // create XML representation
            return metarRootTag;
        } catch (ParsingException ex) {
            throw new ParsingException(ex.getMessage() + " in [" + CoreUtil.truncateString(this.translatedMetar.getInitialTacString(), 25) + "]", ex);
        }

    }

    @Override
    protected String postProcessXML(String xml) {
        // 0. Add namespaces if missing
        if (!xml.contains("xmlns:om=")) {
            xml = xml.replace("<iwxxm:METAR", "<iwxxm:METAR xmlns:om=\"http://www.opengis.net/om/2.0\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"");
        }

        // 1. Fix the record name
        xml = xml.replace("iwxxm:MeteorologicalAerodromeObservation ", "iwxxm:MeteorologicalAerodromeObservationRecord ");
        xml = xml.replace("iwxxm:MeteorologicalAerodromeObservation>", "iwxxm:MeteorologicalAerodromeObservationRecord>");
        xml = xml.replace("</iwxxm:MeteorologicalAerodromeObservation>", "</iwxxm:MeteorologicalAerodromeObservationRecord>");

        // 2. Wrap the record in om:OM_Observation
        String recordStartTag = "<iwxxm:MeteorologicalAerodromeObservationRecord";
        int startIdx = xml.indexOf(recordStartTag);
        if (startIdx == -1) return xml;
        
        int endIdx = xml.indexOf("</iwxxm:observation>");
        if (endIdx == -1) return xml;

        String recordContent = xml.substring(startIdx, xml.lastIndexOf("</iwxxm:MeteorologicalAerodromeObservationRecord>") + "</iwxxm:MeteorologicalAerodromeObservationRecord>".length());
        
        // Extract time for O&M header
        String timePos = "";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<gml:timePosition>([^<]+)</gml:timePosition>");
        java.util.regex.Matcher m = p.matcher(xml);
        if (m.find()) {
            timePos = m.group(1);
        }
        
        String icao = translatedMetar.getIcaoCode();
        String obsId = "obs-" + icao + "-" + timePos.replace(":", "").replace("-", "");
        
        StringBuilder omWrap = new StringBuilder();
        omWrap.append("<om:OM_Observation gml:id=\"").append(obsId).append("\">\n");
        // Updated om:type URL to use 2023-1
        omWrap.append("            <om:type xlink:href=\"http://codes.wmo.int/49-2/observation-type/IWXXM/2023-1/MeteorologicalAerodromeObservation\"/>\n");
        omWrap.append("            <om:phenomenonTime>\n");
        omWrap.append("                <gml:TimeInstant gml:id=\"ti-").append(obsId).append("\">\n");
        omWrap.append("                    <gml:timePosition>").append(timePos).append("</gml:timePosition>\n");
        omWrap.append("                </gml:TimeInstant>\n");
        omWrap.append("            </om:phenomenonTime>\n");
        omWrap.append("            <om:resultTime xlink:href=\"#ti-").append(obsId).append("\"/>\n");
        omWrap.append("            <om:procedure xlink:href=\"#proc-1\"/>\n");
        omWrap.append("            <om:observedProperty xlink:href=\"http://codes.wmo.int/49-2/observable-property/MeteorologicalAerodromeObservation\"/>\n");
        omWrap.append("            <om:featureOfInterest xlink:href=\"#aerodrome-").append(icao).append("\"/>\n");
        omWrap.append("            <om:result>\n");
        omWrap.append("                ").append(recordContent).append("\n");
        omWrap.append("            </om:result>\n");
        omWrap.append("        </om:OM_Observation>");

        // Replace the old observation content with the wrapped one
        int outerStart = xml.indexOf("<iwxxm:observation>") + "<iwxxm:observation>".length();
        int outerEnd = xml.indexOf("</iwxxm:observation>");
        
        xml = xml.substring(0, outerStart) + "\n        " + omWrap.toString() + "\n    " + xml.substring(outerEnd);

        // 3. Fix NOSIG and other Trend Indicators
        // First remove any existing changeIndicator generated by JAXB Enum
        xml = xml.replaceAll("<iwxxm:changeIndicator[^>]*>.*?</iwxxm:changeIndicator>", "");
        
        // Inject NOSIG changeIndicator into the trend-nosig section
        xml = xml.replaceAll("(<iwxxm:MeteorologicalAerodromeTrendForecast[^>]+gml:id=\"trend-nosig-[^\"]+\"[^>]*?)(?<!/)>", 
                             "$1>\n            <iwxxm:changeIndicator xlink:href=\"http://codes.wmo.int/49-2/TrendForecastChangeIndicator/NOSIG\"/>");
        xml = xml.replaceAll("(<iwxxm:MeteorologicalAerodromeTrendForecast[^>]+gml:id=\"trend-nosig-[^\"]+\"[^>]*?)\\s*/>", 
                             "$1>\n            <iwxxm:changeIndicator xlink:href=\"http://codes.wmo.int/49-2/TrendForecastChangeIndicator/NOSIG\"/>\n        </iwxxm:MeteorologicalAerodromeTrendForecast>");
        
        // Inject BECOMING changeIndicator into change-record-bcmg section
        xml = xml.replaceAll("(<iwxxm:MeteorologicalAerodromeTrendForecast[^>]+gml:id=\"change-record-bcmg-[^\"]+\"[^>]*?)(?<!/)>", 
                             "$1>\n            <iwxxm:changeIndicator xlink:href=\"http://codes.wmo.int/49-2/TrendForecastChangeIndicator/BECOMING\"/>");
        xml = xml.replaceAll("(<iwxxm:MeteorologicalAerodromeTrendForecast[^>]+gml:id=\"change-record-bcmg-[^\"]+\"[^>]*?)\\s*/>", 
                             "$1>\n            <iwxxm:changeIndicator xlink:href=\"http://codes.wmo.int/49-2/TrendForecastChangeIndicator/BECOMING\"/>\n        </iwxxm:MeteorologicalAerodromeTrendForecast>");
                             
        // Inject TEMPO changeIndicator into change-record-tempo section
        xml = xml.replaceAll("(<iwxxm:MeteorologicalAerodromeTrendForecast[^>]+gml:id=\"change-record-tempo-[^\"]+\"[^>]*?)(?<!/)>", 
                             "$1>\n            <iwxxm:changeIndicator xlink:href=\"http://codes.wmo.int/49-2/TrendForecastChangeIndicator/TEMPO\"/>");
        xml = xml.replaceAll("(<iwxxm:MeteorologicalAerodromeTrendForecast[^>]+gml:id=\"change-record-tempo-[^\"]+\"[^>]*?)\\s*/>", 
                             "$1>\n            <iwxxm:changeIndicator xlink:href=\"http://codes.wmo.int/49-2/TrendForecastChangeIndicator/TEMPO\"/>\n        </iwxxm:MeteorologicalAerodromeTrendForecast>");
        
        // 5. Define the procedure (ObservationProcess)
        if (!xml.contains("gml:id=\"proc-1\"")) {
            String procType = translatedMetar.isAuto() ? "automatedStation" : "manualObservation";
            String procDef = "\n    <iwxxm:observationProcess gml:id=\"proc-1\">\n" +
                             "        <iwxxm:processType xlink:href=\"http://codes.wmo.int/common/procedure/" + procType + "\"/>\n" +
                             "    </iwxxm:observationProcess>";
            xml = xml.replace("</iwxxm:METAR>", procDef + "\n</iwxxm:METAR>");
        }
        
        return xml;
    }

    @Override
    public METARType addTranslationCentreHeader(METARType report)
            throws DatatypeConfigurationException {
        report = iwxxmHelpers.addTranslationCentreHeaders(report,
                DateTime.now(),
                DateTime.now(),
                UUID.randomUUID().toString(),
                App.getInstance().getString("CentreDesignator"),
                App.getInstance().getString("CentreName"));
        //report.setTranslationFailedTAC("");
        return report;
    }

    // -------------------------------//
    /*
	 * private MeteorologicalAerodromeObservationPropertyType
	 * createObservationResult() {
	 * 
	 * // тег <>om:OM_Observation MeteorologicalAerodromeObservationPropertyType
	 * result = ofIWXXM.createMeteorologicalAerodromeObservationPropertyType();
	 * MeteorologicalAerodromeObservationType obsType =
	 * ofIWXXM.createMeteorologicalAerodromeObservationType();
	 * result.setMeteorologicalAerodromeObservation(obsType);
	 * 
	 * obsType.setId(iwxxmHelpers.generateUUIDv4(String.format("obs-%s-%s",
	 * translatedMetar.getIcaoCode(), dateTime)));
	 * 
	 * // тип наблюдения - ссылка xlink:href ReferenceType observeType =
	 * ofGML.createReferenceType();
	 * observeType.setHref(UriConstants.OBSERVATION_TYPE_METAR);
	 * obsType.setType(observeType);
	 * 
	 * // Create instant time section TimeObjectPropertyType timeObjectProperty =
	 * ofOM.createTimeObjectPropertyType(); TimeInstantType timeInstant =
	 * ofGML.createTimeInstantType(); timeInstant
	 * .setId(iwxxmHelpers.generateUUIDv4(String.format("ti-%s-%s",
	 * translatedMetar.getIcaoCode(), dateTime))); TimePositionType timePosition =
	 * ofGML.createTimePositionType();
	 * timePosition.getValue().add(dateTimePosition);
	 * timeInstant.setTimePosition(timePosition);
	 * 
	 * JAXBElement<TimeInstantType> timeElement =
	 * ofGML.createTimeInstant(timeInstant);
	 * timeObjectProperty.setAbstractTimeObject(timeElement);
	 * 
	 * // and place it to <phenomenonTime>
	 * obsType.se.setPhenomenonTime(timeObjectProperty);
	 * 
	 * // create <resultTime> TimeInstantPropertyType timeInstantResult =
	 * ofGML.createTimeInstantPropertyType(); timeInstantResult.setHref("#" +
	 * timeInstant.getId());// "#ti-UUWW-"+dateTime);
	 * obsType.setResultTime(timeInstantResult);
	 * 
	 * // create <om:procedure> frame ProcessType metceProcess =
	 * ofMetce.createProcessType();
	 * metceProcess.setId(iwxxmHelpers.generateUUIDv4("p-49-2-metar"));
	 * 
	 * StringOrRefType processDescription = ofGML.createStringOrRefType();
	 * processDescription.setValue(StringConstants.WMO_49_2_METCE_METAR);
	 * metceProcess.setDescription(processDescription);
	 * 
	 * OMProcessPropertyType omProcedure = ofOM.createOMProcessPropertyType();
	 * omProcedure.setAny(ofMetce.createProcess(metceProcess));
	 * ot.setProcedure(omProcedure);
	 * 
	 * // tag om:ObserverdProperty ReferenceType observedProperty =
	 * ofGML.createReferenceType();
	 * observedProperty.setHref(UriConstants.OBSERVED_PROPERTY_METAR);
	 * observedProperty.setTitle(StringConstants.WMO_METAR_OBSERVED_PROPERTY_TITLE);
	 * 
	 * ot.setObservedProperty(observedProperty);
	 * 
	 * ot.setFeatureOfInterest(createAirportDescriptionSectionTag());
	 * 
	 * // At last create payload MeteorologicalAerodromeObservationPropertyType
	 * metarRecord = createMETARRecordTag();
	 * 
	 * 
	 * 
	 * 
	 * 
	 * return result;
	 * 
	 * }
     */
    private MeteorologicalAerodromeTrendForecastPropertyType createTrendResultsSection(MetarForecastSection section, int sectionIndex)
            throws WMORegisterException {

        // TODO: Checking this
        MeteorologicalAerodromeTrendForecastPropertyType metarTrendType = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeTrendForecastPropertyType();
        MeteorologicalAerodromeTrendForecastType metarTrend = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeTrendForecastType();
        metarTrendType.setMeteorologicalAerodromeTrendForecast(metarTrend);

        // Indicator (Use ForecastChangeIndicatorType for Trend)
        ForecastChangeIndicatorType changeIndicator = ForecastChangeIndicatorType.BECOMING;
        String indicatorStr = "bcmg";

        if (section instanceof METARBecomingSection) {
            changeIndicator = ForecastChangeIndicatorType.BECOMING;
            indicatorStr = "bcmg";
        } else if (section instanceof METARTempoSection) {
            changeIndicator = ForecastChangeIndicatorType.TEMPORARY_FLUCTUATIONS;
            indicatorStr = "tempo";
        }
        
        // metarTrend.setChangeIndicator(changeIndicator);
        metarTrend.setId(String.format("change-record-%s-%d-%s-%s", indicatorStr, sectionIndex, translatedMetar.getIcaoCode(), UUID.randomUUID().toString()));
        metarTrend.setCloudAndVisibilityOK(section.getCommonWeatherSection().isCavok());

        // visibility
        if (section.getCommonWeatherSection().getPrevailVisibility() != null) {
            LengthType vis = iwxxmHelpers.getOfGML().createLengthType();
            vis.setUom(section.getCommonWeatherSection().getVisibilityUnits().getStringValue());

            if (section.getCommonWeatherSection().getPrevailVisibility() == 9999) {
                vis.setValue(9999);
                metarTrend.setPrevailingVisibility(vis);

            } else if (section.getCommonWeatherSection().getPrevailVisibility() == 0) {
                vis.setValue(50);
                metarTrend.setPrevailingVisibility(vis);
                metarTrend.setPrevailingVisibilityOperator(RelationalOperatorType.BELOW);

            } else {
                vis.setValue(section.getCommonWeatherSection().getPrevailVisibility());
                metarTrend.setPrevailingVisibility(vis);
            }

        }

        // surfaceWind
        AerodromeSurfaceWindTrendForecastPropertyType sWindpropertyType = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindTrendForecastPropertyType();
        AerodromeSurfaceWindTrendForecastType sWindType = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindTrendForecastType();
        boolean sectionHasWind = false;

        // Set gust speed
        if (section.getCommonWeatherSection().getGustSpeed() != null) {
            SpeedType speedGustType = iwxxmHelpers.getOfGML().createSpeedType();
            speedGustType.setUom(section.getCommonWeatherSection().getSpeedUnits().getStringValue());
            speedGustType.setValue(section.getCommonWeatherSection().getGustSpeed());
            sWindType.setWindGustSpeed(speedGustType);
            sectionHasWind = true;
        }

        // Set mean wind
        if (section.getCommonWeatherSection().getWindSpeed() != null) {
            SpeedType speedMeanType = iwxxmHelpers.getOfGML().createSpeedType();
            speedMeanType.setUom(section.getCommonWeatherSection().getSpeedUnits().getStringValue());
            speedMeanType.setValue(section.getCommonWeatherSection().getWindSpeed());
            sWindType.setMeanWindSpeed(speedMeanType);
            sectionHasWind = true;
        }

        // Set wind direction
        if (section.getCommonWeatherSection().getWindDir() != null) {
            AngleType windAngle = iwxxmHelpers.getOfGML().createAngleType();
            windAngle.setUom(ANGLE_UNITS.DEGREES.getStringValue());
            windAngle.setValue(section.getCommonWeatherSection().getWindDir());
            sWindType.setMeanWindDirection(windAngle);
            sectionHasWind = true;
        }

        if (sectionHasWind) {
            JAXBElement<AerodromeSurfaceWindTrendForecastType> windElement = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindTrendForecast(sWindType);
            sWindpropertyType.setAerodromeSurfaceWindTrendForecast(windElement);
            metarTrend.setSurfaceWind(sWindpropertyType);
        }

        // clouds
        if (section.getCommonWeatherSection().getCloudSections().size() > 0) {
            AerodromeCloudForecastType clouds = iwxxmHelpers.getOfIWXXM().createAerodromeCloudForecastType();
            
            for (METARCloudSection cloudSection : section.getCommonWeatherSection().getCloudSections()) {
                CloudLayerPropertyType cloudLayer = iwxxmHelpers.getOfIWXXM().createCloudLayerPropertyType();
                
                if (cloudSection.getAmount() != null && cloudSection.getAmount().equalsIgnoreCase(WMOCloudRegister.verticalVisibilityCode)) {
                    LengthWithNilReasonType vvType = iwxxmHelpers.getOfIWXXM().createLengthWithNilReasonType();
                    vvType.setUom(LENGTH_UNITS.FT.getStringValue().replace("[ft_i]", "ft"));
                    if (cloudSection.getHeight() != null && cloudSection.getHeight().isPresent()) {
                        vvType.setValue((double) cloudSection.getHeight().get());
                    } else {
                        vvType.getNilReason().add(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE));
                    }
                    clouds.setVerticalVisibility(iwxxmHelpers.getOfIWXXM().createAerodromeCloudTypeVerticalVisibility(vvType));
                } else {
                    if (cloudSection.getHeight() != null && cloudSection.getHeight().isPresent()) {
                        cloudLayer.setCloudLayer(iwxxmHelpers.createCloudLayerSection(cloudSection.getAmount(), (double) cloudSection.getHeight().get(),
                                cloudSection.getType(), null, LENGTH_UNITS.FT));
                    } else {
                        cloudLayer.setCloudLayer(iwxxmHelpers.createEmptyCloudLayerSection(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE)));
                    }
                    clouds.getLayer().add(cloudLayer);
                }
            }
            
            AerodromeCloudForecastPropertyType cloudsProp = iwxxmHelpers.getOfIWXXM().createAerodromeCloudForecastPropertyType();
            cloudsProp.setAerodromeCloudForecast(clouds);
            
            JAXBElement<AerodromeCloudForecastPropertyType> cloudsPropElement = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeTrendForecastTypeCloud(cloudsProp);
            metarTrend.setCloud(cloudsPropElement);
        }
        // forecasted weather
        java.util.List<String> trendWeathers = section.getCommonWeatherSection().getCurrentWeather();
        if (trendWeathers.isEmpty()) {
            String tac = translatedMetar.getInitialTacString();
            String[] parts = tac.split("TEMPO|BECMG");
            if (sectionIndex + 1 < parts.length) {
                trendWeathers = robustExtractWeather(parts[sectionIndex + 1]);
            }
        }
        for (String weatherCode : trendWeathers) {
            metarTrend.getWeather().add(iwxxmHelpers.createForecastWeatherSection(weatherCode));
        }

        TrendForecastTimeIndicatorType timeIndicator = null;
        if (section instanceof METARTimedATSection) {
            timeIndicator = TrendForecastTimeIndicatorType.AT;

        } else if (section instanceof METARTimedFMSection) {
            timeIndicator = TrendForecastTimeIndicatorType.FROM;

        } else if (section instanceof METARTimedTLSection) {
            timeIndicator = TrendForecastTimeIndicatorType.UNTIL;
        }

        if (timeIndicator != null) {
            metarTrend.setTimeIndicator(timeIndicator);
        }

        Interval timeIntreval = section.getTrendValidityInterval();
        // For basic TEMPO/BECMG, use a default 2-hour window from observation time
        if (!(section instanceof MetarForecastTimeSection)) {
            DateTime start = translatedMetar.getMessageIssueDateTime();
            DateTime end = start.plusHours(2);
            timeIntreval = new Interval(start, end);
        }

        TimePeriodPropertyType period = iwxxmHelpers.createTrendPeriodSection(translatedMetar.getIcaoCode(), timeIntreval.getStart(), timeIntreval.getEnd(), sectionIndex);
        JAXBElement<TimePeriodType> periodTime = iwxxmHelpers.getOfGML().createTimePeriod(period.getTimePeriod());

        AbstractTimeObjectPropertyType aTime = iwxxmHelpers.getOfIWXXM().createAbstractTimeObjectPropertyType();
        aTime.setAbstractTimeObject(periodTime);

        // Omit phenomenonTime for METAR TEMPO/BECMG if interval is arbitrary/fabricated
        // metarTrend.setPhenomenonTime(aTime);

        return metarTrendType;
    }

    private MeteorologicalAerodromeTrendForecastPropertyType createNosigForecastSection()
            throws WMORegisterException {

        MeteorologicalAerodromeTrendForecastPropertyType metarTrendProp = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeTrendForecastPropertyType();
        MeteorologicalAerodromeTrendForecastType metarTrend = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeTrendForecastType();
        
        metarTrend.setId("trend-nosig-" + translatedMetar.getIcaoCode() + "-" + UUID.randomUUID().toString());
        
        // We use BECOMING as a placeholder and will replace it in postProcessXML
        // because NO_SIGNIFICANT_CHANGES does not exist in this binding's enum.
        // metarTrend.setChangeIndicator(ForecastChangeIndicatorType.BECOMING);
        
        metarTrendProp.setMeteorologicalAerodromeTrendForecast(metarTrend);
        
        return metarTrendProp;
    }

    /**
     * Create aerodrome description section as GML FeatureOfInterest ICAO code
     */
    private AirportHeliportPropertyType createAirportDescriptionSectionTag() {

        return iwxxmHelpers.createAirportDescriptionSectionTag(translatedMetar.getIcaoCode());

    }

    /**
     * Fills the observation with meteorological data
     */
    private void populateObservationData(MeteorologicalAerodromeObservationType omObservation) throws WMORegisterException {

        // Set temperature (Standard uom: Cel)
        MeasureWithNilReasonType mtTemperature = iwxxmHelpers.getOfIWXXM().createMeasureWithNilReasonType();
        mtTemperature.setUom("Cel");
        mtTemperature.setValue(translatedMetar.getCommonWeatherSection().getAirTemperature().doubleValue());
        omObservation.setAirTemperature(mtTemperature);

        // Set dew pont (Standard uom: Cel)
        MeasureWithNilReasonType mtDew = iwxxmHelpers.getOfIWXXM().createMeasureWithNilReasonType();
        mtDew.setUom("Cel");
        mtDew.setValue(translatedMetar.getCommonWeatherSection().getDewPoint().doubleValue());
        omObservation.setDewpointTemperature(mtDew);

        // Set QNH (Standard uom: hPa)
        MeasureWithNilReasonType mtQNH = iwxxmHelpers.getOfIWXXM().createMeasureWithNilReasonType();
        mtQNH.setUom("hPa");
        mtQNH.setValue(translatedMetar.getCommonWeatherSection().getQnh().doubleValue());
        omObservation.setQnh(mtQNH);

        omObservation.setCloudAndVisibilityOK(translatedMetar.getCommonWeatherSection().isCavok());

        // Create and set wind section
        AerodromeSurfaceWindPropertyType windProp = createWindSectionTag();
        MeteorologicalAerodromeObservationType.SurfaceWind surfaceWind = new MeteorologicalAerodromeObservationType.SurfaceWind();
        surfaceWind.setAerodromeSurfaceWind(windProp.getAerodromeSurfaceWind());
        omObservation.setSurfaceWind(surfaceWind);

        // Create and set visibility section
        if (!translatedMetar.getCommonWeatherSection().isCavok()) {
            AerodromeHorizontalVisibilityPropertyType hVisProp = createVisibilitySectionTag();
            if (hVisProp != null) {
                MeteorologicalAerodromeObservationType.Visibility visibility = new MeteorologicalAerodromeObservationType.Visibility();
                visibility.setAerodromeHorizontalVisibility(hVisProp.getAerodromeHorizontalVisibility());
                omObservation.setVisibility(iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeObservationTypeVisibility(visibility));
            }
        }

        // create and set present weather conditions
        if (translatedMetar.getCommonWeatherSection().isPresentWeatherNotObservable()) {
            AerodromePresentWeatherType presentWeather = iwxxmHelpers.getOfIWXXM().createAerodromePresentWeatherType();
            presentWeather.getNilReason().add(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE));
            // omObservation.getPresentWeather().add(presentWeather);
        }

        java.util.List<String> weathers = translatedMetar.getCommonWeatherSection().getCurrentWeather();
        if (weathers.isEmpty()) {
            String tac = translatedMetar.getInitialTacString();
            int tempoIdx = tac.indexOf("TEMPO");
            int becmgIdx = tac.indexOf("BECMG");
            int splitIdx = -1;
            if (tempoIdx != -1 && becmgIdx != -1) splitIdx = Math.min(tempoIdx, becmgIdx);
            else if (tempoIdx != -1) splitIdx = tempoIdx;
            else if (becmgIdx != -1) splitIdx = becmgIdx;
            
            String obsTac = (splitIdx != -1) ? tac.substring(0, splitIdx) : tac;
            weathers = robustExtractWeather(obsTac);
        }

        if (weathers.isEmpty() && !translatedMetar.getCommonWeatherSection().isCavok()) {
            omObservation.getPresentWeather().add(iwxxmHelpers.createPresentWeatherSection("noSignificantWeather"));
        } else {
            for (String weatherCode : weathers) {
                omObservation.getPresentWeather().add(iwxxmHelpers.createPresentWeatherSection(weatherCode));
            }
        }

        // create and set clouds
        AerodromeCloudPropertyType cloudProp = createCloudSectionTag(translatedMetar.getCommonWeatherSection(), translatedMetar.getIcaoCode(), 0);
        MeteorologicalAerodromeObservationType.Cloud cloud = new MeteorologicalAerodromeObservationType.Cloud();
        cloud.setAerodromeCloud(cloudProp.getAerodromeCloud());
        omObservation.setCloud(iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeObservationTypeCloud(cloud));

        // RVR
        for (METARRVRSection rvrSection : translatedMetar.getRvrSections()) {
            AerodromeRunwayVisualRangePropertyType rvrProp = createRVRTag(rvrSection);
            MeteorologicalAerodromeObservationType.Rvr rvr = new MeteorologicalAerodromeObservationType.Rvr();
            rvr.setAerodromeRunwayVisualRange(rvrProp.getAerodromeRunwayVisualRange());
            omObservation.getRvr().add(rvr);
        }

        // Runway State
        for (METARRunwayStateSection rwStateSection : translatedMetar.getRunwayStateSections()) {
            AerodromeRunwayStatePropertyType rsProp = createRunwayStateTag(rwStateSection);
            MeteorologicalAerodromeObservationType.RunwayState rs = new MeteorologicalAerodromeObservationType.RunwayState();
            rs.setAerodromeRunwayState(rsProp.getAerodromeRunwayState());
            omObservation.getRunwayState().add(rs);
        }

        // SNOCLO?
        if (translatedMetar.isSnowclosed()) {
            AerodromeRunwayStatePropertyType rsProp = createSNOCLORunwayStateTag();
            MeteorologicalAerodromeObservationType.RunwayState rs = new MeteorologicalAerodromeObservationType.RunwayState();
            rs.setAerodromeRunwayState(rsProp.getAerodromeRunwayState());
            omObservation.getRunwayState().add(rs);
        }

        // Wind Shear
        if (translatedMetar.getWindShearSections().size() > 0) {
            AerodromeWindShearPropertyType wsProp = createWindShearTag();
            MeteorologicalAerodromeObservationType.WindShear ws = new MeteorologicalAerodromeObservationType.WindShear();
            ws.setAerodromeWindShear(wsProp.getAerodromeWindShear());
            omObservation.setWindShear(iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeObservationTypeWindShear(ws));
        }

        // Sea Condition
        if (translatedMetar.getSeaStateSection() != null) {
            AerodromeSeaConditionPropertyType scProp = createSeaCondition(translatedMetar.getSeaStateSection());
            MeteorologicalAerodromeObservationType.SeaCondition sc = new MeteorologicalAerodromeObservationType.SeaCondition();
            sc.setAerodromeSeaCondition(scProp.getAerodromeSeaCondition());
            omObservation.setSeaCondition(iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeObservationTypeSeaCondition(sc));
        }
    }



    /**
     * Wind section
     */
    private _int.icao.iwxxm._2023_1.AerodromeSurfaceWindPropertyType createWindSectionTag() {

        _int.icao.iwxxm._2023_1.AerodromeSurfaceWindPropertyType result = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindPropertyType();
        // body
        AerodromeSurfaceWindType surfaceWind = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindType();

        surfaceWind.setVariableWindDirection(translatedMetar.getCommonWeatherSection().isVrb());

        // Set gust speed (ICAO uom: kt or m/s)
        // Set gust speed (ICAO uom: kt or m/s)
        if (translatedMetar.getCommonWeatherSection().getGustSpeed() != null) {
            VelocityWithNilReasonType speedGustType = iwxxmHelpers.getOfIWXXM().createVelocityWithNilReasonType();
            String uom = translatedMetar.getCommonWeatherSection().getSpeedUnits().getStringValue().replace("[kn_i]", "kt");
            speedGustType.setUom(uom);
            speedGustType.setValue(translatedMetar.getCommonWeatherSection().getGustSpeed().doubleValue());
            JAXBElement<VelocityWithNilReasonType> sg = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindTypeWindGustSpeed(speedGustType);
            surfaceWind.setWindGustSpeed(sg);
        }

        // Set mean wind
        if (translatedMetar.getCommonWeatherSection().isVrb()) {
            VelocityWithNilReasonType speedMeanType = iwxxmHelpers.getOfIWXXM().createVelocityWithNilReasonType();
            String uom = translatedMetar.getCommonWeatherSection().getVrbSpeedUnits().getStringValue().replace("[kn_i]", "kt");
            speedMeanType.setUom(uom);
            speedMeanType.setValue(translatedMetar.getCommonWeatherSection().getWindVrbSpeed().doubleValue());
            surfaceWind.setMeanWindSpeed(speedMeanType);
        } else if (translatedMetar.getCommonWeatherSection().getWindSpeed() != null) {
            VelocityWithNilReasonType speedMeanType = iwxxmHelpers.getOfIWXXM().createVelocityWithNilReasonType();
            String uom = translatedMetar.getCommonWeatherSection().getSpeedUnits().getStringValue().replace("[kn_i]", "kt");
            speedMeanType.setUom(uom);
            speedMeanType.setValue(translatedMetar.getCommonWeatherSection().getWindSpeed().doubleValue());
            surfaceWind.setMeanWindSpeed(speedMeanType);
        }

        // Set wind direction
        if (translatedMetar.getCommonWeatherSection().getWindDir() != null) {
            AngleWithNilReasonType windAngle = iwxxmHelpers.getOfIWXXM().createAngleWithNilReasonType();
            windAngle.setUom("deg");
            windAngle.setValue(translatedMetar.getCommonWeatherSection().getWindDir().doubleValue());
            JAXBElement<AngleWithNilReasonType> wa = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindTypeMeanWindDirection(windAngle);
            wa.setValue(windAngle);
            surfaceWind.setMeanWindDirection(wa);
        }

        // Set wind angles
        if (translatedMetar.getCommonWeatherSection().getWindVariableFrom() != null) {
            AngleWithNilReasonType windAngleCW = iwxxmHelpers.getOfIWXXM().createAngleWithNilReasonType();
            windAngleCW.setUom("deg");
            windAngleCW.setValue(translatedMetar.getCommonWeatherSection().getWindVariableFrom());
            JAXBElement<AngleWithNilReasonType> waCW = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindTypeExtremeClockwiseWindDirection(windAngleCW);
            surfaceWind.setExtremeClockwiseWindDirection(waCW);
        }

        if (translatedMetar.getCommonWeatherSection().getWindVariableTo() != null) {
            AngleWithNilReasonType windAngleCCW = iwxxmHelpers.getOfIWXXM().createAngleWithNilReasonType();
            windAngleCCW.setUom("deg");
            windAngleCCW.setValue(translatedMetar.getCommonWeatherSection().getWindVariableTo());
            JAXBElement<AngleWithNilReasonType> waCCW = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindTypeExtremeCounterClockwiseWindDirection(windAngleCCW);
            surfaceWind.setExtremeCounterClockwiseWindDirection(waCCW);
        }

        result.setAerodromeSurfaceWind(surfaceWind);
        return result;
    }

    /**
     * Visibility section
     */
    private _int.icao.iwxxm._2023_1.AerodromeHorizontalVisibilityPropertyType createVisibilitySectionTag() {

        _int.icao.iwxxm._2023_1.AerodromeHorizontalVisibilityPropertyType resultVisibility = iwxxmHelpers.getOfIWXXM().createAerodromeHorizontalVisibilityPropertyType();
        AerodromeHorizontalVisibilityType visibility = iwxxmHelpers.getOfIWXXM().createAerodromeHorizontalVisibilityType();

        boolean isSet = false;

        // Minimal visibility
        if (translatedMetar.getCommonWeatherSection().getMinimumVisibility() != null) {
            DistanceWithNilReasonType minVis = iwxxmHelpers.getOfIWXXM().createDistanceWithNilReasonType();
            minVis.setUom("m");
            minVis.setValue(translatedMetar.getCommonWeatherSection().getMinimumVisibility());

            JAXBElement<DistanceWithNilReasonType> minV = iwxxmHelpers.getOfIWXXM().createAerodromeHorizontalVisibilityTypeMinimumVisibility(minVis);
            visibility.setMinimumVisibility(minV);

            if (translatedMetar.getCommonWeatherSection().getMinimumVisibilityDirection() != null) {
                Double dirAngleD = translatedMetar.getCommonWeatherSection().getMinimumVisibilityDirection().getDoubleValue();
                AngleWithNilReasonType minVisAngle = iwxxmHelpers.getOfIWXXM().createAngleWithNilReasonType();
                minVisAngle.setValue(dirAngleD);
                minVisAngle.setUom("deg");
                JAXBElement<AngleWithNilReasonType> minVisDirection = iwxxmHelpers.getOfIWXXM().createAerodromeHorizontalVisibilityTypeMinimumVisibilityDirection(minVisAngle);
                visibility.setMinimumVisibilityDirection(minVisDirection);
            }
            isSet = true;
        }

        // Prevailing visibility
        if (translatedMetar.getCommonWeatherSection().getPrevailVisibility() != null) {
            DistanceWithNilReasonType prevailVis = iwxxmHelpers.getOfIWXXM().createDistanceWithNilReasonType();
            prevailVis.setUom("m");
            if (translatedMetar.getCommonWeatherSection().getPrevailVisibility() == 9999) {
                prevailVis.setValue(9999);
                visibility.setPrevailingVisibility(prevailVis);
            } else {
                prevailVis.setValue(translatedMetar.getCommonWeatherSection().getPrevailVisibility());
                visibility.setPrevailingVisibility(prevailVis);
            }
            isSet = true;
        }

        resultVisibility.setAerodromeHorizontalVisibility(visibility);
        return isSet ? resultVisibility : null;
    }



    /**
     * Clouds in trend sections
     *
     * @throws WMORegisterException *
     */
    private AerodromeCloudPropertyType createCloudSectionTag(MetarCommonWeatherSection weatherSection,
            String icaoCode, int sectionIndex) throws WMORegisterException {
        // Envelop
        AerodromeCloudPropertyType cloudsType = iwxxmHelpers.getOfIWXXM().createAerodromeCloudPropertyType();

        // Body
        AerodromeCloudType clouds = iwxxmHelpers.getOfIWXXM().createAerodromeCloudType();
        boolean layersCreated = false;
        boolean cloudsCreated = false;

        for (METARCloudSection cloudSection : weatherSection.getCloudSections()) {

            if (cloudSection.getAmount() != null && cloudSection.getAmount().equalsIgnoreCase(WMOCloudRegister.verticalVisibilityCode)) {
                LengthWithNilReasonType vvType = iwxxmHelpers.getOfIWXXM().createLengthWithNilReasonType();
                vvType.setUom(LENGTH_UNITS.FT.getStringValue().replace("[ft_i]", "ft"));
                if (cloudSection.getHeight() != null && cloudSection.getHeight().isPresent()) {
                    vvType.setValue((double) cloudSection.getHeight().get());
                } else {
                    vvType.getNilReason().add(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE));
                }
                clouds.setVerticalVisibility(iwxxmHelpers.getOfIWXXM().createAerodromeCloudTypeVerticalVisibility(vvType));
                cloudsCreated = true;

            } else if (cloudSection.isNoCloudsDetected()) {

                String nilReasonUrl = iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE);
                // cloudsType.getNilReason().add(nilReasonUrl);

            } else if (cloudSection.isNoSignificantClouds()) {
                String nilReasonUrl = iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOTHING_OF_OPERATIONAL_SIGNIFICANCE);
                // cloudsType.getNilReason().add(nilReasonUrl);

            } else {
                CloudLayerType cl;
                if (cloudSection.getHeight() != null && cloudSection.getHeight().isPresent()) {
                    cl = iwxxmHelpers.createCloudLayerSection(cloudSection.getAmount(), (double) cloudSection.getHeight().get(), cloudSection.getType(), null, LENGTH_UNITS.FT);
                } else {
                    cl = iwxxmHelpers.createEmptyCloudLayerSection(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE));
                }
                AerodromeCloudType.Layer layer = new AerodromeCloudType.Layer();
                layer.setCloudLayer(cl);
                clouds.getLayer().add(layer);
                layersCreated = true;
            }
        }

        if (cloudsCreated || layersCreated) {
            cloudsType.setAerodromeCloud(clouds);
        }

        return cloudsType;
    }

    /**
     * Wind shear section Creates tag AerodromeWindShear if wind shear is reported for all or any of runways
     *
     * @return WindShear or null if wind shear was not reported
     */
    private _int.icao.iwxxm._2023_1.AerodromeWindShearPropertyType createWindShearTag() {

        _int.icao.iwxxm._2023_1.AerodromeWindShearPropertyType result = iwxxmHelpers.getOfIWXXM().createAerodromeWindShearPropertyType();
        AerodromeWindShearType windShear = iwxxmHelpers.getOfIWXXM().createAerodromeWindShearType();

        if (translatedMetar.isWindShearForAll()) {
            windShear.setAllRunways(true);
        } else {
            for (String rwWs : translatedMetar.getWindShearSections()) {
                RunwayDirectionPropertyType runwayType = iwxxmHelpers.createRunwayDesignatorSectionTag(translatedMetar.getIcaoCode(), rwWs);
                windShear.getRunway().add(runwayType);
            }
        }

        result.setAerodromeWindShear(windShear);
        return result;
    }

    private _int.icao.iwxxm._2023_1.AerodromeSeaConditionPropertyType createSeaCondition(MERTARSeaStateSection seaStateSection) throws WMORegisterException {

        _int.icao.iwxxm._2023_1.AerodromeSeaConditionPropertyType result = iwxxmHelpers.getOfIWXXM().createAerodromeSeaConditionPropertyType();
        AerodromeSeaConditionType seaConditionType = iwxxmHelpers.getOfIWXXM().createAerodromeSeaConditionType();

        if (seaStateSection.getTemperature() != null) {
            MeasureWithNilReasonType tempType = iwxxmHelpers.getOfIWXXM().createMeasureWithNilReasonType();
            tempType.setUom("Cel");
            tempType.setValue(seaStateSection.getTemperature().doubleValue());
            seaConditionType.setSeaSurfaceTemperature(tempType);
        }

        if (seaStateSection.getState() != null) {
            SeaSurfaceStateType stateStype = iwxxmHelpers.getOfIWXXM().createSeaSurfaceStateType();
            String href = this.iwxxmHelpers.getSeaSurfaceTypeRegister().getWMOUrlByCode(seaStateSection.getState().toString());
            stateStype.setHref(href);
            JAXBElement<SeaSurfaceStateType> seaSurfaceStateTypeElement = iwxxmHelpers.getOfIWXXM().createAerodromeSeaConditionTypeSeaState(stateStype);
            seaConditionType.setSeaState(seaSurfaceStateTypeElement);
        }

        result.setAerodromeSeaCondition(seaConditionType);
        return result;
    }

    /**
     * Creates Runway visual range tag
     */
    private _int.icao.iwxxm._2023_1.AerodromeRunwayVisualRangePropertyType createRVRTag(METARRVRSection rvrs) {

        _int.icao.iwxxm._2023_1.AerodromeRunwayVisualRangePropertyType rvrProp = iwxxmHelpers.getOfIWXXM().createAerodromeRunwayVisualRangePropertyType();
        AerodromeRunwayVisualRangeType rvr = iwxxmHelpers.getOfIWXXM().createAerodromeRunwayVisualRangeType();

        RunwayDirectionPropertyType runwayDir = null;
        if (!createdRunways.containsKey(rvrs.getRvrDesignator())) {
            runwayDir = iwxxmHelpers.createRunwayDesignatorSectionTag(translatedMetar.getIcaoCode(), rvrs.getRvrDesignator());
            createdRunways.put(rvrs.getRvrDesignator(), runwayDir.getRunwayDirection().getId());
        } else {
            runwayDir = iwxxmHelpers.getOfIWXXM().createRunwayDirectionPropertyType();
            runwayDir.setHref("#" + createdRunways.get(rvrs.getRvrDesignator()));
        }
        rvr.setRunway(runwayDir);

        if (rvrs.getRvrValue() != null) {
            DistanceWithNilReasonType meanLength = iwxxmHelpers.getOfIWXXM().createDistanceWithNilReasonType();
            meanLength.setUom("m");
            meanLength.setValue(rvrs.getRvrValue());
            rvr.setMeanRVR(meanLength);
        }

        if (rvrs.getOperator() != null) {
            RelationalOperatorType rvrOper = RelationalOperatorType.ABOVE;
            String op = rvrs.getOperator().toString();
            if (op.equalsIgnoreCase("P")) rvrOper = RelationalOperatorType.ABOVE;
            else if (op.equalsIgnoreCase("M")) rvrOper = RelationalOperatorType.BELOW;
            
            rvr.setMeanRVROperator(iwxxmHelpers.getOfIWXXM().createAerodromeRunwayVisualRangeTypeMeanRVROperator(rvrOper));
        }

        if (rvrs.getTendency() != null) {
            VisualRangeTendencyType vrTendency = VisualRangeTendencyType.MISSING_VALUE;
            switch (rvrs.getTendency()) {
                case D: vrTendency = VisualRangeTendencyType.DOWNWARD; break;
                case N: vrTendency = VisualRangeTendencyType.NO_CHANGE; break;
                case U: vrTendency = VisualRangeTendencyType.UPWARD; break;
            }
            rvr.setPastTendency(vrTendency);
        }

        rvrProp.setAerodromeRunwayVisualRange(rvr);
        return rvrProp;
    }

    /**
     * Creates Runway state tag to include into collection
     *
     * @throws WMORegisterException
     */
    private _int.icao.iwxxm._2023_1.AerodromeRunwayStatePropertyType createRunwayStateTag(METARRunwayStateSection rwrs)
            throws WMORegisterException {

        _int.icao.iwxxm._2023_1.AerodromeRunwayStatePropertyType rvrProp = iwxxmHelpers.getOfIWXXM().createAerodromeRunwayStatePropertyType();
        AerodromeRunwayStateType rvrState = iwxxmHelpers.getOfIWXXM().createAerodromeRunwayStateType();
        
        rvrState.setAllRunways(rwrs.isApplicableForAllRunways());
        rvrState.setCleared(rwrs.isCleared());

        if (!rwrs.isInApplicate()) {
            rvrState.setFromPreviousReport(true);
            RunwayDirectionPropertyType runwayDir = iwxxmHelpers.getOfIWXXM().createRunwayDirectionPropertyType();
            runwayDir.getNilReason().add(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_INAPPLICABLE));
            rvrState.setRunway(iwxxmHelpers.getOfIWXXM().createAerodromeRunwayStateTypeRunway(runwayDir));
        } else if (!rwrs.isApplicableForAllRunways()) {
            RunwayDirectionPropertyType runwayDir = null;
            if (!createdRunways.containsKey(rwrs.getRvrDesignator())) {
                runwayDir = iwxxmHelpers.createRunwayDesignatorSectionTag(translatedMetar.getIcaoCode(), rwrs.getRvrDesignator(), rwrs.getTrueBearing());
                createdRunways.put(rwrs.getRvrDesignator(), runwayDir.getRunwayDirection().getId());
            } else {
                runwayDir = iwxxmHelpers.getOfIWXXM().createRunwayDirectionPropertyType();
                runwayDir.setHref("#" + createdRunways.get(rwrs.getRvrDesignator()));
            }
            if (runwayDir != null) {
                rvrState.setRunway(iwxxmHelpers.getOfIWXXM().createAerodromeRunwayStateTypeRunway(runwayDir));
            }
        }

        if (rwrs.getType().isPresent()) {
            RunwayDepositsType dType = iwxxmHelpers.getOfIWXXM().createRunwayDepositsType();
            dType.setHref(iwxxmHelpers.getRwDepositReg().getWMOUrlByCode(rwrs.getType().get()));
            rvrState.setDepositType(iwxxmHelpers.getOfIWXXM().createAerodromeRunwayStateTypeDepositType(dType));
        }

        if (rwrs.getContamination().isPresent()) {
            RunwayContaminationType cType = iwxxmHelpers.getOfIWXXM().createRunwayContaminationType();
            cType.setHref(iwxxmHelpers.getRwContaminationReg().getWMOUrlByCode(rwrs.getContamination().get()));
            rvrState.setContamination(iwxxmHelpers.getOfIWXXM().createAerodromeRunwayStateTypeContamination(cType));
        }

        if (rwrs.getDepositDepth().isPresent()) {
            DistanceWithNilReasonType depth = iwxxmHelpers.getOfIWXXM().createDistanceWithNilReasonType();
            depth.setValue(rwrs.getDepositDepth().get());
            depth.setUom("mm");
            rvrState.setDepthOfDeposit(iwxxmHelpers.getOfIWXXM().createAerodromeRunwayStateTypeDepthOfDeposit(depth));
        }

        if (rwrs.getFriction().isPresent()) {
            RunwayFrictionCoefficientType frictionType = iwxxmHelpers.getOfIWXXM().createRunwayFrictionCoefficientType();
            frictionType.setHref(iwxxmHelpers.getRwFrictionReg().getWMOUrlByCode(rwrs.getFriction().get()));
            rvrState.setEstimatedSurfaceFrictionOrBrakingAction(iwxxmHelpers.getOfIWXXM().createAerodromeRunwayStateTypeEstimatedSurfaceFrictionOrBrakingAction(frictionType));
        }

        rvrProp.setAerodromeRunwayState(rvrState);
        return rvrProp;
    }

    private _int.icao.iwxxm._2023_1.AerodromeRunwayStatePropertyType createSNOCLORunwayStateTag()
            throws WMORegisterException {

        _int.icao.iwxxm._2023_1.AerodromeRunwayStatePropertyType rvrProp = iwxxmHelpers.getOfIWXXM().createAerodromeRunwayStatePropertyType();
        AerodromeRunwayStateType rvrState = iwxxmHelpers.getOfIWXXM().createAerodromeRunwayStateType();
        
        rvrState.setAllRunways(true);
        RunwayDirectionPropertyType runwayDir = iwxxmHelpers.getOfIWXXM().createRunwayDirectionPropertyType();
        runwayDir.getNilReason().add(iwxxmHelpers.getRwSnowRegister().getWMOUrlByCode(WMORunWaySnowRegister.NIL_REASON_SNOCLO));
        rvrState.setRunway(iwxxmHelpers.getOfIWXXM().createAerodromeRunwayStateTypeRunway(runwayDir));

        rvrProp.setAerodromeRunwayState(rvrState);
        return rvrProp;
    }

    /**
     * Creates issueTime
     */
    private TimeInstantPropertyType createIssueTimesection() {
        return iwxxmHelpers.createJAXBTimeSection(translatedMetar.getMessageIssueDateTime(),
                translatedMetar.getIcaoCode());
    }

    /*
	 * private MeteorologicalAerodromeTrendForecastPropertyType
	 * createTrendForecast(MetarForecastSection section, int sectionIndex) {
	 * 
	 * 
	 * MeteorologicalAerodromeTrendForecastPropertyType trendType
	 * =ofIWXXM.createMeteorologicalAerodromeTrendForecastPropertyType();
	 * MeteorologicalAerodromeTrendForecastType trend =
	 * ofIWXXM.createMeteorologicalAerodromeTrendForecastType();
	 * trendType.setMeteorologicalAerodromeTrendForecast(trend);
	 * 
	 * trend.setId(iwxxmHelpers.generateUUIDv4(String.format("cf-%d-%s",
	 * sectionIndex, translatedMetar.getIcaoCode())));
	 * 
	 * 
	 * // phenomenon time for metar
	 * 
	 * JAXBElement<TimePeriodType> timeElement = ofGML.createTimePeriod(iwxxmHelpers
	 * .createTrendPeriodSection(translatedMetar.getIcaoCode(),
	 * section.getTrendValidityInterval().getStart(),
	 * section.getTrendValidityInterval().getEnd(), sectionIndex) .getTimePeriod());
	 * 
	 * 
	 * 
	 * AbstractTimeObjectPropertyType timeType =
	 * ofIWXXM.createAbstractTimeObjectPropertyType();
	 * timeType.setAbstractTimeObject(timeElement);
	 * 
	 * trend.setPhenomenonTime(timeType);
	 * 
	 * return trendType;
	 * 
	 * }
     */
    /**
     * Robustly extracts weather phenomena from a METAR TAC string using standard WMO patterns.
     */
    private java.util.List<String> robustExtractWeather(String tac) {
        java.util.List<String> results = new java.util.ArrayList<>();
        if (tac == null || tac.isEmpty()) return results;

        // Regex for weather phenomena based on WMO 306 standards:
        // Intensity/Proximity: [-+]|VC
        // Descriptor: MI|PR|BC|DR|BL|SH|TS|FZ
        // Phenomena: DZ|RA|SN|SG|PL|IC|GR|GS|BR|FG|FU|VA|DU|SA|HZ|PO|SQ|FC|SS|DS
        String regex = "\\b([-+]|VC)?(MI|PR|BC|DR|BL|SH|TS|FZ)?(DZ|RA|SN|SG|PL|IC|GR|GS|BR|FG|FU|VA|DU|SA|HZ|PO|SQ|FC|SS|DS)+\\b";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(tac);
        while (m.find()) {
            String w = m.group();
            // Filter out airport codes (like VVTS) or other false positives
            if (w.equals(translatedMetar.getIcaoCode()) || w.equals("METAR") || w.equals("NOSIG") || w.equals("CAVOK")) {
                continue;
            }
            results.add(w);
        }
        return results;
    }
}


