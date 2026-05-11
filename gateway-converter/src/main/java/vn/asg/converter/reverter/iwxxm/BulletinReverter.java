/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.wmo.def.collect._2014.MeteorologicalBulletinType;
import _int.wmo.def.collect._2014.MeteorologicalInformationMemberPropertyType;
import vn.asg.converter.reverter.entity.BulletinBase;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

/**
 *
 * @author ThanhNk
 * @param <TBulletin>
 * @param <TEntity>
 */
public abstract class BulletinReverter<TReverter extends Reverter<IWXXMType, TEntity>, TBulletin extends BulletinBase<TEntity>, TEntity, IWXXMType>
        extends Reverter<MeteorologicalBulletinType, TBulletin> {

    @Override
    public TBulletin convert(String content) throws IWXXMParsingException {
        MeteorologicalBulletinType metar = null;
        try {
            metar = revert(content, MeteorologicalBulletinType.class);
        } catch (JAXBException ex) {
            throw new IWXXMParsingException("Không đọc được định dạng điện văn.", ex);
        }

        return convert(metar);
    }

    @Override
    public TBulletin convert(MeteorologicalBulletinType type) throws IWXXMParsingException {
        if (type == null) {
            return null;
        }

        if (!type.isSetMeteorologicalInformation()) {
            return null;
        }

        TReverter reverter = this.createConverter();

        TBulletin metarBulletin = this.createBulletin();

        metarBulletin.setIdentify(Common.convertBulletinIdentification(type.getBulletinIdentifier()));

        List<MeteorologicalInformationMemberPropertyType> meteoroLogicalInformationMemberProperty = type.getMeteorologicalInformation();
        for (MeteorologicalInformationMemberPropertyType meteoroLogicalType : meteoroLogicalInformationMemberProperty) {

            JAXBElement<IWXXMType> meteoType = (JAXBElement<IWXXMType>) meteoroLogicalType.getAbstractFeature();

            TEntity meteo = reverter.convert(meteoType.getValue());
            if (meteo == null) {
                continue;
            }
            metarBulletin.getEntities().add(meteo);
        }

        return metarBulletin;
    }

    @Override
    public String convertToString(String content) throws IWXXMParsingException {
        TBulletin metarBulletin = convert(content);
        return metarBulletin == null ? null : metarBulletin.toString();
    }

    public abstract TReverter createConverter();

    public abstract TBulletin createBulletin();

}

