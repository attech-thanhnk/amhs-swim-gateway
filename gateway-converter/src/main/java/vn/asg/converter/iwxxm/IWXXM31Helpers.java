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

import _int.icao.iwxxm._2023_1.AbstractTimeObjectPropertyType;
import _int.icao.iwxxm._2023_1.AerodromeForecastWeatherType;
import _int.icao.iwxxm._2023_1.AerodromePresentWeatherType;
import _int.icao.iwxxm._2023_1.AerodromeRecentWeatherType;
import _int.icao.iwxxm._2023_1.AirportHeliportPropertyType;
import _int.icao.iwxxm._2023_1.CloudAmountReportedAtAerodromeType;
import _int.icao.iwxxm._2023_1.CloudLayerType;
import _int.icao.iwxxm._2023_1.DistanceWithNilReasonType;
import _int.icao.iwxxm._2023_1.LengthWithNilReasonType;
import _int.icao.iwxxm._2023_1.ReportType;
import _int.icao.iwxxm._2023_1.RunwayDirectionPropertyType;
import _int.icao.iwxxm._2023_1.SigConvectiveCloudTypeType;
import _int.icao.iwxxm._2023_1.StringWithNilReasonType;
import java.net.URISyntaxException;
import java.util.GregorianCalendar;
import java.util.Optional;

import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.gamc.gis.service.GeoService;
import org.gamc.spmi.iwxxmConverter.common.StringConstants;
import org.gamc.spmi.iwxxmConverter.general.IWXXMHelpers;
import org.gamc.spmi.iwxxmConverter.iwxxmenums.LENGTH_UNITS;
import org.gamc.spmi.iwxxmConverter.wmo.WMOAirWXRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMOCloudRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMOCloudTypeRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMONilReasonRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMOPrecipitationRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMORegister.WMORegisterException;
import org.gamc.spmi.iwxxmConverter.wmo.WMORunWayContaminationRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMORunWayDepositsRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMORunWayFrictionRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMOSigConvectiveCloudTypeRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMOSigWXRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMOSpaceWeatherLocationRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMOSpaceWeatherRegister;
import org.joda.time.DateTime;

import _int.wmo.def.collect._2014.ObjectFactory;
import aero.aixm.schema._5_1.AirportHeliportTimeSlicePropertyType;
import aero.aixm.schema._5_1.AirportHeliportTimeSliceType;
import aero.aixm.schema._5_1.AirportHeliportType;
import aero.aixm.schema._5_1.CodeAirportHeliportDesignatorType;
import aero.aixm.schema._5_1.CodeICAOType;
import aero.aixm.schema._5_1.CodeVerticalDatumType;
import aero.aixm.schema._5_1.ElevatedPointPropertyType;
import aero.aixm.schema._5_1.ElevatedPointType;
import aero.aixm.schema._5_1.RunwayDirectionTimeSlicePropertyType;
import aero.aixm.schema._5_1.RunwayDirectionTimeSliceType;
import aero.aixm.schema._5_1.RunwayDirectionType;
import aero.aixm.schema._5_1.TextDesignatorType;
import aero.aixm.schema._5_1.TextNameType;
import aero.aixm.schema._5_1.ValBearingType;
import aero.aixm.schema._5_1.ValDistanceVerticalType;
import vn.asg.converter.config.Airport;
import vn.asg.converter.config.Airports;
import vn.asg.converter.config.App;
import vn.asg.converter.config.ElevatedPoint;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import net.opengis.gml.v_3_2_1.DirectPositionType;
import net.opengis.gml.v_3_2_1.FeaturePropertyType;
import net.opengis.gml.v_3_2_1.TimeInstantPropertyType;
import net.opengis.gml.v_3_2_1.TimeInstantType;
import net.opengis.gml.v_3_2_1.TimePeriodPropertyType;
import net.opengis.gml.v_3_2_1.TimePeriodType;
import net.opengis.gml.v_3_2_1.TimePositionType;
import net.opengis.gml.v_3_2_1.TimePrimitivePropertyType;
import org.gamc.gis.model.GTCalculatedRegion;
import org.gamc.spmi.iwxxmConverter.common.CoordPoint;
import org.gamc.spmi.iwxxmConverter.common.Coordinate;
import org.gamc.spmi.iwxxmConverter.iwxxmenums.RUMB_UNITS;
import org.gamc.spmi.iwxxmConverter.wmo.WMORunWaySnowRegister;
import org.gamc.spmi.iwxxmConverter.wmo.WMOSeaSurfaceTypeRegister;

/**
 * Set of the helper functions. Provides creation of a common objects to use during xml creation. Helps to reduce boiler-plate code. Can be extended to provide specific implementation for METAR, TAF, SIGMET etc..
 */
public class IWXXM31Helpers extends IWXXMHelpers {

    // private Logger logger = LoggerFactory.getLogger(IWXXM31Helpers.class);
    protected org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(IWXXM31Helpers.class);

    private final _int.icao.iwxxm._2023_1.ObjectFactory ofIWXXM = new _int.icao.iwxxm._2023_1.ObjectFactory();
    private final net.opengis.gml.v_3_2_1.ObjectFactory ofGML = new net.opengis.gml.v_3_2_1.ObjectFactory();
    private final aero.aixm.schema._5_1.ObjectFactory ofAIXM = new aero.aixm.schema._5_1.ObjectFactory();
    private final _int.wmo.def.collect._2014.ObjectFactory ofMeteoBulletin = new ObjectFactory();

