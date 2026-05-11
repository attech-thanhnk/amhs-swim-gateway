/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.METARType;
import vn.asg.converter.reverter.entity.METAR;
import javax.xml.bind.JAXBException;

/**
 *
 * @author ThanhNk
 */
public class METARReverter extends METARBaseReverter<METARType, METAR> {

    @Override
    public METAR convert(String content) throws IWXXMParsingException {

        METARType metar = null;
        try {
            metar = revert(content, METARType.class);
        } catch (JAXBException ex) {
            throw new IWXXMParsingException("Không đọc được định dạng điện văn.", ex);
        }

        return convert(metar);
    }
    
    
    @Override
    public METAR convert(METARType objectType) throws IWXXMParsingException {
        
        if (objectType == null) {
            return null;
        }

        METAR metar = new METAR();

        this.parse(objectType, metar);

        return metar;

    }

    @Override
    public String convertToString(String content) throws IWXXMParsingException {
        METAR metar = convert(content);
        return metar == null ? null : metar.toString();
    }

}

