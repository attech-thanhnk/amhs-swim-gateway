/*
 */
package vn.asg.converter.iwxxm;

import vn.asg.converter.tac.TAFTacMessage;
import vn.asg.converter.tac.TAFBulletinTacMessage;
import _int.icao.iwxxm._2023_1.TAFType;
import _int.wmo.def.collect._2014.MeteorologicalBulletinType;
import _int.wmo.def.collect._2014.MeteorologicalInformationMemberPropertyType;
import java.io.UnsupportedEncodingException;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException;
import org.gamc.spmi.iwxxmConverter.exceptions.ParsingException;
import org.gamc.spmi.iwxxmConverter.wmo.WMORegister;

/**
 *
 * @author ThanhNk
 */
public class TAFBulletinConvertV3 extends TacBaseConverter<TAFBulletinTacMessage, MeteorologicalBulletinType> {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(TAFBulletinConvertV3.class);
    private final IWXXM31Helpers iwxxmHelpers = new IWXXM31Helpers();
    private String dateTime;
    private String dateTimePosition;
    private String identifier;

    /**
     *
     * @param tac
     * @return
     * @throws org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException
     */
    @Override
    public String convertTacToXML(String tac) throws ConvertingFailException {
        try {
            logger.info(String.format("Converting to XML"));
            TAFBulletinTacMessage translatedMessage = new TAFBulletinTacMessage(tac);
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
            TAFBulletinTacMessage translatedMessage = new TAFBulletinTacMessage(tac);
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
        return this.identifier;
    }

    @Override
    protected JAXBElement<MeteorologicalBulletinType> createJaxbElement(MeteorologicalBulletinType r) {
        return iwxxmHelpers.getOfMeteoBulletin().createMeteorologicalBulletin(r);
    }
    
    /**
     *
     * @param translatedMessage
     * @return
     * @throws ParsingException
     * @throws DatatypeConfigurationException
     * @throws UnsupportedEncodingException
     * @throws JAXBException
     * @throws WMORegister.WMORegisterException
     */
    @Override
    public MeteorologicalBulletinType convertMessage(TAFBulletinTacMessage translatedMessage)
            throws ParsingException, DatatypeConfigurationException, UnsupportedEncodingException, JAXBException, WMORegister.WMORegisterException {

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

        TAFConverterV3 metarConverter = new TAFConverterV3();
        for (TAFTacMessage metarTacMessage : translatedMessage.getMessages()) {
            TAFType metaType = metarConverter.convertMessage(metarTacMessage);
            JAXBElement<TAFType> metaTypeElement = iwxxmHelpers.getOfIWXXM().createTAF(metaType);
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

