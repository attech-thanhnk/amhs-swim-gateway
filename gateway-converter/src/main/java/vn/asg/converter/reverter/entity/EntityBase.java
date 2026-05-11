/*
 */
package vn.asg.converter.reverter.entity;

import _int.icao.iwxxm._2023_1.AerodromeSurfaceWindTrendForecastType;
import _int.icao.iwxxm._2023_1.AirportHeliportPropertyType;
import _int.icao.iwxxm._2023_1.CloudLayerPropertyType;
import _int.icao.iwxxm._2023_1.CloudLayerType;
import _int.icao.iwxxm._2023_1.SigConvectiveCloudTypeType;
import aero.aixm.schema._5_1.AirportHeliportTimeSlicePropertyType;
import aero.aixm.schema._5_1.AirportHeliportTimeSliceType;
import aero.aixm.schema._5_1.AirportHeliportType;
import aero.aixm.schema._5_1.CodeAirportHeliportDesignatorType;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.xml.bind.JAXBElement;
import net.opengis.gml.v_3_2_1.LengthType;
import net.opengis.gml.v_3_2_1.ReferenceType;
import net.opengis.gml.v_3_2_1.TimeInstantPropertyType;
import net.opengis.gml.v_3_2_1.TimeInstantType;
import net.opengis.gml.v_3_2_1.TimePositionType;
import vn.asg.converter.reverter.iwxxm.Common;
import vn.asg.converter.utils.Builder;
import vn.asg.converter.utils.CustomDateFormat;

/**
 *
 * @author ThanhNk
 */
public abstract class EntityBase {

    private SimpleDateFormat instantTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");//dd/MM/yyyy         
    protected SimpleDateFormat tacDateFormat = new SimpleDateFormat("ddHHmm'Z'");//dd/MM/yyyy         
    protected DateFormat dateFormater = new CustomDateFormat();
    protected KeyStore weatherCode = KeyStore.load("resources/weather-codes.xml");
    protected KeyStore cloudCode = KeyStore.load("resources/cloud-codes.xml");

    public abstract String getTypeofReport();

}


