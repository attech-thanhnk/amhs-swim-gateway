/*
 */
package vn.asg.converter.reverter.entity;

import _int.icao.iwxxm._2023_1.AIRMETType;
import vn.asg.converter.reverter.iwxxm.Common;
import vn.asg.converter.reverter.iwxxm.IWXXMParsingException;

/**
 *
 * @author ThanhNk
 */
public class AIRMET extends SIGMET {

    @Override
    public String getTypeofReport() {
        return "AIRMET";
    }

    public AIRMET() {
    }
    private Common common = new Common();

    public AIRMET(AIRMETType objectType) throws IWXXMParsingException {
//        parse(objectType);
    }

    @Override
    public String toString() {
        return super.toString();
    }

}

