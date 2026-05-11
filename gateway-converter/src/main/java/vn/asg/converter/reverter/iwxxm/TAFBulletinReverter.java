/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.TAFType;
import vn.asg.converter.reverter.entity.TAF;
import vn.asg.converter.reverter.entity.TAFBulletin;

/**
 *
 * @author ThanhNk
 */
public class TAFBulletinReverter extends BulletinReverter<TAFReverter, TAFBulletin, TAF, TAFType>{

    @Override
    public TAFReverter createConverter() {
        return new TAFReverter();
    }

    @Override
    public TAFBulletin createBulletin() {
        return new TAFBulletin();
    }

   
    
   
}

