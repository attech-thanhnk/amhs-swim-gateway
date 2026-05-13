package vn.asg.converter.parser.weather;

import vn.asg.converter.model.weather.TafMessage;
import vn.asg.converter.parser.MessageParser;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc dữ liệu bản tin TAF.
 */
public class TafParser implements MessageParser<TafMessage> {

    private static final Pattern TAF_HEADER = 
        Pattern.compile("^TAF\\s+(?:AMD\\s+|COR\\s+)?([A-Z]{4})\\s+(\\d{6}Z)?\\s*(\\d{4}/\\d{4})");

    @Override
    public TafMessage parse(String body) throws Exception {
        if (body == null || body.isBlank()) {
            throw new Exception("Empty TAF body");
        }

        String raw = body.trim().replaceAll("\\s+", " ");
        TafMessage msg = new TafMessage();
        msg.setMessageType("TAF");
        msg.setOriginalTac(body);

        Matcher m = TAF_HEADER.matcher(raw);
        if (m.find()) {
            msg.setStationIcao(m.group(1));
            msg.setIssueTime(m.group(2));
            msg.setValidity(m.group(3));
        }

        // Tách các trend BECMG, TEMPO
        String[] trends = raw.split("(?=BECMG|TEMPO|PROB)");
        if (trends.length > 0) {
            msg.setBaseConditions(trends[0].trim());
            msg.setTrends(new ArrayList<>());
            for (int i = 1; i < trends.length; i++) {
                String trendPart = trends[i].trim();
                TafMessage.TafTrend trend = new TafMessage.TafTrend();
                String[] parts = trendPart.split(" ", 3);
                if (parts.length >= 2) {
                    trend.setType(parts[0]);
                    trend.setPeriod(parts[1]);
                    if (parts.length > 2) trend.setConditions(parts[2]);
                }
                msg.getTrends().add(trend);
            }
        }

        return msg;
    }
}
