/*
 */
package vn.asg.converter.common;

import java.util.regex.Pattern;

/**
 *
 * @author ThanhNk
 */
public class CommonRegex {
    
    // METAR BULLETIN
    public static final Pattern metarBulletin = Pattern
            .compile("(?<type>^SA)(?<areaIndicator>[A-Z]{2})(?<no>\\d{2})\\s+(?<locationIndicator>[A-Z]{4})\\s+(?<datetime>\\d{6})(\\s+(?<group>[A-Z]{3}(?:(?!METAR).))\\s+)?");
    
    // METAR
    public static final Pattern metarToken = Pattern
            .compile("(?<metar>METAR\\s+(?!METAR).*)");
    
    // SPECI BULLETIN
    public static final Pattern speciBulletin = Pattern
            .compile("(?<type>^SP)(?<areaIndicator>[A-Z]{2})(?<no>\\d{2})\\s+(?<locationIndicator>[A-Z]{4})\\s+(?<datetime>\\d{6})(\\s+(?<group>[A-Z]{3}(?:(?!SPECI).))\\s+)?");
    
    // SPECI
    public static final Pattern speciToken = Pattern
            .compile("(?<metar>SPECI\\s+(?!SPECI).*)");
    
    // TAF BULLETIN
    public static final Pattern tafBulletin = Pattern
            // .compile("(?<type>^FT|FC)(?<areaIndicator>[A-Z]{2})(?<no>\\d{2})\\s+(?<locationIndicator>[A-Z]{4})\\s+(?<datetime>\\d{6})(\\s+(?<group>[A-Z]{3})\\s+)?");
            // .compile("(?<type>^FT|FC)(?<areaIndicator>[A-Z]{2})(?<no>\\d{2})\\s+(?<locationIndicator>[A-Z]{4})\\s+(?<datetime>\\d{6})(\\s+(?<group>[A-Z]{3})\\s+)?(?!TAF).*");
            .compile("(?<type>^FT|FC)(?<areaIndicator>[A-Z]{2})(?<no>\\d{2})\\s+(?<locationIndicator>[A-Z]{4})\\s+(?<datetime>\\d{6})(\\s+(?<group>[A-Z]{3}(?:(?!TAF).))\\s+)?");
    
    
    // TAF
    public static final Pattern tafToken = Pattern
            .compile("(?<metar>TAF\\s+(?!TAF).*)");
    
    // SIGMET BULLETIN
    public static final Pattern sigmetBulletin = Pattern
            .compile("(?<type>^WS|WC|WV)(?<areaIndicator>[A-Z]{2})(?<no>\\d{2})\\s+(?<locationIndicator>[A-Z]{4})\\s+(?<datetime>\\d{6})(\\s+(?<group>[A-Z]{3})\\s+)?");
            // .compile("(?<type>^WS|WC|WV)(?<areaIndicator>[A-Z]{2})(?<no>\\d{2})\\s+(?<locationIndicator>[A-Z]{4})\\s+(?<datetime>\\d{6})(\\s+(?<group>[A-Z]{3}(?:(?!SIGMET).))\\s+)?");
    
    // SIGMET
    public static final Pattern sigmetToken = Pattern
            .compile("(?<icao>[A-Z]{4})\\s+(?<isSigmet>SIGMET)");
    
}

