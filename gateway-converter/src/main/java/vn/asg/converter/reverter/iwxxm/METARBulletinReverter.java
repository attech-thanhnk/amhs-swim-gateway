/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.METARType;
import vn.asg.converter.reverter.entity.METAR;
import vn.asg.converter.reverter.entity.METARBulletin;

/**
 *
 * @author ThanhNk
 */
public class METARBulletinReverter extends BulletinReverter<METARReverter, METARBulletin, METAR, METARType>{

    @Override
    public METARReverter createConverter() {
        return new METARReverter();
    }

    @Override
    public METARBulletin createBulletin() {
        return new METARBulletin();
    }

    
}

