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

import vn.asg.converter.tac.TAFTacMessage;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

import org.gamc.spmi.iwxxmConverter.exceptions.ParsingException;
import org.gamc.spmi.iwxxmConverter.general.TafForecastSection;
import org.gamc.spmi.iwxxmConverter.general.TafForecastTimeSection;
import org.gamc.spmi.iwxxmConverter.iwxxmenums.ANGLE_UNITS;
import org.gamc.spmi.iwxxmConverter.iwxxmenums.LENGTH_UNITS;
import org.gamc.spmi.iwxxmConverter.iwxxmenums.TEMPERATURE_UNITS;
import org.gamc.spmi.iwxxmConverter.tafconverter.TAFCloudSection;
import org.gamc.spmi.iwxxmConverter.tafconverter.TafCommonWeatherSection;
import org.gamc.spmi.iwxxmConverter.wmo.WMOCloudRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMONilReasonRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMORegister.WMORegisterException;
import org.joda.time.DateTime;
import _int.icao.iwxxm._2023_1.AerodromeAirTemperatureForecastPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeAirTemperatureForecastType;
import _int.icao.iwxxm._2023_1.AerodromeCloudForecastPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeCloudForecastType;
import _int.icao.iwxxm._2023_1.AerodromeForecastChangeIndicatorType;
import _int.icao.iwxxm._2023_1.AerodromeForecastWeatherType;
import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindForecastPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindForecastType;
import _int.icao.iwxxm._2023_1.AirportHeliportPropertyType;
import _int.icao.iwxxm._2023_1.CloudLayerPropertyType;
import _int.icao.iwxxm._2023_1.LengthWithNilReasonType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeForecastPropertyType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeForecastType;
import _int.icao.iwxxm._2023_1.PermissibleUsageReasonType;
import _int.icao.iwxxm._2023_1.PermissibleUsageType;
import _int.icao.iwxxm._2023_1.RelationalOperatorType;
import _int.icao.iwxxm._2023_1.ReportStatusType;
import _int.icao.iwxxm._2023_1.TAFType;
import vn.asg.converter.config.App;
import net.opengis.gml.v_3_2_1.AngleType;
import net.opengis.gml.v_3_2_1.LengthType;
import net.opengis.gml.v_3_2_1.MeasureType;
import net.opengis.gml.v_3_2_1.SpeedType;
import net.opengis.gml.v_3_2_1.StringOrRefType;
import net.opengis.gml.v_3_2_1.TimeInstantPropertyType;
import net.opengis.gml.v_3_2_1.TimePeriodPropertyType;
import org.gamc.spmi.iwxxmConverter.common.CoreUtil;
import org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException;

/**
 * Base class to perform conversion of TAC into intermediate object {@link TAFTacMessage} and further IWXXM conversion and validation
 */
public class TAFConverterV3 extends TacBaseConverter<TAFTacMessage, TAFType> {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(TAFConverterV3.class);
    /**
     * Our own helpers to suppress boiler-plate code
     */
    // IWXXM31Helpers iwxxmHelpers = new IWXXM31Helpers();
    private String identifier;

    private String dateTime = "";
    private String dateTimePosition = "";

    private String timePeriodBegin = "";
    private String timePeriodEnd = "";

    private String timePeriodBeginPosition = "";
    private String timePeriodEndPosition = "";

    private TAFTacMessage translatedTaf;
    // protected Logger logger = LoggerFactory.getLogger(TAFConverterV3.class);

