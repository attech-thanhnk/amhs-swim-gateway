package vn.asg.converter.reverter.iwxxm;

import java.util.regex.Pattern;

/**
 * @author ThanhNk
 */
public class Regex {
    public static final Pattern metarBulletin = Pattern
            .compile("^A_(?<indicator>[A-Z0-9]{6})(?<icaoDesignator>[A-Z]{4})(?<time>[0-9]{6}).*");
}