    private final _int.wmo.def.metce._2013.ObjectFactory ofMETCE = new _int.wmo.def.metce._2013.ObjectFactory();

    /* WMO registers **/
    private final WMONilReasonRegister nilRegister = new WMONilReasonRegister();

    private final WMOCloudRegister cloudReg = new WMOCloudRegister();
    private final WMOCloudTypeRegister cloudTypeReg = new WMOCloudTypeRegister();

    private final WMOSigConvectiveCloudTypeRegister sigCloudTypeReg = new WMOSigConvectiveCloudTypeRegister();

    private final WMOPrecipitationRegister precipitationReg = new WMOPrecipitationRegister();
    private final WMORunWaySnowRegister rwSnowRegister = new WMORunWaySnowRegister();

    private final WMORunWayContaminationRegister rwContaminationReg = new WMORunWayContaminationRegister();
    private final WMORunWayDepositsRegister rwDepositReg = new WMORunWayDepositsRegister();
    private final WMORunWayFrictionRegister rwFrictionReg = new WMORunWayFrictionRegister();

    private final WMOSigWXRegister sigWxPhenomenaRegister = new WMOSigWXRegister();
    private final WMOAirWXRegister airWxPhenomenaRegister = new WMOAirWXRegister();
    private final WMOSeaSurfaceTypeRegister seaSurfaceTypeRegister = new WMOSeaSurfaceTypeRegister();

    private final WMOSpaceWeatherRegister swxEffectsRegister = new WMOSpaceWeatherRegister();
    private final WMOSpaceWeatherLocationRegister swxLocationRegister = new WMOSpaceWeatherLocationRegister();

    private GeoService geoService = null;

    // private AppConfig appConfig = AppConfig.load();

    public static final String NSW = "NSW";

    public IWXXM31Helpers() {
    }

//    public AppConfig getAppConfig() {
//        return this.appConfig;
//    }

    public _int.icao.iwxxm._2023_1.ObjectFactory getOfIWXXM() {
        return ofIWXXM;
    }

    public net.opengis.gml.v_3_2_1.ObjectFactory getOfGML() {
        return ofGML;
    }

    public aero.aixm.schema._5_1.ObjectFactory getOfAIXM() {
        return ofAIXM;
    }

    /**
     * Creates TimeInstantPropertyType from given DateTime
     *
     * @param dt - dateTime to process
     * @param icaoCode - aerodrome ICAO code
     * @param suffix
     * @return {@link TimeInstantPropertyType}
     */
    public TimeInstantPropertyType createTimeInstantPropertyTypeForDateTime(DateTime dt, String icaoCode, String suffix) {

        String sDateTime = dt.toString(getDateTimeFormat()) + "Z";
        String sDateTimePosition = dt.toString(getDateTimeISOFormat());

        TimeInstantPropertyType timeInstantProperty = ofGML.createTimeInstantPropertyType();
        TimeInstantType timeInstant = ofGML.createTimeInstantType();
        timeInstant.setId(generateUUIDv4(String.format("ti-%s-%s-%s", icaoCode, sDateTime, suffix)));
        TimePositionType timePosition = ofGML.createTimePositionType();
        timePosition.getValue().add(sDateTimePosition);
        timeInstant.setTimePosition(timePosition);
        timeInstantProperty.setTimeInstant(timeInstant);

        return timeInstantProperty;
    }

    public StringWithNilReasonType createStringWithNilReasonForString(String value, String nilReason) {
        StringWithNilReasonType snrType = ofIWXXM.createStringWithNilReasonType();
        if (value == null) {
            snrType.getNilReason().add(nilReason);
        } else {
            snrType.setValue(value);
        }

        return snrType;
    }

    public JAXBElement<StringWithNilReasonType> createTagForStringWithNilReasonForString(String value, String nilReason) {
        StringWithNilReasonType snrType = createStringWithNilReasonForString(value, nilReason);
        JAXBElement<StringWithNilReasonType> result = ofIWXXM.createStringWithNilReason(snrType);
        return result;

    }

    /**
     * Сreates JAXB TimeInstantSection for a given DateTime
     *
     * @param dt - dateTime to process
     * @param icaoCode - aerodrome ICAO code
     * @return {@link TimeInstantpropertyType} in JAXB envelope which is ready to embed into getRest() part of the root tag
     */
    public TimeInstantPropertyType createJAXBTimeSection(DateTime dt, String icaoCode) {
        TimeInstantPropertyType timeProperty = createTimeInstantPropertyTypeForDateTime(dt, icaoCode, "timeproperty");
        return timeProperty;

    }

    public AbstractTimeObjectPropertyType createAbstractTimeObject(DateTime dt, String icaoCode) {
        TimeInstantPropertyType timeProperty = this.createTimeInstantPropertyTypeForDateTime(dt, icaoCode, "adt");
        JAXBElement<TimeInstantType> to = this.ofGML.createTimeInstant(timeProperty.getTimeInstant());
        AbstractTimeObjectPropertyType t = this.ofIWXXM.createAbstractTimeObjectPropertyType();
        t.setAbstractTimeObject(to);
        return t;
    }

