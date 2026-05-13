package vn.asg.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.asg.converter.builder.JsonBuilder;
import vn.asg.converter.builder.TextRenderer;
import vn.asg.converter.core.ConversionResult;
import vn.asg.converter.core.OutputFormat;
import vn.asg.converter.core.TacPreprocessor;
import vn.asg.converter.model.BaseMessage;
import vn.asg.converter.model.flight.*;
import vn.asg.converter.model.weather.*;
import vn.asg.converter.model.notam.*;
import vn.asg.converter.parser.MessageParser;
import vn.asg.converter.parser.flight.FplParser;
import vn.asg.converter.parser.weather.*;
import vn.asg.converter.parser.notam.NotamParser;
import vn.asg.converter.parser.flight.AlrParser;
import vn.asg.converter.parser.flight.SplParser;
import vn.asg.converter.parser.flight.CoordinationParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;

/**
 * Bộ điều phối chuyển đổi bản tin TAC sang JSON và TEXT.
 */
public class ConverterFacade {

    private static final Logger log = LoggerFactory.getLogger(ConverterFacade.class);
    
    private final TacPreprocessor preprocessor = new TacPreprocessor();
    private final JsonBuilder jsonBuilder = new JsonBuilder();
    private final TextRenderer textRenderer = new TextRenderer();
    private final ObjectMapper objectMapper;

    public ConverterFacade() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.registerModule(new JodaModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Chuyển đổi mặc định sang JSON.
     */
    public ConversionResult convert(String rawTac, String messageType) {
        return convert(rawTac, messageType, OutputFormat.JSON);
    }

    /**
     * Chuyển đổi điện văn sang định dạng yêu cầu.
     */
    public ConversionResult convert(String rawTac, String messageType, OutputFormat format) {
        if (rawTac == null || rawTac.isBlank()) {
            return ConversionResult.parseError("Input is empty", rawTac);
        }

        try {
            TacPreprocessor.AftnEnvelope env = preprocessor.unwrap(rawTac);
            
            BaseMessage model;
            if (env.body.trim().startsWith("{")) {
                Class<? extends BaseMessage> modelClass = getModelClass(messageType);
                if (modelClass == null) throw new Exception("No model class mapping for type: " + messageType);
                model = objectMapper.readValue(env.body, modelClass);
            } else {
                MessageParser<? extends BaseMessage> parser = getParser(messageType);
                if (parser == null) return ConversionResult.unsupported(env.body);
                model = parser.parse(env.body);
            }

            enrichMetadata(model, env, messageType);

            String payload = switch (format) {
                case JSON -> jsonBuilder.build(model);
                case TEXT -> textRenderer.render(model);
            };

            return ConversionResult.success(payload, format.name(), model.getMessageId());

        } catch (Exception e) {
            log.error("Conversion failed for {}: {}", messageType, e.getMessage());
            return ConversionResult.systemError(e.getMessage(), rawTac);
        }
    }

    /**
     * Lấy bộ Parser tương ứng với loại bản tin.
     */
    private MessageParser<? extends BaseMessage> getParser(String type) {
        if (type == null) return null;
        String baseType = type.toUpperCase().replace("_TEXT", "");
        
        return switch (baseType) {
            case "FPL", "CHG", "CNL", "DEP", "ARR", "DLA", "RQP", "RQS" -> new FplParser();
            case "ALR" -> new AlrParser();
            case "SPL" -> new SplParser();
            case "EST", "CDN", "ACP", "CPL" -> new CoordinationParser();
            case "NOTAM" -> new NotamParser();
            case "METAR", "SPECI" -> new MetarParser();
            case "TAF" -> new TafParser();
            case "SIGMET", "AIRMET", "GAMET", "VAA", "TCA" -> new SigmetParser();
            case "ARS", "ARP" -> new AirepParser();
            case "ASHTAM", "SNOWTAM", "SYNOP", "RQM", "AFP", "RCF" -> null; // Parsers not implemented yet, rely on JSON flow
            default -> null;
        };
    }

    /**
     * Lấy Class Model tương ứng để deserialize JSON.
     */
    private Class<? extends BaseMessage> getModelClass(String type) {
        if (type == null) return null;
        String baseType = type.toUpperCase().replace("_TEXT", "");
        return switch (baseType) {
            case "FPL", "CHG", "CNL", "DEP", "ARR", "DLA", "RQP", "RQS" -> FplMessage.class;
            case "NOTAM", "NOTAMN", "NOTAMR", "NOTAMC" -> NotamMessage.class;
            case "METAR", "SPECI" -> MetarMessage.class;
            case "TAF" -> TafMessage.class;
            case "SIGMET", "AIRMET", "GAMET", "VAA", "TCA" -> SigmetMessage.class;
            case "ALR", "SPL", "EST", "CDN", "ACP", "CPL", "AFP", "RCF" -> FplMessage.class;
            case "ARS", "ARP" -> AirepMessage.class;
            case "SNOWTAM" -> SnowtamMessage.class;
            case "ASHTAM" -> AshtamMessage.class;
            case "SYNOP", "RQM" -> SynopMessage.class;
            default -> null;
        };
    }

    /**
     * Bổ sung thông tin từ phong bì AFTN vào Model.
     */
    private void enrichMetadata(BaseMessage model, TacPreprocessor.AftnEnvelope env, String messageType) {
        model.setOriginator(env.originator);
        model.setRecipients(env.recipients);
        model.setPriority(env.priority);
        model.setFilingTime(env.filingTime);
        if (model.getMessageType() == null || model.getMessageType().isEmpty()) {
            model.setMessageType(messageType);
        }
        
        // Tạo Message ID nếu chưa có
        if (model.getMessageId() == null) {
            model.setMessageId(messageType + "_" + System.currentTimeMillis());
        }
    }
}
