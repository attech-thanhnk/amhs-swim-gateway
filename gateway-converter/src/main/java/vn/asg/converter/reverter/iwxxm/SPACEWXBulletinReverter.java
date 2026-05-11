/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.SpaceWeatherAdvisoryType;
import vn.asg.converter.reverter.entity.SPACEWX;
import vn.asg.converter.reverter.entity.SPACEWXBulletin;

/**
 *
 * @author ThanhNk
 */
public class SPACEWXBulletinReverter extends BulletinReverter<SPACEWXReverter, SPACEWXBulletin, SPACEWX, SpaceWeatherAdvisoryType>{

    @Override
    public SPACEWXReverter createConverter() {
        return new SPACEWXReverter();
    }

    @Override
    public SPACEWXBulletin createBulletin() {
        return new SPACEWXBulletin();
    }
   
}