    /**
     * Creates valid period section for trend sections
     *
     * @param icaoCode
     * @param start - Begin timestamp
     * @param end - End timestamp
     * @param sectionIndex - number of the section to create valid id
     *
     * @return TimePeriodPropertyType
     */
    public TimePeriodPropertyType createTrendPeriodSection(String icaoCode, DateTime start, DateTime end, int sectionIndex) {

        String sectionTimePeriodBeginPosition = start.toString(getDateTimeISOFormat());
        String sectionTimePeriodEndPosition = end.toString(getDateTimeISOFormat());

        String sectionTimePeriodBegin = start.toString(getDateTimeFormat()) + "Z";
        String sectionTimePeriodEnd = end.toString(getDateTimeFormat()) + "Z";

        TimePeriodPropertyType timePeriodProperty = ofGML.createTimePeriodPropertyType();
        TimePeriodType timePeriodType = ofGML.createTimePeriodType();

        timePeriodType.setId(generateUUIDv4(
                String.format("tp-%d-%s-%s", sectionIndex, sectionTimePeriodBegin, sectionTimePeriodEnd)));

        // begin
        TimeInstantType timeBeginInstant = ofGML.createTimeInstantType();
        timeBeginInstant
                .setId(generateUUIDv4(String.format("ti-%d-%s-%s", sectionIndex, icaoCode, sectionTimePeriodBegin)));

        TimePositionType timePositionBegin = ofGML.createTimePositionType();
        // timePositionBegin.getValue().add(timePeriodBeginPosition);
        timePositionBegin.getValue().add(sectionTimePeriodBeginPosition);
        timeBeginInstant.setTimePosition(timePositionBegin);

        TimeInstantPropertyType timeBeginProperty = ofGML.createTimeInstantPropertyType();
        timeBeginProperty.setTimeInstant(timeBeginInstant);

        // end
        TimeInstantType timeEndInstant = ofGML.createTimeInstantType();
        timeEndInstant.setId(generateUUIDv4(String.format("ti-%s-%s", icaoCode, sectionTimePeriodEnd)));
        TimePositionType timePositionEnd = ofGML.createTimePositionType();
        timePositionEnd.getValue().add(sectionTimePeriodEndPosition);
        timeEndInstant.setTimePosition(timePositionEnd);

        TimeInstantPropertyType timeEndProperty = ofGML.createTimeInstantPropertyType();
        timeEndProperty.setTimeInstant(timeEndInstant);

        timePeriodType.setBeginPosition(timePositionBegin);
        timePeriodType.setEndPosition(timePositionEnd);

        timePeriodProperty.setTimePeriod(timePeriodType);

        return timePeriodProperty;

    }

    /**
     * creates XML section with time period, e.g.for validity periods
     * @param icaoCode
     * @param from
     * @param to
     * @return 
     */
    public TimePeriodPropertyType createTimePeriod(String icaoCode, DateTime from, DateTime to) {

        TimePeriodPropertyType timePeriodProperty = this.ofGML.createTimePeriodPropertyType();
        TimePeriodType timePeriodType = this.ofGML.createTimePeriodType();

        timePeriodType.setId(generateUUIDv4(String.format("tp-%s-%s-%s", icaoCode, from.toString(), to.toString())));

        // begin
        TimeInstantType timeBeginInstant = this.ofGML.createTimeInstantType();
        timeBeginInstant.setId(generateUUIDv4(String.format("ti-%s-%s", icaoCode, from.toString())));
        TimePositionType timePositionBegin = this.ofGML.createTimePositionType();
        timePositionBegin.getValue().add(from.toString());
        timeBeginInstant.setTimePosition(timePositionBegin);

        TimeInstantPropertyType timeBeginProperty = this.ofGML.createTimeInstantPropertyType();
        timeBeginProperty.setTimeInstant(timeBeginInstant);

        timePeriodType.setBeginPosition(timePositionBegin);

        // end
        TimeInstantType timeEndInstant = this.ofGML.createTimeInstantType();
        timeEndInstant.setId(generateUUIDv4(String.format("ti-%s-%s", icaoCode, to.toString())));
        TimePositionType timePositionEnd = this.ofGML.createTimePositionType();
        timePositionEnd.getValue().add(to.toString());
        timeEndInstant.setTimePosition(timePositionEnd);

        TimeInstantPropertyType timeEndProperty = this.ofGML.createTimeInstantPropertyType();
        timeEndProperty.setTimeInstant(timeEndInstant);

        timePeriodType.setEndPosition(timePositionEnd);

        timePeriodProperty.setTimePeriod(timePeriodType);
        return timePeriodProperty;

    }
    
    public TimePeriodPropertyType createRefTimePeriod(String refId) {

        TimePeriodPropertyType timePeriodProperty = this.ofGML.createTimePeriodPropertyType();
        timePeriodProperty.setHref(refId);
        return timePeriodProperty;

    }

