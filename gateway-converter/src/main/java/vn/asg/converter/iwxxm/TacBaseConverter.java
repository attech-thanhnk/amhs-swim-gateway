/*
 */
package vn.asg.converter.iwxxm;

import _int.wmo.def.collect._2014.MeteorologicalBulletinType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPOutputStream;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.datatype.DatatypeConfigurationException;
import net.opengis.gml.v_3_2_1.AbstractFeatureType;
import org.gamc.spmi.iwxxmConverter.common.NamespaceMapper;
import org.gamc.spmi.iwxxmConverter.common.StringConstants;
import org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException;
import org.gamc.spmi.iwxxmConverter.exceptions.ParsingException;
import org.gamc.spmi.iwxxmConverter.tac.TacConverter;
import org.gamc.spmi.iwxxmConverter.tac.TacMessage;
import org.gamc.spmi.iwxxmConverter.wmo.WMORegister.WMORegisterException;

/**
 *
 * @author ThanhNk
 * @param <TacType>
 * @param <TargetType>
 */
public abstract class TacBaseConverter<TacType extends TacMessage, TargetType extends AbstractFeatureType> implements TacConverter<TacType, TargetType> {

    protected final IWXXM31Helpers iwxxmHelpers = new IWXXM31Helpers();

    /***
     * @param targetObj
     * @param classOfTarget
     * @return content of IWXXM file
     * @throws JAXBException
     * @throws UnsupportedEncodingException
     * @throws IOException 
     */
    @Override
    public String marshallMessageToXML(TargetType targetObj) throws JAXBException, UnsupportedEncodingException, IOException {
        Marshaller jaxbMarshaller = createMarshaller(targetObj);

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            JAXBElement<TargetType> metarRootElement = (JAXBElement<TargetType>) createJaxbElement(targetObj);
            jaxbMarshaller.marshal(metarRootElement, stream);
            String xml = stream.toString("UTF-8");
            return postProcessXML(xml);
        }
    }

    protected String postProcessXML(String xml) {
        return xml;
    }

    /**
     * Serialize IWXXM file to 
     * @param targetObj
     * @param classOfTarget
     * @param zipped
     * @return
     * @throws JAXBException
     * @throws UnsupportedEncodingException
     * @throws IOException 
     */
    public byte[] marshallMessageToByte(TargetType targetObj, boolean zipped) throws JAXBException, UnsupportedEncodingException, IOException {
        Marshaller jaxbMarshaller = createMarshaller(targetObj);
        // Serialize
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            JAXBElement<TargetType> metarRootElement = (JAXBElement<TargetType>) createJaxbElement(targetObj);
            if (!zipped) {
                jaxbMarshaller.marshal(metarRootElement, outputStream);
            } else {
                try (GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream)) {
                    jaxbMarshaller.marshal(metarRootElement, gzipStream);
                    gzipStream.close();
                }
            }
            outputStream.close();
            return outputStream.toByteArray();
        }
    }
    
    private Marshaller createMarshaller(TargetType targetObj) throws PropertyException, JAXBException {
        
        // JAXBContext jaxbContext = JAXBContext.newInstance(classOfTarget);
        JAXBContext jaxbContext = JAXBContext.newInstance(targetObj.getClass());
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.setProperty(StringConstants.SUN_JAXB_NAMESPACE_MAPPING_PROPERTY_NAME, new NamespaceMapper());

        if (targetObj instanceof MeteorologicalBulletinType) {
            jaxbMarshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, UriConstants.SCHEMA_LOCATION_OF_BULLETIN_COLLECTION);
        } else {
            jaxbMarshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, UriConstants.SCHEMA_LOCATION_OF_SINGLE);
        }
        return jaxbMarshaller;
    }

    protected ConvertingFailException handleException(Exception ex) {
        if (ex instanceof IOException) {
            return new ConvertingFailException("Lỗi IO." + ex.getMessage(), ex);
        } else if (ex instanceof JAXBException) {
            return new ConvertingFailException("Lỗi chuyển đổi thống tin sang dạng XML." + ex.getMessage(), ex);
        } else if (ex instanceof DatatypeConfigurationException) {
            return new ConvertingFailException("Lỗi định dạng dữ liệu." + ex.getMessage(), ex);
        } else if (ex instanceof ParsingException) {
            return new ConvertingFailException("Lỗi phân tích nội dung điện văn." + ex.getMessage(), ex);
        } else if (ex instanceof WMORegisterException) {
            return new ConvertingFailException("Không tìm thấy giá trị tham chiếu WMO." + ex.getMessage(), ex);
        } else {
            return new ConvertingFailException("Lỗi chuyển đổi." + ex.getMessage(), ex);
        }
    }

    protected abstract JAXBElement<TargetType> createJaxbElement(TargetType r);

}

