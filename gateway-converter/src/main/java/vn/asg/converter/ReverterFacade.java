package vn.asg.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.asg.converter.reverter.aixm.NotamReverter;
import vn.asg.converter.reverter.fixm.FplReverter;
import vn.asg.converter.reverter.iwxxm.*;

/**
 * Entry point duy nhất để dịch XML về dạng Text TAC.
 * @author ThanhNk
 */
public class ReverterFacade {

    private static final Logger log = LoggerFactory.getLogger(ReverterFacade.class);

    /**
     * Dịch ngược chuỗi XML thành chuỗi TAC.
     * Tự động nhận diện loại XML dựa trên namespace hoặc các tag đặc trưng.
     */
    public String revert(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            return null;
        }

        try {
            // Nhận dạng loại XML một cách nhanh chóng bằng cách kiểm tra chứa chuỗi
            if (xmlContent.contains("aixm:AIXMBasicMessage") || xmlContent.contains("urn:aero:aixm")) {
                log.info("Detected AIXM (NOTAM) message for reverting");
                return new NotamReverter().revert(xmlContent);
            } 
            else if (xmlContent.contains("fixm:Flight") || xmlContent.contains("http://www.fixm.aero")) {
                log.info("Detected FIXM (FPL) message for reverting");
                return new FplReverter().revert(xmlContent);
            }
            else if (xmlContent.contains("iwxxm:METAR")) {
                log.info("Detected IWXXM METAR message for reverting");
                return new METARReverter().convertToString(xmlContent);
            }
            else if (xmlContent.contains("iwxxm:SPECI")) {
                log.info("Detected IWXXM SPECI message for reverting");
                return new SPECIReverter().convertToString(xmlContent);
            }
            else if (xmlContent.contains("iwxxm:SpaceWeatherAdvisory")) {
                log.info("Detected IWXXM SpaceWeatherAdvisory message for reverting");
                return new SPACEWXReverter().convertToString(xmlContent);
            }
            else if (xmlContent.contains("iwxxm:TAF")) {
                log.info("Detected IWXXM TAF message for reverting");
                return new TAFReverter().convertToString(xmlContent);
            }
            else if (xmlContent.contains("iwxxm:SIGMET")) {
                log.info("Detected IWXXM SIGMET message for reverting");
                return new SIGMETReverter().convertToString(xmlContent);
            }
            else if (xmlContent.contains("iwxxm:AIRMET")) {
                log.info("Detected IWXXM AIRMET message for reverting");
                return new AIRMETReverter().convertToString(xmlContent);
            }
            else if (xmlContent.contains("iwxxm:VolcanicAshAdvisory")) {
                log.info("Detected IWXXM VolcanicAshAdvisory message for reverting");
                return new VAAReverter().convertToString(xmlContent);
            }
            else if (xmlContent.contains("iwxxm:TropicalCycloneAdvisory")) {
                log.info("Detected IWXXM TropicalCycloneAdvisory message for reverting");
                return new TCAReverter().convertToString(xmlContent);
            }
            else {
                log.warn("Unknown XML format. Cannot revert.");
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to revert XML to TAC", e);
            return null;
        }
    }
}