    /**
     * Сreates FeaturePropertyType for given aerodrome icao code <iwxxm:aerodrome>
     *
     * <group name="AirportHeliportPropertyGroup">
     * <sequence>
     * <element name="designator" type="aixm:CodeAirportHeliportDesignatorType" nillable="true" minOccurs="0"/>
     * <element name="name" type="aixm:TextNameType" nillable="true" minOccurs="0"/>
     * <element name="locationIndicatorICAO" type="aixm:CodeICAOType" nillable="true" minOccurs="0"/>
     * <element name="designatorIATA" type="aixm:CodeIATAType" nillable="true" minOccurs="0"/>
     * <element name="fieldElevation" type="aixm:ValDistanceVerticalType" nillable="true" minOccurs="0"/>
     * <element name="ARP" type="aixm:ElevatedPointPropertyType" nillable="true" minOccurs="0" maxOccurs="1"/>
     * </sequence>
     * </group>
     *
     * @param icaoCode - ICAO code for the aerodrome.
     * @return {@link FeaturePropertyType} with aerodrome description
     */
    public AirportHeliportPropertyType createAirportDescriptionSectionTag(String icaoCode) {

        /**
         * <iwxxm:aerodrome>
         * <aixm:AirportHeliport gml:id="uuid.143d63d9-15f5-442e-9bdc-1f3db93fb619">
         * <aixm:timeSlice>
         * <aixm:AirportHeliportTimeSlice gml:id="uuid.75c3340c-3679-4e31-8aec-efdabe375d49">
         * <gml:validTime/>
         * <aixm:interpretation>SNAPSHOT</aixm:interpretation>
         * <aixm:designator>YUDO</aixm:designator>
         * <aixm:name>DONLON/INTERNATIONAL</aixm:name>
         * <aixm:locationIndicatorICAO>YUDO</aixm:locationIndicatorICAO>
         * <aixm:ARP>
         * <aixm:ElevatedPoint gml:id="uuid.dd2c810b-edaa-4ad9-bb65-9ab774d1522e" srsDimension="2" axisLabels="Lat Long" srsName="http://www.opengis.net/def/crs/EPSG/0/4326">
         * <gml:pos>12.34 -12.34</gml:pos>
         * <aixm:elevation uom="M">12</aixm:elevation>
         * <aixm:verticalDatum>EGM_96</aixm:verticalDatum>
         * </aixm:ElevatedPoint>
         * </aixm:ARP>
         * </aixm:AirportHeliportTimeSlice>
         * </aixm:timeSlice>
         * </aixm:AirportHeliport>
         * </iwxxm:aerodrome>
         */
        // TODO: Need adding aerodrome detail
        // <iwxxm:aerodrome></iwxxm:aerodrome>
        AirportHeliportPropertyType ahpt = ofIWXXM.createAirportHeliportPropertyType();

        // <aixm:AirportHeliport></aixm:AirportHeliport>
        AirportHeliportType aht = ofAIXM.createAirportHeliportType();

        // <aixm:AirportHeliport gml:id="aerodrome-VVNB"/>
        aht.setId("aerodrome-" + icaoCode);

        // <aixm:timeSlice>
        //       <aixm:AirportHeliportTimeSlice >
        //       </aixm:AirportHeliportTimeSlice>
        // </aixm:timeSlice>
        AirportHeliportTimeSlicePropertyType ahTimeSliceProperty = ofAIXM.createAirportHeliportTimeSlicePropertyType();
        AirportHeliportTimeSliceType ahTimeSliceType = ofAIXM.createAirportHeliportTimeSliceType();
        ahTimeSliceType.setId("aerodrome-" + icaoCode + "-ts");

        //  <gml:validTime/>
        TimePrimitivePropertyType validTime = ofGML.createTimePrimitivePropertyType();
        ahTimeSliceType.setValidTime(validTime);

        //  <aixm:interpretation>SNAPSHOT</aixm:interpretation>
//        ahTimeSliceType.setInterpretation("BASELINE");
        ahTimeSliceType.setInterpretation(App.getInstance().getString("Interpretation"));

        //  <aixm:designator>YUDO</aixm:designator>
        CodeAirportHeliportDesignatorType designator = ofAIXM.createCodeAirportHeliportDesignatorType();
        designator.setValue(icaoCode);
        JAXBElement<CodeAirportHeliportDesignatorType> designatorTag = ofAIXM.createAirportHeliportTimeSliceTypeDesignator(designator);
        ahTimeSliceType.getRest().add(designatorTag);

        // <aixm:locationIndicatorICAO>YUDO</aixm:locationIndicatorICAO>
        CodeICAOType icaoType = ofAIXM.createCodeICAOType();
        icaoType.setValue(icaoCode);
        JAXBElement<CodeICAOType> locationIdicator = ofAIXM.createAirportHeliportTimeSliceTypeLocationIndicatorICAO(icaoType);
        ahTimeSliceType.getRest().add(locationIdicator);

        /*
        // <aixm:name>DONLON/INTERNATIONAL</aixm:name>
        TextNameType textNameType = ofAIXM.createTextNameType();
        textNameType.setValue("HANOI/INTERNATION");
        JAXBElement<TextNameType> name = ofAIXM.createAirportHeliportTimeSliceTypeName(textNameType);
        ahTimeSliceType.getRest().add(name);
         */
        Airport airportDefine = App.getInstance().getAirportDefine(icaoCode);
        if (airportDefine != null) {

            // <aixm:name>DONLON/INTERNATIONAL</aixm:name>
            TextNameType textNameType = ofAIXM.createTextNameType();
            textNameType.setValue(airportDefine.getName());
            JAXBElement<TextNameType> name = ofAIXM.createAirportHeliportTimeSliceTypeName(textNameType);
            ahTimeSliceType.getRest().add(name);

            // <aixm:ARP></aixm:ARP>
            // <aixm:ElevatedPoint gml:id="uuid.dd2c810b-edaa-4ad9-bb65-9ab774d1522e" srsDimension="2" axisLabels="Lat Long" srsName="http://www.opengis.net/def/crs/EPSG/0/4326">
            ElevatedPoint elevatedPoint = airportDefine.getElevatedPoint();
            if (elevatedPoint != null) {

                ElevatedPointType elevatedPointType = ofAIXM.createElevatedPointType();

                elevatedPointType.setSrsName(elevatedPoint.getSrsName() != null ? elevatedPoint.getSrsName() : "http://www.opengis.net/def/crs/EPSG/0/4326");
                if (elevatedPoint.getAxisLabel() != null && !elevatedPoint.getAxisLabel().isEmpty()) {
                    elevatedPointType.getAxisLabels().addAll(elevatedPoint.getAxisLabel());
                } else {
                    elevatedPointType.getAxisLabels().add("Lat");
                    elevatedPointType.getAxisLabels().add("Long");
                }
                elevatedPointType.setSrsDimension(elevatedPoint.getSrsDimension() != null ? elevatedPoint.getSrsDimension() : java.math.BigInteger.valueOf(2));
                elevatedPointType.setId("aerodrome-" + icaoCode + "-ep");

                // <gml:pos>12.34 -12.34</gml:pos>
                DirectPositionType directPositionType = ofGML.createDirectPositionType();
                directPositionType.getValue().addAll(elevatedPoint.getPosition());
                elevatedPointType.setPos(directPositionType);

                // <aixm:elevation uom="M">12</aixm:elevation>
                ValDistanceVerticalType valDistanceVerticalType = ofAIXM.createValDistanceVerticalType();
                valDistanceVerticalType.setUom(elevatedPoint.getElevationUOM() != null ? elevatedPoint.getElevationUOM() : "M");
                if (elevatedPoint.getElevationValue() != null) {
                    valDistanceVerticalType.setValue(elevatedPoint.getElevationValue());
                } else {
                    valDistanceVerticalType.setValue("12");
                }
                JAXBElement<ValDistanceVerticalType> elevattion = ofAIXM.createElevatedPointTypeElevation(valDistanceVerticalType);
                elevatedPointType.setElevation(elevattion);

                // VerticalDatum
                // <aixm:verticalDatum>EGM_96</aixm:verticalDatum>
                CodeVerticalDatumType codeVerticalDatumType = ofAIXM.createCodeVerticalDatumType();
                if (elevatedPoint.getVerticalDatum() != null && !elevatedPoint.getVerticalDatum().isEmpty()) {
                    codeVerticalDatumType.setValue(elevatedPoint.getVerticalDatum());
                } else {
                    codeVerticalDatumType.setValue("MSL");
                }
                JAXBElement<CodeVerticalDatumType> verticalDatum = ofAIXM.createElevatedPointTypeVerticalDatum(codeVerticalDatumType);
                elevatedPointType.setVerticalDatum(verticalDatum);

                ElevatedPointPropertyType elevatedPointPropertyType = ofAIXM.createElevatedPointPropertyType();
                elevatedPointPropertyType.setElevatedPoint(elevatedPointType);
                JAXBElement<ElevatedPointPropertyType> arp = ofAIXM.createAirportHeliportTimeSliceTypeARP(elevatedPointPropertyType);
                ahTimeSliceType.getRest().add(arp);
            }
        }

        ahTimeSliceProperty.setAirportHeliportTimeSlice(ahTimeSliceType);
        aht.getTimeSlice().add(ahTimeSliceProperty);
        ahpt.setAirportHeliport(aht);

        return ahpt;

    }

