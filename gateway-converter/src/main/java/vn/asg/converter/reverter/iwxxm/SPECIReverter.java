/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.SPECIType;
import vn.asg.converter.reverter.entity.SPECI;
import javax.xml.bind.JAXBException;

/**
 *
 * @author ThanhNk
 */
public class SPECIReverter extends METARBaseReverter<SPECIType, SPECI> {

    @Override
    public SPECI convert(String content) throws IWXXMParsingException {
        SPECIType speci = null;
        try {
            speci = revert(content, SPECIType.class);
        } catch (JAXBException ex) {
            throw new IWXXMParsingException("Không đọc được định dạng điện văn.", ex);
        }

        return convert(speci);
    }

    @Override
    public String convertToString(String content) throws IWXXMParsingException {
        SPECI speci = convert(content);
        return speci == null ? null : speci.toString();
    }

    @Override
    public SPECI convert(SPECIType type) throws IWXXMParsingException {
        if (type == null) {
            return null;
        }
        
        SPECI speci = new SPECI();
        this.parse(type, speci);
        return speci;
    }

}

