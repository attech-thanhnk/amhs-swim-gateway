/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.SPECIType;
import vn.asg.converter.reverter.entity.SPECI;
import vn.asg.converter.reverter.entity.SPECIBulletin;

/**
 *
 * @author ThanhNk
 */
public class SPECIBulletinReverter extends BulletinReverter<SPECIReverter, SPECIBulletin, SPECI, SPECIType> {

    @Override
    public SPECIReverter createConverter() {
        return new SPECIReverter();
    }

    @Override
    public SPECIBulletin createBulletin() {
        return new SPECIBulletin();
    }

   
    
   
}