    /**
     * Converts given TAC string to IWXXM string
     *
     * @param tac - TAC to convert
     * @return - XML String in IWXXM format
     * @throws org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException
     * @throws WMORegisterException
     */
    @Override
    public String convertTacToXML(String tac) throws ConvertingFailException {
        try {
            logger.info(String.format("Converting to XML"));
            TAFTacMessage tafMessage = new TAFTacMessage(tac);
            TAFType result;
            tafMessage.parseMessage();
            result = convertMessage(tafMessage);
            String xmlResult = marshallMessageToXML(result);
            return xmlResult;

        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    @Override
    public byte[] convertTacToXML(String tac, boolean zipped) throws ConvertingFailException {
        try {
            logger.info(String.format("Converting to File"));
            TAFTacMessage tafMessage = new TAFTacMessage(tac);
            TAFType result;
            tafMessage.parseMessage();
            result = convertMessage(tafMessage);
            this.identifier = translatedTaf.getIdentifier();
            if (zipped) {
                this.identifier += ".gz";
            }
            return marshallMessageToByte(result, zipped);
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    @Override
    public String getIdentifier() {
        return this.translatedTaf.getIdentifier();
    }

    @Override
    protected JAXBElement<TAFType> createJaxbElement(TAFType r) {
        return iwxxmHelpers.getOfIWXXM().createTAF(r);
    }

    @Override
    protected String postProcessXML(String xml) {
        // IWXXM 3.0 requires O&M namespaces for observation wrapper
        if (!xml.contains("xmlns:om=")) {
            xml = xml.replace("<iwxxm:TAF", "<iwxxm:TAF xmlns:om=\"http://www.opengis.net/om/2.0\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"");
        }

        // JAXB generates MeteorologicalAerodromeForecast but IWXXM schema requires Record suffix
        xml = xml.replace("iwxxm:MeteorologicalAerodromeForecast ", "iwxxm:MeteorologicalAerodromeForecastRecord ");
        xml = xml.replace("iwxxm:MeteorologicalAerodromeForecast>", "iwxxm:MeteorologicalAerodromeForecastRecord>");
        xml = xml.replace("</iwxxm:MeteorologicalAerodromeForecast>", "</iwxxm:MeteorologicalAerodromeForecastRecord>");

        // IWXXM baseForecast must be wrapped in om:OM_Observation (§3.2.1 of IWXXM spec)
        String recordStartTag = "<iwxxm:MeteorologicalAerodromeForecastRecord";
        int startIdx = xml.indexOf(recordStartTag);
        if (startIdx == -1) return xml;

        int baseForecastEndIdx = xml.indexOf("</iwxxm:baseForecast>");
        if (baseForecastEndIdx == -1) return xml;

        // CRITICAL FIX: Use indexOf (not lastIndexOf) to capture ONLY base forecast record
        // Using lastIndexOf would incorrectly include changeForecast records, causing duplication
        int recordEndIdx = xml.indexOf("</iwxxm:MeteorologicalAerodromeForecastRecord>", startIdx);
        if (recordEndIdx == -1) return xml;

        String recordContent = xml.substring(startIdx, recordEndIdx + "</iwxxm:MeteorologicalAerodromeForecastRecord>".length());

        // Build om:OM_Observation wrapper per ISO 19156 (O&M) standard
        // Extract validity period times from existing GML tags to populate phenomenonTime
        String timePos = "";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<gml:beginPosition>([^<]+)</gml:beginPosition>");
        java.util.regex.Matcher m = p.matcher(xml);
        if (m.find()) {
            timePos = m.group(1);
        }

        String icao = translatedTaf.getIcaoCode();
        String obsId = "fcst-" + icao + "-" + timePos.replace(":", "").replace("-", "");

        StringBuilder omWrap = new StringBuilder();
        omWrap.append("<om:OM_Observation gml:id=\"").append(obsId).append("\">\n");
        omWrap.append("            <om:type xlink:href=\"http://codes.wmo.int/49-2/observation-type/IWXXM/2023-1/MeteorologicalAerodromeForecast\"/>\n");
        omWrap.append("            <om:phenomenonTime>\n");
        omWrap.append("                <gml:TimePeriod gml:id=\"tp-").append(obsId).append("\">\n");
        omWrap.append("                    <gml:beginPosition>").append(timePos).append("</gml:beginPosition>\n");

        String endPos = "";
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("<gml:endPosition>([^<]+)</gml:endPosition>");
        java.util.regex.Matcher m2 = p2.matcher(xml);
        if (m2.find()) {
            endPos = m2.group(1);
        }
        omWrap.append("                    <gml:endPosition>").append(endPos).append("</gml:endPosition>\n");
        omWrap.append("                </gml:TimePeriod>\n");
        omWrap.append("            </om:phenomenonTime>\n");
        omWrap.append("            <om:resultTime xlink:href=\"#ti-issue-").append(icao).append("\"/>\n");
        omWrap.append("            <om:procedure xlink:href=\"#proc-1\"/>\n");
        omWrap.append("            <om:observedProperty xlink:href=\"http://codes.wmo.int/49-2/observable-property/MeteorologicalAerodromeForecast\"/>\n");
        omWrap.append("            <om:featureOfInterest xlink:href=\"#aerodrome-").append(icao).append("\"/>\n");
        omWrap.append("            <om:result>\n");
        omWrap.append("                ").append(recordContent).append("\n");
        omWrap.append("            </om:result>\n");
        omWrap.append("        </om:OM_Observation>");

        // Inject om:OM_Observation wrapper into baseForecast section
        int outerStart = xml.indexOf("<iwxxm:baseForecast>") + "<iwxxm:baseForecast>".length();
        int outerEnd = xml.indexOf("</iwxxm:baseForecast>");

        xml = xml.substring(0, outerStart) + "\n        " + omWrap.toString() + "\n    " + xml.substring(outerEnd);

        // JAXB serializes changeIndicator as XML attribute, but IWXXM schema requires child element
        // Map attribute values to WMO code URLs and convert to proper element format
        xml = convertChangeIndicatorAttributeToElement(xml, "BECOMING", "BECOMING");
        xml = convertChangeIndicatorAttributeToElement(xml, "TEMPORARY_FLUCTUATIONS", "TEMPO");
        xml = convertChangeIndicatorAttributeToElement(xml, "FROM", "FROM");
        xml = convertChangeIndicatorAttributeToElement(xml, "PROBABILITY_30", "PROB30");
        xml = convertChangeIndicatorAttributeToElement(xml, "PROBABILITY_40", "PROB40");
        xml = convertChangeIndicatorAttributeToElement(xml, "PROBABILITY_30_TEMPORARY_FLUCTUATIONS", "PROB30_TEMPO");
        xml = convertChangeIndicatorAttributeToElement(xml, "PROBABILITY_40_TEMPORARY_FLUCTUATIONS", "PROB40_TEMPO");

        // Add observationProcess definition (referenced by om:procedure xlink)
        if (!xml.contains("gml:id=\"proc-1\"")) {
            String procDef = "\n    <iwxxm:observationProcess gml:id=\"proc-1\">\n" +
                             "        <iwxxm:processType xlink:href=\"http://codes.wmo.int/common/procedure/automatedStation\"/>\n" +
                             "    </iwxxm:observationProcess>";
            xml = xml.replace("</iwxxm:TAF>", procDef + "\n</iwxxm:TAF>");
        }

        return xml;
    }

    /**
     * Converts changeIndicator from XML attribute to child element with WMO code URL.
     * JAXB generates: changeIndicator="BECOMING"
     * IWXXM requires: <iwxxm:changeIndicator xlink:href="http://codes.wmo.int/.../BECOMING"/>
     *
     * @param xml XML content
     * @param attributeValue Attribute value from JAXB (e.g., "BECOMING", "TEMPORARY_FLUCTUATIONS")
     * @param wmoCode WMO code for href URL (e.g., "BECOMING", "TEMPO")
     * @return Modified XML
     */
    private String convertChangeIndicatorAttributeToElement(String xml, String attributeValue, String wmoCode) {
        // Pattern matches: <iwxxm:MeteorologicalAerodromeForecastRecord changeIndicator="VALUE" ... gml:id="..." ...>
        String attributePattern = "changeIndicator=\"" + attributeValue + "\"\\s*";
        String elementUrl = "http://codes.wmo.int/49-2/TrendForecastChangeIndicator/" + wmoCode;

        // Find and replace each occurrence
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(<iwxxm:MeteorologicalAerodromeForecastRecord[^>]*)" + attributePattern + "([^>]*>)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(xml);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String openingTag = matcher.group(1) + matcher.group(2);
            String replacement = openingTag + "\n            <iwxxm:changeIndicator xlink:href=\"" + elementUrl + "\"/>";
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Mapps internal TAC representation to JAXB-objects
     *
     * @param translatedTaf - TAC as internal object
     * @return XML String in IWXXM format
     * @throws WMORegisterException
     */
    @Override
    public TAFType convertMessage(TAFTacMessage translatedTaf)
            throws DatatypeConfigurationException, UnsupportedEncodingException, JAXBException, ParsingException, WMORegisterException {

        try {
            this.translatedTaf = translatedTaf;

            // set common TAF time
            dateTime = translatedTaf.getMessageIssueDateTime().toString(iwxxmHelpers.getDateTimeFormat()) + "Z";
            dateTimePosition = translatedTaf.getMessageIssueDateTime().toString(iwxxmHelpers.getDateTimeISOFormat());

            // <iwxxm:TAF> root tag
            TAFType tafRootTag = iwxxmHelpers.getOfIWXXM().createTAFType();

            // Id with ICAO code and current timestamp
            tafRootTag.setId(iwxxmHelpers.generateUUIDv4(String.format("taf-%s-%s", translatedTaf.getIcaoCode(), dateTime)));

            // Set NON_OPERATIONAL and TEST properties.
            // tafRootTag.setPermissibleUsage(PermissibleUsageType.NON_OPERATIONAL);
            // tafRootTag.setPermissibleUsageReason(PermissibleUsageReasonType.TEST);
            PermissibleUsageType permissibleUsageType = PermissibleUsageType.valueOf(App.getInstance().getString("PermissibleUsage"));
            tafRootTag.setPermissibleUsage(permissibleUsageType);
            if (permissibleUsageType == PermissibleUsageType.NON_OPERATIONAL) {
                // Non-Operational Reason
                tafRootTag.setPermissibleUsageReason(PermissibleUsageReasonType.valueOf(App.getInstance().getString("PermissibleUsageReason")));
                // Some description
                tafRootTag.setPermissibleUsageSupplementary(App.getInstance().getString("PermissibleUsageSupplementary"));
            }

            // COR, AMD, CNL, NORMAL
            switch (translatedTaf.getMessageStatusType()) {
                case AMENDMENT:
                    tafRootTag.setReportStatus(ReportStatusType.AMENDMENT);
                    break;
                case CANCEL:
                    tafRootTag.setReportStatus(null);
                    break;
                case CORRECTION:
                    tafRootTag.setReportStatus(ReportStatusType.CORRECTION);
                    break;
                default:
                    tafRootTag.setReportStatus(ReportStatusType.NORMAL);
            }

            // Using desciption to quote the origin message for debuggin
            /**
             * <gml:description xlink:type="simple"></gml:description>
             */
            if (App.getInstance().getBoolean("AddTacContent", false)) {
                StringOrRefType refTacString = iwxxmHelpers.getOfGML().createStringOrRefType();
                refTacString.setValue(translatedTaf.getInitialTacString());
                tafRootTag.setDescription(refTacString);
            }

            // Some description
            // tafRootTag.setPermissibleUsageSupplementary("TAF composing test using JAXB");
            // Begining of the content
            // <iwxxm:aerodrome></iwxxm:aerodrome>
            tafRootTag.setAerodrome(createAirportDescriptionSectionTag());

            tafRootTag = addTranslationCentreHeader(tafRootTag);

            // issuetime and valid period are top-level tags
            tafRootTag.setIssueTime(createIssueTimesection());

            if (translatedTaf.isNil()) {

                MeteorologicalAerodromeForecastPropertyType recordPropertyType = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeForecastPropertyType();
                recordPropertyType.getNilReason().add(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_MISSING));
                tafRootTag.setBaseForecast(recordPropertyType);
                return tafRootTag;

            }

            timePeriodBeginPosition = translatedTaf.getValidityInterval().getStart().toString(iwxxmHelpers.getDateTimeISOFormat());
            timePeriodEndPosition = translatedTaf.getValidityInterval().getEnd().toString(iwxxmHelpers.getDateTimeISOFormat());
            timePeriodBegin = translatedTaf.getValidityInterval().getStart().toString(iwxxmHelpers.getDateTimeFormat()) + "Z";
            timePeriodEnd = translatedTaf.getValidityInterval().getEnd().toString(iwxxmHelpers.getDateTimeFormat()) + "Z";

            if (translatedTaf.getCommonWeatherSection().isCancelled()) {
                tafRootTag.setIsCancelReport(true);
                tafRootTag.setCancelledReportValidPeriod(iwxxmHelpers.createTimePeriod(translatedTaf.getIcaoCode(), translatedTaf.getValidityInterval().getStart(), translatedTaf.getValidityInterval().getEnd()));
                return tafRootTag;
            }

            TimePeriodPropertyType timePeriodPropertyType = iwxxmHelpers.createTimePeriod(
                    translatedTaf.getIcaoCode(),
                    translatedTaf.getValidityInterval().getStart(),
                    translatedTaf.getValidityInterval().getEnd());
            // tafRootTag.setValidPeriod(iwxxmHelpers.createTimePeriod(translatedTaf.getIcaoCode(), translatedTaf.getValidityInterval().getStart(), translatedTaf.getValidityInterval().getEnd()));
            tafRootTag.setValidPeriod(timePeriodPropertyType);

            // Compose TAF body message and place it in the root
            String refId = timePeriodPropertyType.getTimePeriod().getId();
            tafRootTag.setBaseForecast(createBaseResultSection(refId));

            // TODO : create change section for TrendForecast and possible Extensions (RMK)
            AtomicInteger globalSectionIndex = new AtomicInteger(1);
            for (TafForecastSection bcmgSection : translatedTaf.getBecomingSections()) {
                bcmgSection.parseSection();
                MeteorologicalAerodromeForecastPropertyType trendSectionforecast = createTrendResultsSection(bcmgSection, globalSectionIndex.getAndIncrement());
                tafRootTag.getChangeForecast().add(trendSectionforecast);
            }

            for (TafForecastSection tempoSection : translatedTaf.getTempoSections()) {
                tempoSection.parseSection();
                MeteorologicalAerodromeForecastPropertyType trendSectionforecast = createTrendResultsSection(tempoSection, globalSectionIndex.getAndIncrement());
                tafRootTag.getChangeForecast().add(trendSectionforecast);
            }

            for (TafForecastTimeSection timedSection : translatedTaf.getTimedSections()) {
                timedSection.parseSection();
                MeteorologicalAerodromeForecastPropertyType trendTimedSectionforecast = createTrendResultsSection(timedSection, globalSectionIndex.getAndIncrement());
                tafRootTag.getChangeForecast().add(trendTimedSectionforecast);
            }

            for (TafForecastSection probSection : translatedTaf.getProbabilitySections()) {
                probSection.parseSection();
                MeteorologicalAerodromeForecastPropertyType probabilitySectionforecast = createTrendResultsSection(probSection, globalSectionIndex.getAndIncrement());
                tafRootTag.getChangeForecast().add(probabilitySectionforecast);
            }

            return tafRootTag;
        } catch (ParsingException ex) {
            throw new ParsingException(ex.getMessage() + " in [" + CoreUtil.truncateString(this.translatedTaf.getInitialTacString(), 25) + "]", ex);
        }
    }

    /**
     * Creates issueTime
     */
    private TimeInstantPropertyType createIssueTimesection() {
        return iwxxmHelpers.createJAXBTimeSection(translatedTaf.getMessageIssueDateTime(), translatedTaf.getIcaoCode());
    }

    /**
     * Creates base section of IWXXM TAF
     */
    /*
	private MeteorologicalAerodromeForecastPropertyType createBaseForecast() {

		// тег <om:OM_Observation>
		OMObservationPropertyType omOM_Observation = ofOM.createOMObservationPropertyType();
		OMObservationType ot = ofOM.createOMObservationType();
		ot.setId(iwxxmHelpers.generateUUIDv4(String.format("bf-%s-%s", translatedTaf.getIcaoCode(), dateTime)));

		// тип наблюдения - ссылка xlink:href
		ReferenceType observeType = ofGML.createReferenceType();
		observeType.setHref(UriConstants.OBSERVATION_TYPE_TAF);
		ot.setType(observeType);

		//ot.set(createAirportDescriptionSectionTag());

		// phenomenon time for taf always equals to validityPeriod
		TimeObjectPropertyType phenomenonTimeProperty = ofOM.createTimeObjectPropertyType();
		JAXBElement<TimePeriodType> timeElement = ofGML.createTimePeriod(iwxxmHelpers
				.createTrendPeriodSection(translatedTaf.getIcaoCode(), translatedTaf.getValidityInterval().getStart(),
						translatedTaf.getValidityInterval().getEnd(), 0)
				.getTimePeriod());
		phenomenonTimeProperty.setAbstractTimeObject(timeElement);
		ot.setPhenomenonTime(phenomenonTimeProperty);

		// result time for taf = link to issueTime
		TimeInstantPropertyType resultTime = ofGML.createTimeInstantPropertyType();
		resultTime.setHref("#" + createIssueTimesection().getTimeInstant().getId());
		ot.setResultTime(resultTime);

		// valid time - link to valid time
		TimePeriodPropertyType validTime = ofGML.createTimePeriodPropertyType();
		validTime.setHref("#" + createValidityPeriodSection().getTimePeriod().getId());
		ot.setValidTime(validTime);

		// create <om:procedure> frame
		ProcessType metceProcess = ofMetce.createProcessType();
		metceProcess.setId(iwxxmHelpers.generateUUIDv4("p-49-2-taf-" + translatedTaf.getIcaoCode()));

		StringOrRefType processDescription = ofGML.createStringOrRefType();
		processDescription.setValue(StringConstants.WMO_49_2_METCE_TAF);
		metceProcess.setDescription(processDescription);

		OMProcessPropertyType omProcedure = ofOM.createOMProcessPropertyType();
		omProcedure.setAny(ofMetce.createProcess(metceProcess));
		ot.setProcedure(omProcedure);

		// тег om:ObserverdProperty
		ReferenceType observedProperty = ofGML.createReferenceType();
		observedProperty.setHref(UriConstants.OBSERVED_PROPERTY_TAF);
		// observedProperty.setTitle(StringConstants.WMO_TAF_OBSERVED_PROPERTY_TITLE);
		ot.setObservedProperty(observedProperty);

		// set result section
		//ot.setResult(createBaseResultSection());

		omOM_Observation.setOMObservation(ot);

		return omOM_Observation;
	}
     */
    /**
     * Result section of the BASE taf
     *
     * @throws WMORegisterException
     */
    private MeteorologicalAerodromeForecastPropertyType createBaseResultSection(String refTimePhenomenonID) throws WMORegisterException {

        MeteorologicalAerodromeForecastPropertyType recordPropertyType = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeForecastPropertyType();
        MeteorologicalAerodromeForecastType recordType = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeForecastType();
        recordType.setId(iwxxmHelpers.generateUUIDv4(String.format("base-fcst-record-%s", translatedTaf.getIcaoCode())));

        // Set PhenomenonTime
        TimePeriodPropertyType timePeriodProperty = iwxxmHelpers.createRefTimePeriod(refTimePhenomenonID);
        recordType.setPhenomenonTime(timePeriodProperty);

        // CAVOK
        recordType.setCloudAndVisibilityOK(translatedTaf.getCommonWeatherSection().isCavok());

        // visibility
        if (translatedTaf.getCommonWeatherSection().getPrevailVisibility() != null) {
            LengthType vis = iwxxmHelpers.getOfGML().createLengthType();
            vis.setUom(translatedTaf.getCommonWeatherSection().getVisibilityUnits().getStringValue());
            vis.setValue(translatedTaf.getCommonWeatherSection().getPrevailVisibility());
            recordType.setPrevailingVisibility(vis);
        }
        recordPropertyType.setMeteorologicalAerodromeForecast(recordType);

        // surfaceWind
        AerodromeSurfaceWindForecastPropertyType sWindpropertyType = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindForecastPropertyType();
        AerodromeSurfaceWindForecastType sWindType = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindForecastType();

        // Set gust speed
        if (translatedTaf.getCommonWeatherSection().getGustSpeed() != null) {
            SpeedType speedGustType = iwxxmHelpers.getOfGML().createSpeedType();
            speedGustType.setUom(translatedTaf.getCommonWeatherSection().getSpeedUnits().getStringValue().replace("[kn_i]", "kt"));
            speedGustType.setValue(translatedTaf.getCommonWeatherSection().getGustSpeed());
            sWindType.setWindGustSpeed(speedGustType);
        }

        // VRB?
        if (translatedTaf.getCommonWeatherSection().isVrb()) {
            sWindType.setVariableWindDirection(translatedTaf.getCommonWeatherSection().isVrb());
            SpeedType speedMeanType = iwxxmHelpers.getOfGML().createSpeedType();
            speedMeanType.setUom(translatedTaf.getCommonWeatherSection().getVrbSpeedUnits().getStringValue().replace("[kn_i]", "kt"));
            speedMeanType.setValue(translatedTaf.getCommonWeatherSection().getWindVrbSpeed());
            sWindType.setMeanWindSpeed(speedMeanType);
        } else {
            // Set mean wind
            SpeedType speedMeanType = iwxxmHelpers.getOfGML().createSpeedType();
            speedMeanType.setUom(translatedTaf.getCommonWeatherSection().getSpeedUnits().getStringValue().replace("[kn_i]", "kt"));
            speedMeanType.setValue(translatedTaf.getCommonWeatherSection().getWindSpeed());
            sWindType.setMeanWindSpeed(speedMeanType);
        }

        // Set wind direction
        if (!translatedTaf.getCommonWeatherSection().isVrb()) {
            AngleType windAngle = iwxxmHelpers.getOfGML().createAngleType();
            windAngle.setUom(ANGLE_UNITS.DEGREES.getStringValue());
            windAngle.setValue(translatedTaf.getCommonWeatherSection().getWindDir());
            sWindType.setMeanWindDirection(windAngle);
        }

        sWindpropertyType.setAerodromeSurfaceWindForecast(sWindType);
        recordType.setSurfaceWind(sWindpropertyType);

        // clouds
        recordType.setCloud(createCloudSectionTag(translatedTaf.getCommonWeatherSection(), translatedTaf.getIcaoCode(), 0));

        // Min and Max temperatures from taf
        AerodromeAirTemperatureForecastPropertyType temperatureForecast = createTemperaturesSection();
        if (temperatureForecast != null) {
            recordType.getTemperature().add(createTemperaturesSection());
        }

        // forecasted weather
        for (String weatherCode : translatedTaf.getCommonWeatherSection().getCurrentWeather()) {
            recordType.getWeather().add(createWeatherSection(weatherCode));
        }

        return recordPropertyType;
    }

    /**
     * Body for trend sections
     *
     * @param section - {@link TafForecastSection} object
     * @param sectionIndex - index of the processing section
     * @return {@link MeteorologicalAerodromeForecastRecordPropertyType} object
     * @throws WMORegisterException
     */
    private MeteorologicalAerodromeForecastPropertyType createTrendResultsSection(TafForecastSection section, int sectionIndex) throws WMORegisterException {

        MeteorologicalAerodromeForecastPropertyType recordPropertyType = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeForecastPropertyType();
        MeteorologicalAerodromeForecastType recordType = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeForecastType();
        recordPropertyType.setMeteorologicalAerodromeForecast(recordType);

        // set id
        recordType.setId(iwxxmHelpers.generateUUIDv4(String.format("change-record-%d-%s", sectionIndex, translatedTaf.getIcaoCode())));
        AerodromeForecastChangeIndicatorType changeIndicator = AerodromeForecastChangeIndicatorType.BECOMING;

        switch (section.getSectionType()) {
            case BECMG:
                changeIndicator = AerodromeForecastChangeIndicatorType.BECOMING;
                break;
            case TEMPO:
                changeIndicator = AerodromeForecastChangeIndicatorType.TEMPORARY_FLUCTUATIONS;
                break;
            case PROB30:
                changeIndicator = AerodromeForecastChangeIndicatorType.PROBABILITY_30;
                break;
            case PROB40:
                changeIndicator = AerodromeForecastChangeIndicatorType.PROBABILITY_40;
                break;
            case PROB30TEMPO:
                changeIndicator = AerodromeForecastChangeIndicatorType.PROBABILITY_30_TEMPORARY_FLUCTUATIONS;
                break;
            case PROB40TEMPO:
                changeIndicator = AerodromeForecastChangeIndicatorType.PROBABILITY_40_TEMPORARY_FLUCTUATIONS;
                break;
            case FM:
                changeIndicator = AerodromeForecastChangeIndicatorType.FROM;
                break;
            case INTER:
                changeIndicator = AerodromeForecastChangeIndicatorType.TEMPORARY_FLUCTUATIONS;
                break;
            default:
                break;
        }
        recordType.setChangeIndicator(changeIndicator);
        // CAVOK
        recordType.setCloudAndVisibilityOK(section.getCommonWeatherSection().isCavok());

        recordType.setPhenomenonTime(iwxxmHelpers.createTimePeriod(translatedTaf.getIcaoCode(), section.getTrendValidityInterval().getStart(), section.getTrendValidityInterval().getEnd()));

        // visibility
        if (section.getCommonWeatherSection().getPrevailVisibility() != null) {
            LengthType vis = iwxxmHelpers.getOfGML().createLengthType();
            vis.setUom(section.getCommonWeatherSection().getVisibilityUnits().getStringValue());

            if (section.getCommonWeatherSection().getPrevailVisibility() == 9999) {
                vis.setValue(10000);
                recordType.setPrevailingVisibility(vis);
                recordType.setPrevailingVisibilityOperator(RelationalOperatorType.ABOVE);

            } else if (section.getCommonWeatherSection().getPrevailVisibility() == 0) {
                vis.setValue(50);
                recordType.setPrevailingVisibility(vis);
                recordType.setPrevailingVisibilityOperator(RelationalOperatorType.BELOW);

            } else {
                vis.setValue(section.getCommonWeatherSection().getPrevailVisibility());
                recordType.setPrevailingVisibility(vis);
            }

            // vis.setValue(section.getCommonWeatherSection().getPrevailVisibility());
            // recordType.setPrevailingVisibility(vis);
        }

        // surfaceWind
        AerodromeSurfaceWindForecastPropertyType sWindpropertyType = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindForecastPropertyType();
        AerodromeSurfaceWindForecastType sWindType = iwxxmHelpers.getOfIWXXM().createAerodromeSurfaceWindForecastType();
        boolean sectionHasWind = false;

        // VRB?
        if (section.getCommonWeatherSection().isVrb()) {
            sWindType.setVariableWindDirection(section.getCommonWeatherSection().isVrb());
            SpeedType speedMeanType = iwxxmHelpers.getOfGML().createSpeedType();
            speedMeanType.setUom(section.getCommonWeatherSection().getVrbSpeedUnits().getStringValue().replace("[kn_i]", "kt"));
            speedMeanType.setValue(section.getCommonWeatherSection().getWindVrbSpeed());
            sWindType.setMeanWindSpeed(speedMeanType);
            sectionHasWind = true;
        }
        // Set gust speed
        if (section.getCommonWeatherSection().getGustSpeed() != null) {
            SpeedType speedGustType = iwxxmHelpers.getOfGML().createSpeedType();
            speedGustType.setUom(section.getCommonWeatherSection().getSpeedUnits().getStringValue().replace("[kn_i]", "kt"));
            speedGustType.setValue(section.getCommonWeatherSection().getGustSpeed());
            sWindType.setWindGustSpeed(speedGustType);
            sectionHasWind = true;
        }

        // Set mean wind
        if (section.getCommonWeatherSection().getWindSpeed() != null) {
            SpeedType speedMeanType = iwxxmHelpers.getOfGML().createSpeedType();
            speedMeanType.setUom(section.getCommonWeatherSection().getSpeedUnits().getStringValue().replace("[kn_i]", "kt"));
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
            sWindpropertyType.setAerodromeSurfaceWindForecast(sWindType);
            recordType.setSurfaceWind(sWindpropertyType);
        }

        // clouds
        if (section.getCommonWeatherSection().getCloudSections().size() > 0) {
            AerodromeCloudForecastPropertyType cloudType = createCloudSectionTag(section.getCommonWeatherSection(), translatedTaf.getIcaoCode(), sectionIndex);

            recordType.setCloud(cloudType);
        }

        // forecasted weather
        for (String weatherCode : section.getCommonWeatherSection().getCurrentWeather()) {
            recordType.getWeather().add(iwxxmHelpers.createForecastWeatherSection(weatherCode));
        }

        return recordPropertyType;
    }

    /**
     * Creates XML section for change forecast node - TEMPO OR BECOMING
     */
    /**
     * @param sectionIndex - the index of section among all change sections to create unique id for it
     */
    /*
	private MeteorologicalAerodromeForecastPropertyType createTrendForecast(TafForecastSection section, int sectionIndex) {

		MeteorologicalAerodromeForecastPropertyType trendType = ofIWXXM.createMeteorologicalAerodromeForecastPropertyType();
		MeteorologicalAerodromeForecastType trend = ofIWXXM.createMeteorologicalAerodromeForecastType();
		trendType.setMeteorologicalAerodromeForecast(trend);
		
		
		trend.setId(iwxxmHelpers.generateUUIDv4(String.format("cf-%d-%s", sectionIndex, translatedTaf.getIcaoCode())));

		// phenomenon time for taf always equals to validityPeriod
		TimePeriodPropertyType phenomenonTimeProperty = ofGML.createTimePeriodPropertyType();
		
		TimePeriodType tp = iwxxmHelpers
				.createTrendPeriodSection(translatedTaf.getIcaoCode(), section.getTrendValidityInterval().getStart(),
						section.getTrendValidityInterval().getEnd(), sectionIndex)
				.getTimePeriod();
		phenomenonTimeProperty.setTimePeriod(tp);

		trend.setPhenomenonTime(phenomenonTimeProperty);

	

		
		// тег om:ObserverdProperty
		ReferenceType observedProperty = ofGML.createReferenceType();
		observedProperty.setHref(UriConstants.OBSERVED_PROPERTY_TAF);
		// observedProperty.setTitle(StringConstants.WMO_TAF_OBSERVED_PROPERTY_TITLE);
		ot.setObservedProperty(observedProperty);
		
		// set result section
		t.setResult(createTrendResultsSection(section, sectionIndex));

		

		return trendType;

	}
     */
    /**
     * Creates sections for min and max temperatures forecasted in TAF
     */
    private AerodromeAirTemperatureForecastPropertyType createTemperaturesSection() {

        AerodromeAirTemperatureForecastPropertyType tempPropertyType = iwxxmHelpers.getOfIWXXM().createAerodromeAirTemperatureForecastPropertyType();
        AerodromeAirTemperatureForecastType temps = iwxxmHelpers.getOfIWXXM().createAerodromeAirTemperatureForecastType();

        if (translatedTaf.getCommonWeatherSection().getAirTemperatureMin() == null && translatedTaf.getCommonWeatherSection().getAirTemperatureMax() == null) {
            return null;
        }

        boolean isSet = false;
        // Set min temperature
        if (translatedTaf.getCommonWeatherSection().getAirTemperatureMin() != null) {
            MeasureType minTemperature = iwxxmHelpers.getOfGML().createMeasureType();
            minTemperature.setUom(TEMPERATURE_UNITS.CELSIUS.getStringValue());
            minTemperature.setValue(translatedTaf.getCommonWeatherSection().getAirTemperatureMin().doubleValue());

            TimeInstantPropertyType timeInstantMinTempProperty = iwxxmHelpers.createTimeInstantPropertyTypeForDateTime(
                    translatedTaf.getCommonWeatherSection().getAirTemperatureMinTime(), translatedTaf.getIcaoCode(), "tn");

            temps.setMinimumAirTemperature(minTemperature);
            temps.setMinimumAirTemperatureTime(timeInstantMinTempProperty);
            isSet = true;
        }

        // Set max temperature
        if (translatedTaf.getCommonWeatherSection().getAirTemperatureMax() != null) {
            MeasureType maxTemperature = iwxxmHelpers.getOfGML().createMeasureType();
            maxTemperature.setUom(TEMPERATURE_UNITS.CELSIUS.getStringValue());
            maxTemperature.setValue(translatedTaf.getCommonWeatherSection().getAirTemperatureMax().doubleValue());
            TimeInstantPropertyType timeInstantMaxTempProperty = iwxxmHelpers.createTimeInstantPropertyTypeForDateTime(
                    translatedTaf.getCommonWeatherSection().getAirTemperatureMaxTime(), translatedTaf.getIcaoCode(), "tx");

            // Time of the min temp forecasted
            temps.setMaximumAirTemperature(maxTemperature);
            temps.setMaximumAirTemperatureTime(timeInstantMaxTempProperty);
            isSet = true;
        }

        tempPropertyType.setAerodromeAirTemperatureForecast(temps);
        return isSet ? tempPropertyType : null;

    }

    /**
     * Creates weather section for given string code with link to WMO register url
     *
     * @throws WMORegisterException
     */
    private AerodromeForecastWeatherType createWeatherSection(String weatherCode) throws WMORegisterException {
        // <iwxxm:weather xlink:href="http://codes.wmo.int/306/4678/-SHRA"/>

        AerodromeForecastWeatherType forecastWeather = iwxxmHelpers.getOfIWXXM().createAerodromeForecastWeatherType();
        forecastWeather.setHref(iwxxmHelpers.getPrecipitationReg().getWMOUrlByCode(weatherCode));

        return forecastWeather;
    }

    /**
     * Cloud section
     *
     * @throws WMORegisterException
     */
    private AerodromeCloudForecastPropertyType createCloudSectionTag(TafCommonWeatherSection weatherSection,
            String icaoCode, int sectionIndex) throws WMORegisterException {
        // Envelop
        AerodromeCloudForecastPropertyType cloudsType = iwxxmHelpers.getOfIWXXM().createAerodromeCloudForecastPropertyType();

        // Body
        AerodromeCloudForecastType clouds = iwxxmHelpers.getOfIWXXM().createAerodromeCloudForecastType();
        clouds.setId(iwxxmHelpers.generateUUIDv4(String.format("acf-%d-%s", sectionIndex, icaoCode)));
        boolean layersCreated = false;
        boolean cloudsCreated = false;

        for (TAFCloudSection cloudSection : weatherSection.getCloudSections()) {

            CloudLayerPropertyType cloudLayer = iwxxmHelpers.getOfIWXXM().createCloudLayerPropertyType();

            //int cloudAmount = iwxxmHelpers.getCloudReg().getCloudAmountByStringCode(cloudSection.getAmount());
            if (cloudSection.getAmount() != null && cloudSection.getAmount().equalsIgnoreCase(WMOCloudRegister.verticalVisibilityCode)) {
                LengthWithNilReasonType vvType = iwxxmHelpers.getOfIWXXM().createLengthWithNilReasonType();
                vvType.setUom(LENGTH_UNITS.FT.getStringValue());
                if (cloudSection.getHeight() != null && cloudSection.getHeight().isPresent()) {
                    vvType.setValue((double) cloudSection.getHeight().get());
                } else {
                    vvType.getNilReason().add(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE));
                }
                clouds.setVerticalVisibility(iwxxmHelpers.getOfIWXXM().createAerodromeCloudTypeVerticalVisibility(vvType));
                cloudsCreated = true;

            } else if (cloudSection.isNoCloudsDetected()) {

                String nilReasonUrl = iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE);
                //cloudLayer.setCloudLayer(iwxxmHelpers.createEmptyCloudLayerSection(nilReasonUrl));
                cloudsType.getNilReason().add(nilReasonUrl);

            } else if (cloudSection.isNoSignificantClouds()) {
                String nilReasonUrl = iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOTHING_OF_OPERATIONAL_SIGNIFICANCE);

                //cloudLayer.setCloudLayer(iwxxmHelpers.createEmptyCloudLayerSection(nilReasonUrl));
                cloudsType.getNilReason().add(nilReasonUrl);

            } else {

                if (cloudSection.getHeight() != null && cloudSection.getHeight().isPresent()) {
                    cloudLayer.setCloudLayer(iwxxmHelpers.createCloudLayerSection(cloudSection.getAmount(), (double) cloudSection.getHeight().get(),
                            cloudSection.getType(), null, LENGTH_UNITS.FT));
                } else {
                    cloudLayer.setCloudLayer(iwxxmHelpers.createEmptyCloudLayerSection(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE)));
                }
                layersCreated = true;

            }
            if (layersCreated) {
                clouds.getLayer().add(cloudLayer);
            }

        }

        // Place body into envelop
        if (cloudsCreated || layersCreated) {
            cloudsType.setAerodromeCloudForecast(clouds);
        }

        return cloudsType;
    }

    /**
     * Create aerodrome description section as GML FeatureOfInterest ICAO code=UUWW
     */
    private AirportHeliportPropertyType createAirportDescriptionSectionTag() {

        return iwxxmHelpers.createAirportDescriptionSectionTag(translatedTaf.getIcaoCode());

    }

    /**
     * Adding headers to the root
     */
    @Override
    public TAFType addTranslationCentreHeader(TAFType taf) throws DatatypeConfigurationException {

        // taf = iwxxmHelpers.addTranslationCentreHeaders(taf, DateTime.now(), DateTime.now(),
        //        UUID.randomUUID().toString(), "UUWW", "Vnukovo, RU");
        //taf.setTranslationFailedTAC("");
        taf = iwxxmHelpers.addTranslationCentreHeaders(taf,
                DateTime.now(),
                DateTime.now(),
                UUID.randomUUID().toString(),
                App.getInstance().getString("CentreDesignator"),
                App.getInstance().getString("CentreName"));
        //report.setTranslationFailedTAC("");

        return taf;
    }

}


