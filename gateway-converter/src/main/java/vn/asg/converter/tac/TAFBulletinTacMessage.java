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
import org.gamc.spmi.iwxxmConverter.common.CoreUtil;
import org.gamc.spmi.iwxxmConverter.metarconverter.METARParsingException;
import org.gamc.spmi.iwxxmConverter.tac.TacMessageImpl;
import org.gamc.spmi.iwxxmConverter.tafconverter.TAFParsingException;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 *
 * @author ThanhNk
 */
public class TAFBulletinTacMessage extends TacMessageImpl {

    private SimpleDateFormat bulletinIDDateFormater = new SimpleDateFormat("yyyyMMddHHmmss");
    private DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    private String tac;

    private String messageType;
    private String areaIndicator;
    private String locationIndicator;
    private String number;
    private String time;
    private String group = "";

    private final List<TAFTacMessage> messages = new ArrayList<>();

    public TAFBulletinTacMessage(String initialTacMessage) {
        super(initialTacMessage);
    }

    @Override
    public void parseMessage() throws ParsingException {
        
        StringBuffer tac = new StringBuffer(this.getInitialTacString());
        // StringBuffer tac = new StringBuffer(this.normalize(this.getInitialTacString()));
        
        Matcher matcher = CommonRegex.tafBulletin.matcher(tac);
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
            throw new TAFParsingException("Check date and time");
        }

        

        int start = 0;
        int end = matcher.end();
        tac.delete(start, end);

        matcher = CommonRegex.tafToken.matcher(tac);
        int index = -1;
        while (matcher.find()) {
            if (index == -1) {
                index = matcher.start();
                continue;
            }

            String found = tac.substring(index, matcher.start());
            messages.add(new TAFTacMessage(found));
            index = matcher.start();
        }

        if (index >= 0) {
            String found = tac.substring(index);
            messages.add(new TAFTacMessage(found));
        }

        // Parsing all messages
        for (TAFTacMessage message : this.messages) {
            try {
                message.parseMessage();
            } catch (TAFParsingException e) {
                throw new TAFParsingException(e.getMessage() + " in [" + CoreUtil.truncateString(this.getInitialTacString(), 50) + "]");
            }
        }
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
        return "FT";
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.TAF;
    }

    @Override
    public MessageStatusType getMessageStatusType() {
        return MessageStatusType.NORMAL;
    }

    public String getIdentifier() {
        return String.format("A_%s%s%s%s%s%s_C_%s_%s.xml",
                this.messageType,
                this.areaIndicator,
                this.number,
                this.locationIndicator,
                this.time,
                this.group == null ? "" : this.group,
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
    public List<TAFTacMessage> getMessages() {
        return messages;
    }

    //</editor-fold>
}


