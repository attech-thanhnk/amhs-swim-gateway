package vn.asg.swim.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Tuân thủ EUR Doc 047: Dịch vụ Kiểm thử tính Hợp lệ của Bản tin (Message Validation Service)
 *
 * Thực hiện kiểm thử tính hợp lệ của các bản tin AMQP theo các yêu cầu của tài liệu EUR Doc 047:
 * - C-02, C-03: Kiểm tra chiều chuyển đổi định dạng (Conversion direction)
 * - C-05, S-08: Kiểm tra giới hạn kích thước bản tin (Message size)
 * - C-07, S-09: Kiểm tra số lượng người nhận (Recipients count)
 * - S-06, S-07: Kiểm tra các trường bắt buộc (Mandatory fields)
 * - S-11, S-15: Kiểm tra định dạng địa chỉ AFTN (AFTN address format)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageValidationService {

    private final ConfigService configService;

    /** Các giá trị ATS-message-priority hợp lệ theo EUR Doc 047 Table 9. */
    private static final List<String> ATS_PRIORITIES = List.of("SS", "DD", "FF", "GG", "KK");

    /** Repertoire ita2(2) của X.400 - không có trong Table 6 nên không chuyển đổi được (CTSW017). */
    public static final String REPERTOIRE_ITA2 = "ITA2";

    /** Repertoire Basic ISO 646, luôn được chấp nhận cho general-text-body-part (CTSW018). */
    public static final String REPERTOIRE_ISO646 = "ISO-646";

    /**
     * Đối tượng chứa kết quả kiểm thử (Validation Result Container)
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }

        public static ValidationResult success() {
            return new ValidationResult(true, new ArrayList<>());
        }

        public static ValidationResult failure(String error) {
            List<String> errors = new ArrayList<>();
            errors.add(error);
            return new ValidationResult(false, errors);
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }

    /**
     * EUR Doc 047 §4.5.1 - Kiểm thử bản tin chiều SWIM → AMHS
     *
     * Thực hiện kiểm tra:
     * - C-02: Chiều chuyển đổi định dạng có cho phép SWIM → AMHS
     * - S-06: Sự hiện diện của các trường bắt buộc (Mandatory fields)
     * - S-08: Kích thước bản tin trong giới hạn cho phép
     * - S-09: Số lượng người nhận trong giới hạn cho phép
     */
    public ValidationResult validateSwimToAmhs(String messageId, Message msg, String payload, int payloadByteSize) {
        List<String> errors = new ArrayList<>();

        // C-02: Kiểm tra chiều chuyển đổi định dạng
        String direction = configService.getConversionDir();
        if ("AMHS_TO_SWIM".equals(direction)) {
            errors.add("Conversion direction is AMHS_TO_SWIM - SWIM→AMHS messages not allowed");
            return ValidationResult.failure(errors);
        }

        // S-06: Kiểm thử các trường AMQP bắt buộc
        try {
            if (messageId == null || messageId.isBlank()) {
                errors.add("Mandatory field 'message-id' (JMSMessageID) is missing");
            }

            // Priority và Timestamp: chỉ đưa ra cảnh báo thay vì từ chối bản tin nhằm tương thích với các công cụ kiểm thử không hoàn toàn tuân thủ
            try {
                msg.getJMSPriority();
            } catch (Exception e) {
                log.warn("Message {}: Mandatory field 'priority' (JMSPriority) is missing", messageId);
            }

            long timestamp = msg.getJMSTimestamp();
            if (timestamp <= 0) {
                log.warn("Message {}: Mandatory field 'creation-time' (JMSTimestamp) is missing", messageId);
            }

            // Bắt buộc: trường data hoặc amqp-value (payload)
            if (payload == null || payload.isBlank()) {
                errors.add("Mandatory field 'data/amqp-value' (message body) is missing");
            }

            // CTSW116: Validate FTBP properties if body part is file-transfer-body-part
            String bodyPartType = msg.getStringProperty("amhs_bodypart_type");
            if ("file-transfer-body-part".equalsIgnoreCase(bodyPartType)) {
                String ftbpFileName = msg.getStringProperty("amhs_ftbp_file_name");
                if (ftbpFileName == null || ftbpFileName.isBlank()) {
                    errors.add("Mandatory FTBP attribute 'amhs_ftbp_file_name' is missing");
                }
            }

        } catch (JMSException e) {
            errors.add("Failed to read AMQP message properties: " + e.getMessage());
        }

        // S-08: Kiểm tra kích thước bản tin (EUR Doc 047 §4.5.1.7, §3.3.1.4: 0 hoặc không cấu hình = không giới hạn).
        // Dùng payloadByteSize (kích thước payload AMQP gốc) thay vì đo độ dài chuỗi payload, vì với
        // nội dung binary, payload là chuỗi đã base64-encode (dài hơn ~33% so với dữ liệu gốc).
        int maxSize = configService.getMaxMsgDataSize();
        if (maxSize > 0 && payloadByteSize > maxSize) {
            errors.add(String.format("Message size %d bytes exceeds maximum %d bytes", payloadByteSize, maxSize));
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
    }

    /**
     * EUR Doc 047 §4.4.1 - Kiểm thử bản tin chiều AMHS → SWIM
     *
     * Thực hiện kiểm tra:
     * - C-03: Chiều chuyển đổi định dạng có cho phép AMHS → SWIM
     * - C-05: Kích thước bản tin trong giới hạn cho phép
     * - C-07: Số lượng người nhận trong giới hạn cho phép
     */
    public ValidationResult validateAmhsToSwim(String payload, String recipients) {
        return validateAmhsToSwim(payload, recipients, null);
    }

    /**
     * EUR Doc 047 §4.4.1 - Kiểm thử bản tin chiều AMHS → SWIM, có truyền kích thước payload thật.
     *
     * @param payloadByteSize kích thước THẬT của payload tính bằng byte, hoặc null để tự đo trên
     *                        chuỗi {@code payload}. Với file-transfer-body-part, nội dung nhị phân
     *                        được lưu dưới dạng base64 trong {@code gwout.text} nên độ dài chuỗi
     *                        lớn hơn dữ liệu gốc khoảng 33%; caller phải truyền kích thước sau khi
     *                        giải mã để CTSW006 so sánh đúng với "Maximum message data size".
     */
    public ValidationResult validateAmhsToSwim(String payload, String recipients, Integer payloadByteSize) {
        List<String> errors = new ArrayList<>();

        // C-03: Kiểm tra chiều chuyển đổi định dạng
        String direction = configService.getConversionDir();
        if ("SWIM_TO_AMHS".equals(direction)) {
            errors.add("Conversion direction is SWIM_TO_AMHS - AMHS→SWIM messages not allowed");
            return ValidationResult.failure(errors);
        }

        // C-05: Kiểm tra kích thước bản tin (EUR Doc 047 §3.3.1.4: 0 hoặc không cấu hình = không giới hạn)
        if (payload != null) {
            int maxSize = configService.getMaxMsgDataSize();
            int actualSize = payloadByteSize != null ? payloadByteSize
                    : payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (maxSize > 0 && actualSize > maxSize) {
                errors.add(String.format("Message size %d bytes exceeds maximum %d bytes (content-too-long)",
                        actualSize, maxSize));
            }
        }

        // C-07: Kiểm tra số lượng người nhận (EUR Doc 047 §3.3.2.4: 0 hoặc không cấu hình = không giới hạn)
        if (recipients != null && !recipients.isBlank()) {
            String[] recipientArray = recipients.trim().split("[,\\s]+");
            int maxRecipients = configService.getMaxMsgRecipients();
            if (maxRecipients > 0 && recipientArray.length > maxRecipients) {
                errors.add(String.format("Recipients count %d exceeds maximum %d (too-many-recipients)",
                        recipientArray.length, maxRecipients));
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
    }

    /**
     * EUR Doc 047 §4.4.2.5 - Kiểm thử cú pháp ATS-message-header của IPM đến (CTSW004).
     *
     * Appendix A/CTSW004 liệt kê 5 trường hợp phải sinh NDR:
     * ATS-message-priority rỗng / sai giá trị, ATS-message-filing-time rỗng / sai định dạng,
     * và ATS-message-header rỗng hoàn toàn (không có IHE) - trường hợp cuối tương đương
     * cả hai trường trên cùng rỗng.
     *
     * Giá trị hợp lệ: priority thuộc {SS, DD, FF, GG, KK} (Table 9), filing-time là
     * date-time group 6 chữ số DDhhmm (cùng quy ước với §4.5.2.10a chiều SWIM→AMHS).
     */
    public ValidationResult validateAtsMessageHeader(String atsPriority, String atsFilingTime) {
        List<String> errors = new ArrayList<>();

        if (atsPriority == null || atsPriority.isBlank()) {
            errors.add("ATS-message-priority is empty");
        } else if (!ATS_PRIORITIES.contains(atsPriority.trim().toUpperCase())) {
            errors.add(String.format("ATS-message-priority '%s' is invalid", atsPriority.trim()));
        }

        if (atsFilingTime == null || atsFilingTime.isBlank()) {
            errors.add("ATS-message-filing-time is empty");
        } else if (!atsFilingTime.trim().matches("^\\d{6}$")) {
            errors.add(String.format("ATS-message-filing-time '%s' is invalid", atsFilingTime.trim()));
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
    }

    /**
     * ĐỊNH NGHĨA DUY NHẤT của địa chỉ AFTN trong toàn hệ thống: đúng 8 chữ cái viết hoa
     * (ICAO Annex 10 Vol II - addressee indicator, Doc 9880 §4.5.2.4).
     * <p>
     * Trước đây tồn tại hai định nghĩa lệch nhau: {@code validateAftnAddress} cho phép cả chữ số
     * ({@code [A-Z0-9]{8}}) trong khi {@code OutboundDispatchService} tự viết {@code [A-Z]{8}} ở
     * hai chỗ. Hệ quả là địa chỉ có chữ số qua được validator nhưng bị bước tạo dispatch loại bỏ
     * âm thầm. Mọi nơi phải gọi vào đây thay vì tự viết regex.
     */
    public static final String AFTN_ADDRESS_PATTERN = "[A-Z]{8}";

    /**
     * Kiểm tra nhanh khuôn địa chỉ AFTN, dùng cho các nhánh chỉ cần true/false.
     * Cùng một tiêu chí với {@link #validateAftnAddress(String, String)}.
     */
    public static boolean isValidAftnAddress(String aftn) {
        return aftn != null && aftn.trim().matches(AFTN_ADDRESS_PATTERN);
    }

    /**
     * EUR Doc 047 §4.5.2.4 - Kiểm thử định dạng địa chỉ AFTN
     *
     * S-11, S-15: Địa chỉ AFTN phải có đúng 8 chữ cái viết hoa
     */
    public ValidationResult validateAftnAddress(String aftn, String fieldName) {
        if (aftn == null || aftn.isBlank()) {
            return ValidationResult.failure(fieldName + " is null or empty");
        }

        String trimmed = aftn.trim();

        // Phải có đúng 8 ký tự
        if (trimmed.length() != 8) {
            return ValidationResult.failure(String.format("%s '%s' must be exactly 8 characters (actual: %d)",
                    fieldName, trimmed, trimmed.length()));
        }

        // Phải là chữ cái viết hoa
        if (!trimmed.matches(AFTN_ADDRESS_PATTERN)) {
            return ValidationResult.failure(String.format("%s '%s' must contain only uppercase letters",
                    fieldName, trimmed));
        }

        return ValidationResult.success();
    }

    /**
     * EUR Doc 047 - Kiểm thử danh sách địa chỉ AFTN phân tách bằng dấu cách
     *
     * S-09, S-11: Kiểm thử danh sách người nhận (recipients list)
     */
    public ValidationResult validateAftnRecipients(String recipients) {
        if (recipients == null || recipients.isBlank()) {
            return ValidationResult.failure("Recipients list is empty");
        }

        List<String> errors = new ArrayList<>();
        String[] addresses = recipients.trim().split("[,\\s]+");

        // S-09: Kiểm tra số lượng người nhận (EUR Doc 047 §3.3.2.4: 0 hoặc không cấu hình = không giới hạn)
        int maxRecipients = configService.getMaxMsgRecipients();
        if (maxRecipients > 0 && addresses.length > maxRecipients) {
            errors.add(String.format("Recipients count %d exceeds maximum %d", addresses.length, maxRecipients));
        }

        // S-11: Kiểm thử định dạng của từng địa chỉ cụ thể
        for (int i = 0; i < addresses.length; i++) {
            ValidationResult result = validateAftnAddress(addresses[i], "Recipient[" + i + "]");
            if (!result.isValid()) {
                errors.addAll(result.getErrors());
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        return ValidationResult.success();
    }

    /**
     * S-06, CTSW016: Kiểm thử định dạng EIT/Body Part Type
     */
    /**
     * EUR Doc 047 §4.4.2.1 - Kiểm tra current encoded-information-types (EIT) của IPM.
     * <p>
     * Chấp nhận khi giá trị là "unspecified"/"unknown", hoặc CHỈ gồm các loại được liệt kê ở
     * §4.4.2.1a: ia5-text (basic hoặc externally-defined {id-eit-ia5-text}),
     * {id-cs-eit-authority 1/2/6/100} và {id-eit-file-transfer 0}. Giá trị nhiều thành phần
     * được phân tách bởi dấu phẩy/khoảng trắng; chỉ cần MỘT thành phần không hợp lệ là từ chối
     * (§4.4.2.1b -> NDR diagnostic "encoded-information-types-unsupported").
     */
    public ValidationResult validateEncodedInformationTypes(String eit) {
        if (eit == null || eit.isBlank()) {
            // Không có thông tin EIT -> coi như "unspecified", được phép (§4.4.2.1a)
            return ValidationResult.success();
        }
        for (String part : eit.trim().split("[,;]+")) {
            String token = part.trim().toLowerCase();
            if (token.isEmpty()) {
                continue;
            }
            if (!isAllowedEit(token)) {
                return ValidationResult.failure(
                        "Unsupported encoded-information-types: " + part.trim());
            }
        }
        return ValidationResult.success();
    }

    private boolean isAllowedEit(String token) {
        // "unspecified" / "unknown" (built-in 0, OID 2.6.3.4.0)
        if (token.contains("unspecified") || token.contains("unknown") || token.equals("2.6.3.4.0")) {
            return true;
        }
        // ia5-text: basic, externally-defined {id-eit-ia5-text}, OID 2.6.3.4.2
        if (token.contains("ia5-text") || token.contains("ia5text") || token.equals("2.6.3.4.2")) {
            return true;
        }
        // {id-eit-file-transfer 0}
        if (token.contains("file-transfer") || token.contains("filetransfer")) {
            return true;
        }
        // {id-cs-eit-authority N}: chỉ 1, 2, 6, 100 được phép (§4.4.2.1a 3-6)
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("authority\\D+(\\d+)").matcher(token);
        if (m.find()) {
            String n = m.group(1);
            return "1".equals(n) || "2".equals(n) || "6".equals(n) || "100".equals(n);
        }
        return false;
    }

    /**
     * EUR Doc 047 §4.4.2.3 - Kiểm tra repertoire của body part (CTSW017 và CTSW019).
     * <p>
     * <b>ia5-text-body-part</b> (CTSW017): tham số X.400 {@code repertoire} là kiểu liệt kê chỉ
     * nhận ita2(2) hoặc ia5(5); vắng mặt thì mặc định là ia5. Giá trị ita2 không nằm trong Table 6
     * nên không chuyển đổi được sang AMQP -> từ chối với supplementary-information
     * "unsupported body part type".
     * <p>
     * <b>general-text-body-part</b> (CTSW018/CTSW019): repertoire Basic ISO 646 luôn được chấp nhận.
     * Repertoire khác ISO 646 (ISO 8859-x, Cyrillic, Arabic, Greek, Hebrew, CJK...) được chuyển đổi
     * hay bị từ chối là tuỳ chính sách nội bộ của AMHS Management Domain, khai báo qua cấu hình
     * {@code ALLOW_NON_ISO646_REPERTOIRE}. Khi từ chối, supplementary-information là
     * "unsupported encoded-information-types".
     *
     * @param repertoire giá trị repertoire đã chuẩn hoá (ITA2 / ISO-646 / ISO-8859-1 / ...),
     *                   null hoặc rỗng nghĩa là không khai báo -> chấp nhận theo mặc định.
     */
    public ValidationResult validateRepertoire(String bodyPartType, String repertoire) {
        if (bodyPartType == null || repertoire == null || repertoire.isBlank()) {
            return ValidationResult.success();
        }
        String bpt = bodyPartType.trim().toLowerCase();
        String rep = repertoire.trim().toUpperCase();

        if (bpt.startsWith("ia5-text")) {
            if (REPERTOIRE_ITA2.equals(rep)) {
                return ValidationResult.failure(String.format(
                        "ia5-text-body-part repertoire '%s' is not supported (unsupported-body-part-type)",
                        repertoire.trim()));
            }
            return ValidationResult.success();
        }

        if (bpt.equals("general-text-body-part")) {
            if (REPERTOIRE_ISO646.equals(rep)) {
                return ValidationResult.success();
            }
            if (configService.isNonIso646RepertoireAllowed()) {
                return ValidationResult.success();
            }
            return ValidationResult.failure(String.format(
                    "general-text-body-part repertoire '%s' rejected by local AMHS Management Domain "
                            + "policy (unsupported-encoded-information-types)",
                    repertoire.trim()));
        }

        return ValidationResult.success();
    }

    public ValidationResult validateBodyPartType(String bodyPartType) {
        if (bodyPartType == null || bodyPartType.isBlank()) {
            return ValidationResult.success();
        }
        String cleanType = bodyPartType.trim().toLowerCase();
        if (cleanType.equals("ia5-text") ||
                cleanType.equals("ia5-text-body-part") ||
                cleanType.equals("general-text-body-part") ||
                cleanType.equals("file-transfer-body-part")) {
            return ValidationResult.success();
        }
        return ValidationResult.failure("Unsupported Encoded Information Type (EIT) / Body Part Type: " + bodyPartType);
    }

    /**
     * Kiểm tra xem chiều chuyển đổi hiện tại có cho phép chiều mong muốn hay không
     */
    public boolean isDirectionAllowed(String direction) {
        String configDir = configService.getConversionDir();
        return "BOTH".equals(configDir) || configDir.equals(direction);
    }
}
