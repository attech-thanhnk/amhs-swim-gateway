/*
 */
package vn.asg.converter.iwxxm;

import _int.icao.iwxxm._2023_1.METARType;
import vn.asg.converter.tac.METARBulletinTacMessage;
import vn.asg.converter.tac.METARTacMessage;
import _int.wmo.def.collect._2014.MeteorologicalBulletinType;
import _int.wmo.def.collect._2014.MeteorologicalInformationMemberPropertyType;
import java.io.UnsupportedEncodingException;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import org.apache.log4j.Logger;
import org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException;
import org.gamc.spmi.iwxxmConverter.exceptions.ParsingException;
import org.gamc.spmi.iwxxmConverter.wmo.WMORegister.WMORegisterException;

/**
 *
 * @author ThanhNk
 */
public class METARBulletinConverterV3 extends TacBaseConverter<METARBulletinTacMessage, MeteorologicalBulletinType> {

    private static final Logger logger = Logger.getLogger(METARBulletinConverterV3.class);
    private final IWXXM31Helpers iwxxmHelpers = new IWXXM31Helpers();
    private String dateTime;
    private String dateTimePosition;
    private String identifier;
    
    
    
    public METARBulletinConverterV3() {
        logger.info(String.format("initiated %s", this.getClass()));
    }

    /**
     *
     * @param tac
     * @return
     * @throws org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException
     * @throws Exception
     */
    @Override
    public String convertTacToXML(String tac) throws ConvertingFailException{
        try {
            logger.info(String.format("Converting to XML"));
            METARBulletinTacMessage translatedMessage = new METARBulletinTacMessage(tac);
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
            METARBulletinTacMessage translatedMessage = new METARBulletinTacMessage(tac);
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
    public MeteorologicalBulletinType convertMessage(METARBulletinTacMessage translatedMessage) 
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

        METARConverterV3 metarConverter = new METARConverterV3();
        for (METARTacMessage metarTacMessage : translatedMessage.getMetarTacMessages()) {
            METARType metaType = (METARType) metarConverter.convertMessage(metarTacMessage);
            JAXBElement<METARType> metaTypeElement = iwxxmHelpers.getOfIWXXM().createMETAR(metaType);
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

