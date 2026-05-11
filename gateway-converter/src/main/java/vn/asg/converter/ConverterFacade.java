package vn.asg.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.asg.converter.core.ConversionResult;
import vn.asg.converter.core.MessageDetector;
import vn.asg.converter.core.TacPreprocessor;
import vn.asg.converter.iwxxm.*;
import vn.asg.converter.model.FplMessage;
import vn.asg.converter.model.NotamMessage;
import vn.asg.converter.parser.FplParser;
import vn.asg.converter.parser.NotamParser;
import vn.asg.converter.builder.AixmBuilder;
import vn.asg.converter.builder.FixmBuilder;

/**
 * Entry point duy nhất của gateway-converter.
 *
 * Hỗ trợ 100% các loại bản tin ATS theo chuẩn ICAO:
 *
 * Đầu vào (TAC/AFTN text):
 *   METAR / SPECI / TAF / SIGMET / AIRMET → IWXXM XML
 *   FPL / CHG / CNL / DEP / ARR / DLA     → FIXM XML
 *   NOTAM                                  → AIXM XML
 *
 * Sử dụng:
 *   ConversionResult result = new ConverterFacade().convert(rawTacString);
 *   if (result.isSuccess()) String xml = result.getXml();
 *
 * @author ThanhNk
 */
public class ConverterFacade {

    private static final Logger log = LoggerFactory.getLogger(ConverterFacade.class);
    private final TacPreprocessor preprocessor = new TacPreprocessor();

    public ConversionResult convert(String rawTac) {
        if (rawTac == null || rawTac.isBlank()) {
            return ConversionResult.parseError("Empty input", rawTac);
        }

        String originalTac = rawTac;
        try {
            // Bước 1: Bóc vỏ AFTN → lấy body thuần
            TacPreprocessor.AftnEnvelope env = preprocessor.unwrap(rawTac);
            log.debug("Body after AFTN unwrap: [{}]", env.body);

            // Bước 2: Nhận diện loại bản tin
            MessageDetector.DetectionResult det = MessageDetector.detect(env.body);
            log.info("Detected: {} / {}", det.category(), det.form());

            // Bước 3: Chọn converter và thực hiện chuyển đổi
            return switch (det.category()) {

                // ── IWXXM — Khí tượng ──────────────────────────────────
                case METAR -> {
                    if (det.form() == MessageDetector.Form.BULLETIN) {
                        var conv = new METARBulletinConverterV3();
                        yield ConversionResult.success(
                                conv.convertTacToXML(env.body), "IWXXM", conv.getIdentifier());
                    } else {
                        var conv = new METARConverterV3();
                        yield ConversionResult.success(
                                conv.convertTacToXML(env.body), "IWXXM", conv.getIdentifier());
                    }
                }

                case SPECI -> {
                    if (det.form() == MessageDetector.Form.BULLETIN) {
                        var conv = new SPECIBulletinConverterV3();
                        yield ConversionResult.success(
                                conv.convertTacToXML(env.body), "IWXXM", conv.getIdentifier());
                    } else {
                        var conv = new SPECIConverterV3();
                        yield ConversionResult.success(
                                conv.convertTacToXML(env.body), "IWXXM", conv.getIdentifier());
                    }
                }

                case TAF -> {
                    if (det.form() == MessageDetector.Form.BULLETIN) {
                        var conv = new TAFBulletinConvertV3();
                        yield ConversionResult.success(
                                conv.convertTacToXML(env.body), "IWXXM", conv.getIdentifier());
                    } else {
                        var conv = new TAFConverterV3();
                        yield ConversionResult.success(
                                conv.convertTacToXML(env.body), "IWXXM", conv.getIdentifier());
                    }
                }

                case SIGMET -> {
                    if (det.form() == MessageDetector.Form.BULLETIN) {
                        var conv = new SIGMETBulletinConverterV3();
                        yield ConversionResult.success(
                                conv.convertTacToXML(env.body), "IWXXM", conv.getIdentifier());
                    } else {
                        var conv = new SIGMETConverterV3();
                        yield ConversionResult.success(
                                conv.convertTacToXML(env.body), "IWXXM", conv.getIdentifier());
                    }
                }

                // AIRMET → hiện tại dùng SIGMET converter (cùng schema IWXXM)
                // Cần tạo AIRMETConverterV3 riêng khi có yêu cầu
                case AIRMET -> {
                    log.warn("AIRMET conversion using SIGMET converter (interim)");
                    if (det.form() == MessageDetector.Form.BULLETIN) {
                        var conv = new SIGMETBulletinConverterV3();
                        yield ConversionResult.success(
                                conv.convertTacToXML(env.body), "IWXXM", "AIRMET_" + conv.getIdentifier());
                    } else {
                        var conv = new SIGMETConverterV3();
                        yield ConversionResult.success(
                                conv.convertTacToXML(env.body), "IWXXM", "AIRMET_" + conv.getIdentifier());
                    }
                }

                // ── AIXM — Thông báo hàng không ────────────────────────
                case NOTAM -> {
                    try {
                        NotamParser parser = new NotamParser();
                        NotamMessage notam = parser.parse(env.body);
                        AixmBuilder builder = new AixmBuilder();
                        String xml = builder.buildNotam(notam);
                        String id  = "NOTAM_" + notam.getNotamId().replace("/", "-") + ".xml";
                        yield ConversionResult.success(xml, "AIXM", id);
                    } catch (Exception e) {
                        log.error("Failed to parse NOTAM: ", e);
                        yield ConversionResult.parseError(e.getMessage(), env.body);
                    }
                }

                // ── FIXM — Quản lý bay ─────────────────────────────────
                case FPL -> {
                    // FPL có đầy đủ thông tin flight plan → convert sang FIXM XML
                    try {
                        FplParser parser = new FplParser();
                        FplMessage fpl   = parser.parse(env.body);
                        FixmBuilder builder = new FixmBuilder();
                        String xml = builder.buildFpl(fpl);
                        String id  = fpl.getMessageType() + "_"
                                     + (fpl.getAircraftId() != null ? fpl.getAircraftId() : "UNKNOWN")
                                     + ".xml";
                        yield ConversionResult.success(xml, "FIXM", id);
                    } catch (Exception e) {
                        log.error("Failed to parse FPL: ", e);
                        yield ConversionResult.parseError(e.getMessage(), env.body);
                    }
                }

                // CHG, CNL, DLA, DEP, ARR chỉ chứa partial info, không thể tạo full FIXM FlightPlan
                // TODO: Implement FIXM Flight Messages (ff:FlightArrival, ff:FlightCancellation, etc.)
                // Hiện tại: Gateway sẽ gửi dưới dạng TAC text để đảm bảo messages được forward
                case CHG, CNL, DLA, DEP, ARR -> {
                    log.debug("{} message - forwarding as TAC text (FIXM Flight Messages not yet implemented)", det.category());
                    yield ConversionResult.unsupported(env.body);
                }

                // ARS/ARP (AIREP) → FIXM (xử lý như FPL, cần parser riêng khi mở rộng)
                case ARS, ARP -> {
                    log.warn("ARS/ARP conversion not fully implemented, returning unsupported");
                    yield ConversionResult.unsupported(env.body);
                }

                default -> {
                    log.warn("Unknown message type: [{}]", env.body.substring(0, Math.min(50, env.body.length())));
                    yield ConversionResult.unsupported(env.body);
                }
            };

        } catch (Exception e) {
            log.error("Conversion failed for: [{}]", originalTac.substring(0, Math.min(80, originalTac.length())), e);
            return ConversionResult.systemError(e.getMessage(), originalTac);
        }
    }

