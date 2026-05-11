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
 * WITHOUT WARRANTIES  OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vn.asg.converter.iwxxm;

import vn.asg.converter.tac.SPECITacMessage;
import vn.asg.converter.tac.METARTacMessage;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

import org.gamc.spmi.iwxxmConverter.exceptions.ParsingException;
import org.gamc.spmi.iwxxmConverter.wmo.WMORegister.WMORegisterException;
import org.joda.time.DateTime;

import _int.icao.iwxxm._2023_1.METARType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeObservationPropertyType;
import _int.icao.iwxxm._2023_1.MeteorologicalAerodromeTrendForecastPropertyType;
import _int.icao.iwxxm._2023_1.PermissibleUsageReasonType;
import _int.icao.iwxxm._2023_1.PermissibleUsageType;
import _int.icao.iwxxm._2023_1.ReportStatusType;
import _int.icao.iwxxm._2023_1.SPECIType;
import vn.asg.converter.config.App;
import net.opengis.gml.v_3_2_1.StringOrRefType;
import net.opengis.gml.v_3_2_1.TimeInstantPropertyType;
import org.gamc.spmi.iwxxmConverter.common.CoreUtil;
import org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException;
import org.gamc.spmi.iwxxmConverter.wmo.WMONilReasonRegister;

/**
 * Base class to perform conversion of TAC into intermediate object {@link METARTacMessage} and further IWXXM conversion and validation
 */
public class SPECIConverterV3 extends TacBaseConverter<SPECITacMessage, SPECIType> {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(SPECIConverterV3.class);
    private final _int.icao.iwxxm._2023_1.ObjectFactory ofIWXXM = new _int.icao.iwxxm._2023_1.ObjectFactory();
    private final IWXXM31Helpers iwxxmHelpers = new IWXXM31Helpers();

    private SPECITacMessage translatedSpeci;
    private String dateTime = "";
    private String dateTimePosition = "";

