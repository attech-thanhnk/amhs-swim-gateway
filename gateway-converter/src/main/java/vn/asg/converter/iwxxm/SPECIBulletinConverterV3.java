/*
 */
package vn.asg.converter.iwxxm;

import vn.asg.converter.tac.SPECIBulletinTacMessage;
import vn.asg.converter.tac.SPECITacMessage;
import _int.icao.iwxxm._2023_1.SPECIType;
import _int.wmo.def.collect._2014.MeteorologicalBulletinType;
import _int.wmo.def.collect._2014.MeteorologicalInformationMemberPropertyType;
import java.io.UnsupportedEncodingException;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException;
import org.gamc.spmi.iwxxmConverter.exceptions.ParsingException;
import org.gamc.spmi.iwxxmConverter.wmo.WMORegister.WMORegisterException;

/**
 *
 * @author ThanhNk
 */
public class SPECIBulletinConverterV3 extends TacBaseConverter<SPECIBulletinTacMessage, MeteorologicalBulletinType> {
    
    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(SPECIBulletinConverterV3.class);
    private IWXXM31Helpers iwxxmHelpers = new IWXXM31Helpers();
    private String dateTime;
    private String dateTimePosition;
    private String identifier;

    @Override
    public String convertTacToXML(String tac)  throws ConvertingFailException {
        try {
            logger.info(String.format("Converting to XML"));
            SPECIBulletinTacMessage translatedMessage = new SPECIBulletinTacMessage(tac);
            translatedMessage.parseMessage();
            MeteorologicalBulletinType metoroLogicalType = convertMessage(translatedMessage);
            return marshallMessageToXML(metoroLogicalType);
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }
    
    @Override
    public byte[] convertTacToXML(String tac, boolean zipped) throws ConvertingFailException {
        try {
            logger.info(String.format("Converting to File"));
            SPECIBulletinTacMessage translatedMessage = new SPECIBulletinTacMessage(tac);
            translatedMessage.parseMessage();
            MeteorologicalBulletinType metoroLogicalType = convertMessage(translatedMessage);
            this.identifier = translatedMessage.getIdentifier();
            if (zipped) {
                this.identifier += ".gz";
            }
            return marshallMessageToByte(metoroLogicalType, zipped);
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }
    
    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    protected JAXBElement<MeteorologicalBulletinType> createJaxbElement(MeteorologicalBulletinType r) {
        return iwxxmHelpers.getOfMeteoBulletin().createMeteorologicalBulletin(r);
    }
    
    @Override
    public MeteorologicalBulletinType convertMessage(SPECIBulletinTacMessage translatedMessage) 
            throws ParsingException, DatatypeConfigurationException, UnsupportedEncodingException, JAXBException, WMORegisterException {
        MeteorologicalBulletinType metoroLogicalType = iwxxmHelpers.getOfMeteoBulletin().createMeteorologicalBulletinType();

        // Using desciption to quote the origin message for debuggin
        /**
         * <gml:description xlink:type="simple"></gml:description>
         */
        
        /*
        StringOrRefType refTacString = iwxxmHelpers.getOfGML().createStringOrRefType();
        refTacString.setValue(translatedMessage.getInitialTacString());
        metoroLogicalType.setDescription(refTacString);
        */

        dateTime = translatedMessage.getMessageIssueDateTime().toString(iwxxmHelpers.getDateTimeFormat()) + "Z";
        dateTimePosition = translatedMessage.getMessageIssueDateTime().toString(iwxxmHelpers.getDateTimeISOFormat());

        // Id with ICAO code and current timestamp
        metoroLogicalType.setId(iwxxmHelpers.generateUUIDv4(String.format("metar-%s-%s", translatedMessage.getIcaoCode(), dateTime)));
        metoroLogicalType.setBulletinIdentifier(translatedMessage.getIdentifier());
        
        SPECIConverterV3 metarConverter = new SPECIConverterV3();
        for (SPECITacMessage metarTacMessage : translatedMessage.getMetarTacMessages()) {
            SPECIType metaType = metarConverter.convertMessage(metarTacMessage);
            JAXBElement<SPECIType> metaTypeElement = iwxxmHelpers.getOfIWXXM().createSPECI(metaType);
            MeteorologicalInformationMemberPropertyType meteologicalInformationMemberType = iwxxmHelpers.getOfMeteoBulletin().createMeteorologicalInformationMemberPropertyType();
            meteologicalInformationMemberType.setAbstractFeature(metaTypeElement);
            metoroLogicalType.getMeteorologicalInformation().add(meteologicalInformationMemberType);
        }
        
        return metoroLogicalType;
    }

    @Override
    public MeteorologicalBulletinType addTranslationCentreHeader(MeteorologicalBulletinType report) throws DatatypeConfigurationException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}