    /**
     * Creates block for RunwayDirectionPropertyType with AIXXM description of the aerodrome runway
     */
    public RunwayDirectionPropertyType createRunwayDesignatorSectionTag(String icaoCode, String designator) {
        RunwayDirectionPropertyType runwayDir = ofIWXXM.createRunwayDirectionPropertyType();
        RunwayDirectionType rdt = ofAIXM.createRunwayDirectionType();
        rdt.setId(generateUUIDv4(String.format("runway-%s-%s", icaoCode, designator)));

        RunwayDirectionTimeSlicePropertyType rdts = ofAIXM.createRunwayDirectionTimeSlicePropertyType();
        RunwayDirectionTimeSliceType rdtst = ofAIXM.createRunwayDirectionTimeSliceType();

        TextDesignatorType textDesignator = ofAIXM.createTextDesignatorType();
        textDesignator.setValue(designator);
        JAXBElement<TextDesignatorType> textDesTag = ofAIXM.createRunwayTimeSliceTypeDesignator(textDesignator);
        rdtst.setId(generateUUIDv4(String.format("runway-%s-%s-ts", icaoCode, designator)));
        rdtst.setDesignator(textDesTag);
        // rdtst.setInterpretation("BASELINE");
        rdtst.setInterpretation(App.getInstance().getString("Interpretation"));

        /*
        ValBearingType trueBrearingType = ofAIXM.createValBearingType();
        trueBrearingType.setValue(new BigDecimal(rd));
        JAXBElement<ValBearingType> trueBearing = ofAIXM.createLocalizerTimeSliceTypeTrueBearing(new ValBearingType());
        rdtst.setT
         */
        TimePrimitivePropertyType tppt = ofGML.createTimePrimitivePropertyType();
        rdtst.setValidTime(tppt);

        rdts.setRunwayDirectionTimeSlice(rdtst);
        rdt.getTimeSlice().add(rdts);

        runwayDir.setRunwayDirection(rdt);
        return runwayDir;
    }

