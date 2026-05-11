/*
 */
package vn.asg.converter.iwxxm;

import vn.asg.converter.tac.SIGMETTacMessage;
import vn.asg.converter.tac.SIGMETBulletinTacMessage;
import _int.icao.iwxxm._2023_1.SIGMETType;
import _int.wmo.def.collect._2014.MeteorologicalBulletinType;
import _int.wmo.def.collect._2014.MeteorologicalInformationMemberPropertyType;
import java.io.UnsupportedEncodingException;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import net.opengis.gml.v_3_2_1.StringOrRefType;
import org.gamc.spmi.iwxxmConverter.exceptions.ConvertingFailException;
import org.gamc.spmi.iwxxmConverter.exceptions.ParsingException;
import org.gamc.spmi.iwxxmConverter.wmo.WMORegister;

/**
 *
 * @author ThanhNk
 */
public class SIGMETBulletinConverterV3 extends TacBaseConverter<SIGMETTacMessage, MeteorologicalBulletinType> {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(SIGMETBulletinConverterV3.class);
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
            SIGMETBulletinTacMessage translatedMessage = new SIGMETBulletinTacMessage(tac);
            translatedMessage.parseMessage();
            MeteorologicalBulletinType metoroLogicalType = convert(translatedMessage);
            this.identifier = translatedMessage.getMsgID();
            return marshallMessageToXML(metoroLogicalType);
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    @Override
    public byte[] convertTacToXML(String tac, boolean zipped) throws ConvertingFailException {
        try {
            logger.info(String.format("Converting to File"));
            SIGMETBulletinTacMessage translatedMessage = new SIGMETBulletinTacMessage(tac);
            translatedMessage.parseMessage();
            MeteorologicalBulletinType metoroLogicalType = convert(translatedMessage);

            this.identifier = translatedMessage.getMsgID();
            // this.identifier = translatedMessage.createMessageID();
            if (zipped) {
                this.identifier += ".gz";
            }
            return marshallMessageToByte(metoroLogicalType, zipped);
        } catch (Exception ex) {
            throw handleException(ex);
        }
    }

    private MeteorologicalBulletinType convert(SIGMETBulletinTacMessage translatedMessage)
            throws ParsingException, DatatypeConfigurationException, UnsupportedEncodingException, JAXBException, WMORegister.WMORegisterException {

        MeteorologicalBulletinType metoroLogicalType = iwxxmHelpers.getOfMeteoBulletin().createMeteorologicalBulletinType();

        // Using desciption to quote the origin message for debuggin
        /**
         * <gml:description xlink:type="simple"></gml:description>
         */
        StringOrRefType refTacString = iwxxmHelpers.getOfGML().createStringOrRefType();
        refTacString.setValue(translatedMessage.getInitialTacString());
        metoroLogicalType.setDescription(refTacString);

        dateTime = translatedMessage.getMessageIssueDateTime().toString(iwxxmHelpers.getDateTimeFormat()) + "Z";
        dateTimePosition = translatedMessage.getMessageIssueDateTime().toString(iwxxmHelpers.getDateTimeISOFormat());

        // Id with ICAO code and current timestamp
        metoroLogicalType.setId(iwxxmHelpers.generateUUIDv4(String.format("metar-%s-%s", translatedMessage.getIcaoCode(), dateTime)));
        // metoroLogicalType.setBulletinIdentifier(translatedMessage.createMessageID());
        metoroLogicalType.setBulletinIdentifier(translatedMessage.getMsgID());

        SIGMETConverterV3 metarConverter = new SIGMETConverterV3();
        for (SIGMETTacMessage metarTacMessage : translatedMessage.getMessages()) {
            SIGMETType metaType = metarConverter.convertMessage(metarTacMessage);
            JAXBElement<SIGMETType> metaTypeElement = iwxxmHelpers.getOfIWXXM().createSIGMET(metaType);
            MeteorologicalInformationMemberPropertyType meteologicalInformationMemberType = iwxxmHelpers.getOfMeteoBulletin().createMeteorologicalInformationMemberPropertyType();
            meteologicalInformationMemberType.setAbstractFeature(metaTypeElement);
            metoroLogicalType.getMeteorologicalInformation().add(meteologicalInformationMemberType);
        }

        return metoroLogicalType;
    }

    @Override
    public MeteorologicalBulletinType addTranslationCentreHeader(MeteorologicalBulletinType report)
            throws DatatypeConfigurationException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    protected JAXBElement<MeteorologicalBulletinType> createJaxbElement(MeteorologicalBulletinType r) {
        return iwxxmHelpers.getOfMeteoBulletin().createMeteorologicalBulletin(r);
    }

    @Override
    public MeteorologicalBulletinType convertMessage(SIGMETTacMessage translatedMessage) throws DatatypeConfigurationException, UnsupportedEncodingException, JAXBException, ParsingException, WMORegister.WMORegisterException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}

