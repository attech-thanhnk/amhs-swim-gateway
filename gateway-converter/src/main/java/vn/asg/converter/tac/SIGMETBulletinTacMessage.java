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
public class SIGMETBulletinTacMessage extends TacMessageImpl {

    private SimpleDateFormat bulletinIDDateFormater = new SimpleDateFormat("yyyyMMddHHmmss");
    private DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyyMMddHHmmss");
    
    
    // private String tac;
    private String messageType;
    private String areaIndicator;
    private String locationIndicator;
    private String number;
    private String time;
    private String group = "";

    private final List<SIGMETTacMessage> messages = new ArrayList<>();

    public SIGMETBulletinTacMessage(String initialTacMessage) {
        super(initialTacMessage);
    }

    @Override
    public void parseMessage() throws ParsingException {
        StringBuffer tac = new StringBuffer(this.getInitialTacString());
        Matcher matcher = CommonRegex.sigmetBulletin.matcher(tac);
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

        matcher = CommonRegex.sigmetToken.matcher(tac);
        int index = -1;
        while (matcher.find()) {
            if (index == -1) {
                index = matcher.start();
                continue;
            }

            String found = tac.substring(index, matcher.start());
            messages.add(new SIGMETTacMessage(found));
            index = matcher.start();
        }

        if (index >= 0) {
            String found = tac.substring(index);
            messages.add(new SIGMETTacMessage(found));
        }

        // Parsing all messages
        for (SIGMETTacMessage message : this.messages) {
            message.parseMessage();
        }
        
        this.identifier = createMessageID();
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
    public String getTacStartToken() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SIGMET;
    }

    @Override
    public MessageStatusType getMessageStatusType() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public String createMessageID() {
        return String.format("A_%s%s%s%s%s%s_C_%s_%s.xml",
                convertToIwxxmType(this.messageType),
                this.areaIndicator,
                this.number,
                this.locationIndicator,
                this.time,
                this.group == null ? "": this.group,
                this.locationIndicator,
                this.getMessageIssueDateTime().toString("yyyyMMddHHmmss"));
    }
    
    //<editor-fold defaultstate="collapsed" desc=" Class properties ">

    /**
     * @return the areaIndicator
     */
    public String getAreaIndicator() {
        return areaIndicator;
    }

    /**
     * @return the locationIndicator
     */
    public String getLocationIndicator() {
        return locationIndicator;
    }

    /**
     * @return the number
     */
    public String getNumber() {
        return number;
    }

    /**
     * @return the time
     */
    public String getTime() {
        return time;
    }

    /**
     * @return the group
     */
    public String getGroup() {
        return group;
    }

    /**
     * @return the messages
     */
    public List<SIGMETTacMessage> getMessages() {
        return messages;
    }
    

    //</editor-fold>
}