    public RunwayDirectionPropertyType createRunwayDesignatorSectionTag(String icaoCode, String designator, Integer bearing) {
        RunwayDirectionPropertyType runwayDir = ofIWXXM.createRunwayDirectionPropertyType();
        RunwayDirectionType rdt = ofAIXM.createRunwayDirectionType();
        rdt.setId(generateUUIDv4(String.format("runway-%s-%s", icaoCode, designator)));

        RunwayDirectionTimeSlicePropertyType rdts = ofAIXM.createRunwayDirectionTimeSlicePropertyType();
        RunwayDirectionTimeSliceType rdtst = ofAIXM.createRunwayDirectionTimeSliceType();

        TextDesignatorType textDesignator = ofAIXM.createTextDesignatorType();
        textDesignator.setValue(designator);
        JAXBElement<TextDesignatorType> textDesTag = ofAIXM.createRunwayTimeSliceTypeDesignator(textDesignator);
        rdtst.setId(generateUUIDv4(String.format("runway-%s-%s-ts", icaoCode, designator)));
        rdtst.setDesignator(textDesTag);
        // rdtst.setInterpretation("BASELINE");
        rdtst.setInterpretation(App.getInstance().getString("Interpretation"));

        if (bearing != null) {
            ValBearingType bearingType = ofAIXM.createValBearingType();
            bearingType.setValue(new BigDecimal(bearing.intValue()));
            JAXBElement<ValBearingType> trueBearing = ofAIXM.createLocalizerTimeSliceTypeTrueBearing(bearingType);
            rdtst.setTrueBearing(trueBearing);
        }

        TimePrimitivePropertyType tppt = ofGML.createTimePrimitivePropertyType();
        rdtst.setValidTime(tppt);

        rdts.setRunwayDirectionTimeSlice(rdtst);
        rdt.getTimeSlice().add(rdts);

        runwayDir.setRunwayDirection(rdt);
        return runwayDir;
    }

    /**
     * Adds header with translation center properties to the message
     *
     * @param report - message of the class, derived from {@link ReportType} - TAFType, METARType, etc...
     * @param translationTime - translation time
     * @param bulletinReceivedTime - when bulletin was received (or null if not applicable))
     * @param bulletinId - bulletin id (or null if not applicable)
     * @param designator - ICAO code of the translation center
     * @param centreName - name of the translation center
     * @return The same report object with filled properties
     */
    public <T extends ReportType> T addTranslationCentreHeaders(T report,
            DateTime translationTime,
            DateTime bulletinReceivedTime,
            String bulletinId,
            String designator,
            String centreName)
            throws DatatypeConfigurationException {

        // Create and set special XML DateTime object
        GregorianCalendar calDateTime = translationTime.toGregorianCalendar();
        XMLGregorianCalendar xmlCalRepr = DatatypeFactory.newInstance().newXMLGregorianCalendar(calDateTime);
        report.setTranslationTime(xmlCalRepr);

        if (bulletinReceivedTime != null) {
            GregorianCalendar bulletinDateTime = bulletinReceivedTime.toGregorianCalendar();
            XMLGregorianCalendar bulletinCalRepr = DatatypeFactory.newInstance()
                    .newXMLGregorianCalendar(bulletinDateTime);
            report.setTranslatedBulletinReceptionTime(bulletinCalRepr);
        }

        report.setTranslatedBulletinID(bulletinId);
        report.setTranslationCentreName(centreName);
        report.setTranslationCentreDesignator(designator);
        return report;
    }

    /**
     * Creates cloud layer section.Takes the right linkk from WMO register helper
     *
     * @param cloudAmount - octants
     * @param cloudHeight - height of cloud in given units
     * @param cloudTypeCode - significant cloud type if exists
     * @param nilReasonUrl
     *
     * @param nilReason - it should be set when somehow values are missed
     * @param units - {@link LENGTH_UNITS} unit of measure.
     * @return cloudLayer
     * @throws WMORegisterException
     */
    public CloudLayerType createCloudLayerSection(String cloudAmount, double cloudHeight, String cloudTypeCode, String nilReasonUrl, LENGTH_UNITS units)
            throws WMORegisterException {

        // Create layer
        // Layer cloudLayer = ofIWXXM.createAerodromeCloudForecastTypeLayer();
        CloudLayerType currentLayer = ofIWXXM.createCloudLayerType();

        // Cloud amount seems to conform WMO schemas with
        CloudAmountReportedAtAerodromeType amount = ofIWXXM.createCloudAmountReportedAtAerodromeType();

        // Get the right link to WMO code table for cloud amount octant
        amount.setHref(cloudReg.getWMOUrlByCode(cloudAmount));

        // Height of clouds
        DistanceWithNilReasonType layerDistanceBase = ofIWXXM.createDistanceWithNilReasonType();
        if (nilReasonUrl != null) {
            layerDistanceBase.getNilReason().add(nilReasonUrl);
        } else {
            layerDistanceBase.setUom(units.getStringValue().replace("[ft_i]", "ft"));
            layerDistanceBase.setValue(cloudHeight);
        }

        currentLayer.setAmount(amount);
        currentLayer.setBase(layerDistanceBase);

        if (cloudTypeCode != null && !cloudTypeCode.isEmpty()) {

            SigConvectiveCloudTypeType cloudType = ofIWXXM.createSigConvectiveCloudTypeType();

            // Get the right link to WMO code table for cloud type
            cloudType.setHref(sigCloudTypeReg.getWMOUrlByCode(sigCloudTypeReg.getCloudTypeByStringCode(cloudTypeCode)));
            currentLayer.setCloudType(ofIWXXM.createCloudLayerTypeCloudType(cloudType));
        }

        // cloudLayer.setCloudLayer(currentLayer);
        return currentLayer;

    }

