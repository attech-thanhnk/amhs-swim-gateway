/*
 */
package vn.asg.converter.reverter.iwxxm;

import _int.icao.iwxxm._2023_1.AIRMETType;
import vn.asg.converter.reverter.entity.AIRMET;
import vn.asg.converter.reverter.entity.AIRMETBulletin;

/**
 *
 * @author ThanhNk
 */
public class AIRMETBulletinReverter extends BulletinReverter<AIRMETReverter, AIRMETBulletin, AIRMET, AIRMETType>{

    @Override
    public AIRMETReverter createConverter() {
        return new AIRMETReverter();
    }

    @Override
    public AIRMETBulletin createBulletin() {
        return new AIRMETBulletin();
    }

   
    
   
}