    @Override
    public String convertTacToXML(String tac) throws ConvertingFailException {
        try {
            logger.info(String.format("Converting to XML"));
            SPECITacMessage speciMessage = new SPECITacMessage(tac);
            SPECIType result;
            speciMessage.parseMessage();
            result = convertMessage(speciMessage);
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
            SPECITacMessage speciMessage = new SPECITacMessage(tac);
            SPECIType result;
            speciMessage.parseMessage();
            result = convertMessage(speciMessage);
            return marshallMessageToByte(result, zipped);
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    @Override
    public String getIdentifier() {
        return this.translatedSpeci.getIdentifier();
    }

    @Override
    protected JAXBElement<SPECIType> createJaxbElement(SPECIType r) {
        return iwxxmHelpers.getOfIWXXM().createSPECI(r);
    }

    @Override
    protected String postProcessXML(String xml) {
        // 0. Add namespaces if missing
        if (!xml.contains("xmlns:om=")) {
            xml = xml.replace("<iwxxm:SPECI", "<iwxxm:SPECI xmlns:om=\"http://www.opengis.net/om/2.0\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"");
        }

        // 1. Fix the record name
        xml = xml.replace("iwxxm:MeteorologicalAerodromeObservation ", "iwxxm:MeteorologicalAerodromeObservationRecord ");
        xml = xml.replace("iwxxm:MeteorologicalAerodromeObservation>", "iwxxm:MeteorologicalAerodromeObservationRecord>");
        xml = xml.replace("</iwxxm:MeteorologicalAerodromeObservation>", "</iwxxm:MeteorologicalAerodromeObservationRecord>");

        // 2. Wrap the record in om:OM_Observation
        // Similar to METAR
        String recordStartTag = "<iwxxm:MeteorologicalAerodromeObservationRecord";
        int startIdx = xml.indexOf(recordStartTag);
        if (startIdx == -1) return xml;
        
        int endIdx = xml.indexOf("</iwxxm:observation>");
        if (endIdx == -1) return xml;

        String recordContent = xml.substring(startIdx, xml.lastIndexOf("</iwxxm:MeteorologicalAerodromeObservationRecord>") + "</iwxxm:MeteorologicalAerodromeObservationRecord>".length());
        
        // Extract time for O&M header (look for observationTime)
        String timePos = "";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<gml:timePosition>([^<]+)</gml:timePosition>");
        java.util.regex.Matcher m = p.matcher(xml);
        if (m.find()) {
            timePos = m.group(1);
        }
        
        String icao = translatedSpeci.getIcaoCode();
        String obsId = "obs-speci-" + icao + "-" + timePos.replace(":", "").replace("-", "");
        
        StringBuilder omWrap = new StringBuilder();
        omWrap.append("<om:OM_Observation gml:id=\"").append(obsId).append("\">\n");
        omWrap.append("            <om:type xlink:href=\"http://codes.wmo.int/49-2/observation-type/IWXXM/3.0/MeteorologicalAerodromeObservation\"/>\n");
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
        
        return xml.substring(0, outerStart) + "\n        " + omWrap.toString() + "\n    " + xml.substring(outerEnd);
    }

    @Override
    public SPECIType convertMessage(SPECITacMessage translatedMessage)
            throws DatatypeConfigurationException, UnsupportedEncodingException, JAXBException, ParsingException, WMORegisterException {

        try {
            this.translatedSpeci = translatedMessage;

            // <iwxxm:METAR> root tag
            SPECIType speciRootTag = ofIWXXM.createSPECIType();

            // Using desciption to quote the origin message for debuggin
            /**
             * <gml:description xlink:type="simple"></gml:description>
             */
            if (App.getInstance().getBoolean("AddTacContent", false)) {
                StringOrRefType refTacString = iwxxmHelpers.getOfGML().createStringOrRefType();
                refTacString.setValue(translatedMessage.getInitialTacString());
                speciRootTag.setDescription(refTacString);
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
            speciRootTag.setId(iwxxmHelpers.generateUUIDv4(String.format("speci-%s-%s", translatedSpeci.getIcaoCode(), dateTime)));

            // metarRootTag.setAutomatedStation(true);
            // Set NON_OPERATIONAL and TEST properties.
            PermissibleUsageType permissibleUsageType = PermissibleUsageType.valueOf(App.getInstance().getString("PermissibleUsage"));
            speciRootTag.setPermissibleUsage(permissibleUsageType);
            if (permissibleUsageType == PermissibleUsageType.NON_OPERATIONAL) {
                // Non-Operational Reason
                speciRootTag.setPermissibleUsageReason(PermissibleUsageReasonType.valueOf(App.getInstance().getString("PermissibleUsageReason")));
                // Some description
                speciRootTag.setPermissibleUsageSupplementary(App.getInstance().getString("PermissibleUsageSupplementary"));
            }

            // speciRootTag.setPermissibleUsage(PermissibleUsageType.NON_OPERATIONAL);
            // speciRootTag.setPermissibleUsageReason(PermissibleUsageReasonType.TEST);
            // Some description
            // speciRootTag.setPermissibleUsageSupplementary("SPECI composing test using JAXB");
            //SPECI is always normal
            speciRootTag.setReportStatus(ReportStatusType.NORMAL);

            // Adding translate centre
            speciRootTag = addTranslationCentreHeader(speciRootTag);

            // Begining of the content
            // <iwxxm:aerodrome></iwxxm:aerodrome>
            speciRootTag.setAerodrome(iwxxmHelpers.createAirportDescriptionSectionTag(translatedSpeci.getIcaoCode()));

            // <iwxxm:issueTime />
            TimeInstantPropertyType issueTimeType = iwxxmHelpers.createTimeInstantPropertyTypeForDateTime(translatedSpeci.getMessageIssueDateTime(), translatedSpeci.getIcaoCode(), "issue");
            speciRootTag.setIssueTime(issueTimeType);

            // <iwxxm:observationTime />
            TimeInstantPropertyType obsTimeType = iwxxmHelpers.createTimeInstantPropertyTypeForDateTime(translatedSpeci.getMessageIssueDateTime(), translatedSpeci.getIcaoCode(), "observation");
            speciRootTag.setObservationTime(obsTimeType);

            if (translatedSpeci.isNIL()) {
                MeteorologicalAerodromeObservationPropertyType metarRecordTag = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeObservationPropertyType();
                metarRecordTag.getNilReason().add(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_MISSING));
                JAXBElement<MeteorologicalAerodromeObservationPropertyType> nilObservationTag = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeObservationReportTypeObservation(metarRecordTag);
                speciRootTag.setObservation(nilObservationTag);

                // create XML representation
                return speciRootTag;
            }

            String substitutionTac = translatedSpeci.getInitialTacString();
            substitutionTac = substitutionTac.replaceAll("SPECI", "METAR");

            //Mimic MetarConverter to use it's methods   
            METARConverterV3 c = new METARConverterV3();
            METARTacMessage metarMessage = new METARTacMessage(substitutionTac);
            metarMessage.parseMessage();
            METARType metarResult = c.convertMessage(metarMessage);

            //set prepared observation
            speciRootTag.setObservation(metarResult.getObservation());

            /*
                if (translatedSpeci.isNoSignificantChanges()) {
                    MeteorologicalAerodromeTrendForecastPropertyType metarTrendType = iwxxmHelpers.getOfIWXXM().createMeteorologicalAerodromeTrendForecastPropertyType();
                    metarTrendType.getNilReason().add(iwxxmHelpers.getNilRegister().getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NO_SIGNIGICANT_CHANGE));
                    speciRootTag.getTrendForecast().add(metarTrendType);
                }
             */
            //copy trends
            for (MeteorologicalAerodromeTrendForecastPropertyType trend : metarResult.getTrendForecast()) {
                speciRootTag.getTrendForecast().add(trend);
            }

            // create XML representation
            return speciRootTag;

        } catch (ParsingException ex) {
            throw new ParsingException(ex.getMessage() + " in [" + CoreUtil.truncateString(this.translatedSpeci.getInitialTacString(), 25) + "]", ex);
        }

    }

    @Override
    public SPECIType addTranslationCentreHeader(SPECIType report)
            throws DatatypeConfigurationException {

        report = iwxxmHelpers.addTranslationCentreHeaders(report,
                DateTime.now(),
                DateTime.now(),
                UUID.randomUUID().toString(),
                App.getInstance().getString("CentreDesignator"),
                App.getInstance().getString("CentreName"));
        // report = iwxxmHelpers.addTranslationCentreHeaders(report, DateTime.now(), DateTime.now(), UUID.randomUUID().toString(), "UUWW", "Vnukovo, RU");
        // report.setTranslationFailedTAC("");

        return report;
    }

}