    public CloudLayerType createCloudLayerSection(String cloudAmount, Optional<Integer> cloudHeight, String cloudTypeCode, LENGTH_UNITS units)
            throws WMORegisterException {

        // Create layer
        // Layer cloudLayer = ofIWXXM.createAerodromeCloudForecastTypeLayer();
        CloudLayerType currentLayer = ofIWXXM.createCloudLayerType();

        /*
        if ((cloudTypeCode == null || cloudTypeCode.isEmpty() || cloudTypeCode.equalsIgnoreCase("///")) && 
                (cloudHeight == null || !cloudHeight.isPresent()) && 
                (cloudTypeCode == null || cloudTypeCode.isEmpty() || cloudTypeCode.equalsIgnoreCase("///"))) {
            
            DistanceWithNilReasonType distanceWithNilReason = ofIWXXM.createDistanceWithNilReasonType();
            distanceWithNilReason.getNilReason().add(nilRegister.getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE));
            currentLayer.setBase(distanceWithNilReason);
//            currentLayer.getBase().getNilReason().add(nilRegister.getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE));
            return currentLayer;
        }
         */
        // Cloud amount seems to conform WMO schemas with
        CloudAmountReportedAtAerodromeType amount = ofIWXXM.createCloudAmountReportedAtAerodromeType();

        // Get the right link to WMO code table for cloud amount octant
        if (cloudAmount == null || cloudAmount.equalsIgnoreCase("///")) {
            amount.getNilReason().add(nilRegister.getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE));
        } else {

            if (cloudAmount.equalsIgnoreCase("CLR")) {
                amount.setHref(cloudReg.getWMOUrlByCode("SKC"));
            } else {
                amount.setHref(cloudReg.getWMOUrlByCode(cloudAmount));
            }

        }

