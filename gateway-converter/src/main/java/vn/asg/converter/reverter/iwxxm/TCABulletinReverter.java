/*
 */
package vn.asg.converter.reverter.iwxxm;

import vn.asg.converter.reverter.entity.TCA;
import vn.asg.converter.reverter.entity.TCABulletin;
import org.w3c.dom.NodeList;

/**
 *
 * @author ThanhNk
 */
public class TCABulletinReverter extends BulletinReverter<TCAReverter, TCABulletin, TCA, NodeList> {//TropicalCycloneAdvisoryType

    @Override
    public TCAReverter createConverter() {
        return new TCAReverter();
    }

    @Override
    public TCABulletin createBulletin() {
        return new TCABulletin();
    }

}

