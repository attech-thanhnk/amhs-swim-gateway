/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.SIGMETType;
import vn.asg.converter.reverter.entity.SIGMET;
import vn.asg.converter.reverter.entity.SIGMETBulletin;

/**
 *
 * @author ThanhNk
 */
public class SIGMETBulletinReverter extends BulletinReverter<SIGMETReverter, SIGMETBulletin, SIGMET, SIGMETType>{

    @Override
    public SIGMETReverter createConverter() {
        return new SIGMETReverter();
    }

    @Override
    public SIGMETBulletin createBulletin() {
        return new SIGMETBulletin();
    }

   
    
   
}