        // Height of clouds
        DistanceWithNilReasonType layerDistanceBase = ofIWXXM.createDistanceWithNilReasonType();
        if (cloudAmount.equalsIgnoreCase("CLR") || cloudAmount.equalsIgnoreCase("SKC")) {
            layerDistanceBase.getNilReason().add(nilRegister.getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_INAPPLICABLE));
        } else if (cloudHeight == null || !cloudHeight.isPresent()) {
            layerDistanceBase.getNilReason().add(nilRegister.getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE));
        } else {
            layerDistanceBase.setUom(units.getStringValue());
            layerDistanceBase.setValue(cloudHeight.get());
        }

        currentLayer.setAmount(amount);
        currentLayer.setBase(layerDistanceBase);
        if (cloudTypeCode != null && !cloudTypeCode.isEmpty()) {

            SigConvectiveCloudTypeType cloudType = ofIWXXM.createSigConvectiveCloudTypeType();
            // Get the right link to WMO code table for cloud type
            if (cloudTypeCode.equalsIgnoreCase("///")) {
                cloudType.getNilReason().add(nilRegister.getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE));
            } else {
                cloudType.setHref(sigCloudTypeReg.getWMOUrlByCode(sigCloudTypeReg.getCloudTypeByStringCode(cloudTypeCode)));
            }
            currentLayer.setCloudType(ofIWXXM.createCloudLayerTypeCloudType(cloudType));
        }

        return currentLayer;

    }

    /**
     * Created cloud section with pointing the nil reason, e.g. for NSC or NCD cases
     */
    public CloudLayerType createEmptyCloudLayerSection(String nilReasonUrl) {

        // Create layer
        CloudLayerType currentLayer = ofIWXXM.createCloudLayerType();
        DistanceWithNilReasonType layerDistanceBase = ofIWXXM.createDistanceWithNilReasonType();

        layerDistanceBase.getNilReason().add(nilReasonUrl);

        currentLayer.setBase(layerDistanceBase);

        return currentLayer;

    }

    /**
     * Creates tag for vertical visibility
     *
     * @throws WMORegisterException
     */
    public JAXBElement<LengthWithNilReasonType> createVerticalVisibilitySection(Optional<Integer> visibilityValue) throws WMORegisterException {
        LengthWithNilReasonType vvType = ofIWXXM.createLengthWithNilReasonType();

        //Vertical visibility is present, for example VV015
        if (visibilityValue.isPresent()) {
            vvType.setUom(LENGTH_UNITS.FT.getStringValue());
            vvType.setValue(visibilityValue.get());
        } else {
            vvType.setUom(LENGTH_UNITS.FT.getStringValue());
            vvType.getNilReason().add(nilRegister.getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOT_OBSERVABLE));
        }

        return ofIWXXM.createAerodromeCloudTypeVerticalVisibility(vvType);

    }

    /**
     * returns link for WMO weather register for present weather in METAR
     *
     * @throws WMORegisterException
     */
    public AerodromePresentWeatherType createPresentWeatherSection(String weather) throws WMORegisterException {

        AerodromePresentWeatherType presentWeather = ofIWXXM.createAerodromePresentWeatherType();
        if (weather.equalsIgnoreCase(StringConstants.NO_SIGNIFICANT_WEATHER_CHANGES)) {
            presentWeather.getNilReason().add("http://codes.wmo.int/common/nil/noSignificantWeather");
            return presentWeather;
        }
        presentWeather.setHref(getPrecipitationReg().getWMOUrlByCode(weather));

        return presentWeather;
    }

    /**
     * returns link for WMO weather register for recent weather in METAR
     *
     * @throws WMORegisterException
     */
    public AerodromeRecentWeatherType createRecentWeatherSection(String weather) throws WMORegisterException {

        AerodromeRecentWeatherType recentWeather = ofIWXXM.createAerodromeRecentWeatherType();

        if (weather.equalsIgnoreCase(StringConstants.NO_SIGNIFICANT_WEATHER_CHANGES)) {
            recentWeather.getNilReason().add(nilRegister.getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOTHING_OF_OPERATIONAL_SIGNIFICANCE));
            return recentWeather;
        }
        recentWeather.setHref(getPrecipitationReg().getWMOUrlByCode(weather));

        return recentWeather;
    }

    /**
     * returns link for WMO weather register for forecasted weather in METAR and TAF
     *
     * @throws WMORegisterException
     */
    public AerodromeForecastWeatherType createForecastWeatherSection(String weather) throws WMORegisterException {
        AerodromeForecastWeatherType fcstWeather = ofIWXXM.createAerodromeForecastWeatherType();

        if (weather.equalsIgnoreCase(StringConstants.NO_SIGNIFICANT_WEATHER_CHANGES)) {
            fcstWeather.getNilReason().add(nilRegister.getWMOUrlByCode(WMONilReasonRegister.NIL_REASON_NOTHING_OF_OPERATIONAL_SIGNIFICANCE));
            return fcstWeather;
        }

        fcstWeather.setHref(getPrecipitationReg().getWMOUrlByCode(weather));
        return fcstWeather;

    }

    public WMOCloudRegister getCloudReg() {
        return cloudReg;
    }

    public WMOCloudTypeRegister getCloudTypeReg() {
        return cloudTypeReg;
    }

    public WMOPrecipitationRegister getPrecipitationReg() {
        return precipitationReg;
    }

    public WMORunWayContaminationRegister getRwContaminationReg() {
        return rwContaminationReg;
    }

    public WMORunWayDepositsRegister getRwDepositReg() {
        return rwDepositReg;
    }

    public WMORunWayFrictionRegister getRwFrictionReg() {
        return rwFrictionReg;
    }

    public WMOSigConvectiveCloudTypeRegister getSigCloudTypeReg() {
        return sigCloudTypeReg;
    }

    public WMOSigWXRegister getSigWxPhenomenaRegister() {
        return sigWxPhenomenaRegister;
    }

    public WMOSpaceWeatherRegister getSpaceWeatherReg() {
        return swxEffectsRegister;
    }

    public WMOSpaceWeatherLocationRegister getSpaceWeatherLocationReg() {
        return swxLocationRegister;
    }

    public WMONilReasonRegister getNilRegister() {
        return nilRegister;
    }

    public WMOAirWXRegister getAirWxPhenomenaRegister() {
        return airWxPhenomenaRegister;
    }
    
    public WMOSeaSurfaceTypeRegister getSeaSurfaceTypeRegister() {
        return seaSurfaceTypeRegister;
    }

    public WMOSpaceWeatherRegister getSwxEffectsRegister() {
        return swxEffectsRegister;
    }

    public WMOSpaceWeatherLocationRegister getSwxLocationRegister() {
        return swxLocationRegister;
    }

    public WMORunWaySnowRegister getRwSnowRegister() {
        return rwSnowRegister;
    }

    public _int.wmo.def.collect._2014.ObjectFactory getOfMeteoBulletin() {
        return ofMeteoBulletin;
    }

    /**
     * set the geoservice. Use this method to pre-set the geoservice with non-default initialization e.g. Iwxxm31Helpers helpers = new Iwxxm31Helpers(); GeoService gs = new GeoService(); gs.init(true, "my-firs-catalog", true)); helpers.setGeoService(gs);
     */
    public void setGeoService(GeoService geoService) {

        this.geoService = geoService;
    }

    /**
     * returns geoservice initializing it if necessary with default parameters
     *
     * @throws java.net.URISyntaxException
     */
    public GeoService getGeoService() throws URISyntaxException {
        if (!geoService.isServiceInit()) {
            geoService.init(false, "", true);
        }

        return geoService;

    }

    public List<GTCalculatedRegion> calculateWGS84Point(LinkedList<CoordPoint> pointList) {
        List<GTCalculatedRegion> calculatedRegions = new ArrayList<>();
        for (CoordPoint point : pointList) {
            GTCalculatedRegion gtCalculatedRegion = new GTCalculatedRegion();
            gtCalculatedRegion.setCoordinates(convert(point));
            calculatedRegions.add(gtCalculatedRegion);
            // gtCalculatedRegion.s
        }
        return calculatedRegions;
    }

    public LinkedList<Double> convert(CoordPoint point) {

        LinkedList<Double> points = new LinkedList<>();
        points.add(convertHHMMSStoDecimal(point.getLatitude()));
        points.add(convertHHMMSStoDecimal(point.getLongitude()));
        return points;
    }

    public double convertHHMMSStoDecimal(Coordinate coordinate) {
        double sign = coordinate.getAzimuth() == RUMB_UNITS.S || coordinate.getAzimuth() == RUMB_UNITS.W ? -1 : 1;
        double value = (coordinate.getDeg() * 1.0 + coordinate.getMin() / 60.0) * sign;
        return Math.round(value * 100.0) / 100.0;
    }

    public _int.wmo.def.metce._2013.ObjectFactory getOfMETCE() {
        return ofMETCE;
    }

}


