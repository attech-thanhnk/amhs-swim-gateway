/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.TropicalCycloneAdvisoryType;
import _int.icao.iwxxm._2023_1.VolcanicAshAdvisoryPropertyType;
import vn.asg.converter.reverter.entity.TCA;
import vn.asg.converter.reverter.entity.TCABulletin;
import vn.asg.converter.reverter.entity.VAA;
import vn.asg.converter.reverter.entity.VAABulletin;
import org.w3c.dom.NodeList;

/**
 *
 * @author ThanhNk
 */
public class VAABulletinReverter extends BulletinReverter<VAAReverter, VAABulletin, VAA, NodeList> {//VolcanicAshAdvisoryPropertyType

    @Override
    public VAAReverter createConverter() {
        return new VAAReverter();
    }

    @Override
    public VAABulletin createBulletin() {
        return new VAABulletin();
    }

}