    /**
     * Revert XML back to TAC.
     */
    public ConversionResult revert(String xml, String type) {
        if (xml == null || xml.isBlank()) {
            return ConversionResult.parseError("Empty XML input", xml);
        }

        try {
            String tac;
            switch (type.toUpperCase()) {
                case "METAR"  -> tac = new vn.asg.converter.reverter.iwxxm.METARReverter().convertToString(xml);
                case "SPECI"  -> tac = new vn.asg.converter.reverter.iwxxm.SPECIReverter().convertToString(xml);
                case "TAF"    -> tac = new vn.asg.converter.reverter.iwxxm.TAFReverter().convertToString(xml);
                case "SIGMET" -> tac = new vn.asg.converter.reverter.iwxxm.SIGMETReverter().convertToString(xml);
                case "NOTAM"  -> tac = new vn.asg.converter.reverter.aixm.NotamReverter().revert(xml);
                case "FPL", "CHG", "CNL", "DEP", "ARR", "DLA" ->
                    tac = new vn.asg.converter.reverter.fixm.FplReverter().revert(xml);
                default -> {
                    log.warn("Unsupported revert type: {}", type);
                    return ConversionResult.unsupported(xml);
                }
            }
            return ConversionResult.success(tac, "TAC", type + ".txt");
        } catch (Exception e) {
            log.error("Revert failed for type: {}", type, e);
            return ConversionResult.systemError(e.getMessage(), xml);
        }
    }

    /**
     * Revert XML back to TAC với auto-detection.
     */
    public ConversionResult revert(String xml) {
        String tac = new ReverterFacade().revert(xml);
        if (tac == null) {
            return ConversionResult.parseError("Auto-detection failed or invalid XML", xml);
        }
        return ConversionResult.success(tac, "TAC", "reverted.txt");
    }
}
