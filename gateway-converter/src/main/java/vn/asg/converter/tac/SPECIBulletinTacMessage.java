/*
 */
package vn.asg.converter.tac;

import vn.asg.converter.common.CommonRegex;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gamc.spmi.iwxxmConverter.common.MessageStatusType;
import org.gamc.spmi.iwxxmConverter.common.MessageType;
import org.gamc.spmi.iwxxmConverter.exceptions.ParsingException;
import vn.asg.converter.iwxxm.IWXXM31Helpers;
import org.gamc.spmi.iwxxmConverter.metarconverter.METARParsingException;
import org.gamc.spmi.iwxxmConverter.tac.TacMessageImpl;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 *
 * @author ThanhNk
 */
public class SPECIBulletinTacMessage extends TacMessageImpl {

    private SimpleDateFormat bulletinIDDateFormater = new SimpleDateFormat("yyyyMMddHHmmss");
    private DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyyMMddHHmmss");
    private String tac;
    private String messageType;
    private String areaIndicator;
    private String locationIndicator;
    private String number;
    private String time;
    private String group = "";

    private final List<SPECITacMessage> speciTacMessage = new ArrayList<>();

    public SPECIBulletinTacMessage(String tac) {
        super(tac);
    }

    //<editor-fold defaultstate="collapsed" desc=" Class properties ">
    /**
     * public void parse() throws Exception { StringBuffer tacBuff = new StringBuffer(this.tac); Matcher matcher = CommonRegex.metarBulletin.matcher(tacBuff); if (!matcher.lookingAt()) { throw new ParsingException("Message is incorrect format"); }
     *
     * this.messageType = matcher.group("type"); this.areaIndicator = matcher.group("areaIndicator"); this.locationIndicator = matcher.group("locationIndicator"); this.number = matcher.group("no"); this.time = matcher.group("datetime"); this.group = matcher.group("group");
     *
     * int start = 0; int end = matcher.end(); tacBuff.delete(start, end);
     *
     * matcher = CommonRegex.metarToken.matcher(tacBuff); int index = -1; while (matcher.find()) { if (index == -1) { index = matcher.start(); continue; }
     *
     *
     *
     * // Parsing all messages for (METARTacMessage metarTacMessage : this.metarTacMessages) { metarTacMessage.parseMessage(); } }
     *
     */
    public String getTacMessage() {
        return this.tac;
    }

    /**
     * @param messageType the messageType to set
     */
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    /**
     * @return the areaIndicator
     */
    public String getAreaIndicator() {
        return areaIndicator;
    }

    /**
     * @param areaIndicator the areaIndicator to set
     */
    public void setAreaIndicator(String areaIndicator) {
        this.areaIndicator = areaIndicator;
    }

    /**
     * @return the locationIndicator
     */
    public String getLocationIndicator() {
        return locationIndicator;
    }

    /**
     * @param locationIndicator the locationIndicator to set
     */
    public void setLocationIndicator(String locationIndicator) {
        this.locationIndicator = locationIndicator;
    }

    /**
     * @return the number
     */
    public String getNumber() {
        return number;
    }

    /**
     * @param number the number to set
     */
    public void setNumber(String number) {
        this.number = number;
    }

    /**
     * @return the time
     */
    public String getTime() {
        return time;
    }

    /**
     * @param time the time to set
     */
    public void setTime(String time) {
        this.time = time;
    }

    /**
     * @return the group
     */
    public String getGroup() {
        return group;
    }

    /**
     * @param group the group to set
     */
    public void setGroup(String group) {
        this.group = group;
    }

    public List<SPECITacMessage> getMetarTacMessages() {
        return this.speciTacMessage;
    }

    //</editor-fold>
    
    public String getIdentifier() {
        return String.format("A_%s%s%s%s%s%s_C_%s_%s.xml",
                this.messageType,
                this.areaIndicator,
                this.number,
                this.locationIndicator,
                this.time,
                this.group == null ? "": this.group,
                this.locationIndicator,
                this.getMessageIssueDateTime().toString("yyyyMMddHHmmss"));
    }

    @Override
    public void parseMessage() throws ParsingException {
        StringBuffer tac = new StringBuffer(this.getInitialTacString());
        Matcher matcher = CommonRegex.speciBulletin.matcher(tac);
        if (!matcher.lookingAt()) {
            throw new ParsingException("Message is incorrect format");
        }

        this.messageType = matcher.group("type");
        this.areaIndicator = matcher.group("areaIndicator");
        this.locationIndicator = matcher.group("locationIndicator");
        this.number = matcher.group("no");
        this.time = matcher.group("datetime");
        this.group = matcher.group("group");
        
        this.setIcaoCode( this.locationIndicator);
        try {
            this.setMessageIssueDateTime(IWXXM31Helpers.parseDateTimeToken(this.time));
        } catch (ParsingException e) {
            throw new METARParsingException("Check date and time");
        }

        

        int start = 0;
        int end = matcher.end();
        tac.delete(start, end);

        matcher = CommonRegex.speciToken.matcher(tac);
        int index = -1;
        while (matcher.find()) {
            if (index == -1) {
                index = matcher.start();
                continue;
            }

            String found = tac.substring(index, matcher.start());
            speciTacMessage.add(new SPECITacMessage(found));
            index = matcher.start();
        }

        if (index >= 0) {
            String found = tac.substring(index);
            speciTacMessage.add(new SPECITacMessage(found));
        }

        // Parsing all messages
        for (SPECITacMessage speciTacMessage : this.speciTacMessage) {
            speciTacMessage.parseMessage();
        }
    }

    @Override
    public String getTacStartToken() {
        // #1 : Type
        return "SP";
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.METAR;
    }

    @Override
    public Interval getValidityInterval() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Pattern getHeaderPattern() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageStatusType getMessageStatusType() {
        return MessageStatusType.NORMAL;
    }
}


