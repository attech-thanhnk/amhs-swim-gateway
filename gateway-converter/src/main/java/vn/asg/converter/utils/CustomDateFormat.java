/*
 */
package vn.asg.converter.utils;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author ThanhNk
 */
public class CustomDateFormat extends DateFormat {

    private static final String FORMAT1 = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String FORMAT2 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private final SimpleDateFormat sdf1 = new SimpleDateFormat(FORMAT1);
    private final SimpleDateFormat sdf2 = new SimpleDateFormat(FORMAT2);

    @Override
    public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Date parse(String source, ParsePosition pos) {
        if (source.length() - pos.getIndex() + 4 == FORMAT1.length()) {
            return sdf1.parse(source, pos);
        }
        return sdf2.parse(source, pos);
    }

}

