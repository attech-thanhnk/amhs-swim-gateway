package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EUR Doc 047 §3.3.3 - Lựa chọn Cấp độ Dịch vụ ATSMHS (ATSMHS Service Level
 * Selection)
 *
 * Xác định xem nên sử dụng cấp độ dịch vụ Extended hay Basic ATSMHS
 * khi chuyển đổi bản tin SWIM sang AMHS IPM.
 *
 * Các chế độ Cấp độ Dịch vụ (C-08):
 * - EXTENDED: Luôn sử dụng extended ATSMHS (hỗ trợ nội dung nhị phân/binary)
 * - BASIC: Luôn sử dụng basic ATSMHS (chỉ hỗ trợ văn bản/text, từ chối binary)
 * - CONTENT_BASED: Quyết định dựa theo content-type
 * - RECIPIENTS_BASED: Quyết định dựa theo khả năng hỗ trợ của người nhận
 * (recipients)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtsmhsServiceLevelResolver {

    private final ConfigService configService;

    public static final String EXTENDED = "EXTENDED";
    public static final String BASIC = "BASIC";

    /**
     * EUR Doc 047 §3.3.3.1-5: Phân giải cấp độ dịch vụ ATSMHS.
     * <p>
     * Chế độ CHỈ đọc từ {@code gateway_config.ATSMHS_SERVICE_LEVEL}. Bản tin AMQP không được
     * phép chỉ định mức dịch vụ:
     * <ul>
     *   <li>§3.3.3 xếp bốn chế độ vào nhóm yêu cầu C-08…C-12 — cấu hình của ITCU, không phải
     *       thuộc tính bản tin. Table 2 (§4.5.2) không định nghĩa property nào cho việc này.</li>
     *   <li>Mức dịch vụ là <b>năng lực của miền AMHS nhận</b>; bên gửi SWIM không có cách nào
     *       biết điều đó. Ảnh hưởng hợp lệ của bên gửi đã đi qua {@code content-type} ở chế độ
     *       CONTENT_BASED — một property thuộc chuẩn.</li>
     *   <li>Cho bên gửi tự đặt EXTENDED sẽ vô hiệu hoá chốt an toàn §3.3.3.2 (BASIC không chở
     *       được nội dung nhị phân) và đẩy lỗi xuống hạ nguồn.</li>
     * </ul>
     * Trước đây {@code AMQPSubscriberService} đọc property {@code atsmhs_service_level} khỏi bản
     * tin làm giá trị ghi đè; đã bỏ.
     *
     * @param contentType AMQP content-type header
     * @param recipients  Danh sách địa chỉ AFTN phân tách bằng dấu cách
     * @return EXTENDED hoặc BASIC
     */
    public String resolve(String contentType, String recipients) {
        String mode = configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL);
        if (mode == null || mode.isBlank()) {
            mode = "CONTENT_BASED";
        }

        return switch (mode.toUpperCase()) {
            case "EXTENDED" -> {
                log.debug("ATSMHS: mode=EXTENDED → EXTENDED");
                yield EXTENDED;
            }
            case "BASIC" -> {
                log.debug("ATSMHS: mode=BASIC → BASIC");
                yield BASIC;
            }
            case "CONTENT_BASED", "CONTENT-BASED" -> resolveByContent(contentType);
            case "RECIPIENTS_BASED", "RECIPIENT-BASED" -> resolveByRecipients(recipients);
            default -> {
                log.warn("ATSMHS: unknown mode '{}', defaulting to CONTENT_BASED", mode);
                yield resolveByContent(contentType);
            }
        };
    }

    /**
     * EUR Doc 047 §3.3.3.3 - Chế độ Content-based (C-11)
     *
     * Nội dung nhị phân (application/octet-stream) → EXTENDED
     * Nội dung văn bản (text) → BASIC
     */
    private String resolveByContent(String contentType) {
        if (contentType != null && contentType.toLowerCase().contains("octet-stream")) {
            log.debug("ATSMHS: content-based → EXTENDED (binary content)");
            return EXTENDED;
        }
        log.debug("ATSMHS: content-based → BASIC (text content)");
        return BASIC;
    }

    /**
     * EUR Doc 047 §3.3.3.4 - Chế độ Recipients-based (C-12)
     *
     * Nếu TẤT CẢ người nhận hỗ trợ extended ATSMHS → EXTENDED
     * Ngược lại → BASIC
     */
    private String resolveByRecipients(String recipients) {
        if (recipients == null || recipients.isBlank()) {
            log.debug("ATSMHS: recipients-based → BASIC (no recipients)");
            return BASIC;
        }

        // For now, check if recipients are in known extended-capable list
        String extendedCapableAddresses = configService.get("ATSMHS_EXTENDED_CAPABLE_ADDRESSES");

        if (extendedCapableAddresses.isBlank()) {
            // EUR Doc 047 §3.3.3.6: chỉ map extended khi TẤT CẢ người nhận hỗ trợ extended;
            // "Otherwise" -> basic. Danh sách rỗng = không có bằng chứng nào, nên phải là BASIC.
            log.warn("ATSMHS: ATSMHS_EXTENDED_CAPABLE_ADDRESSES chưa được cấu hình - "
                    + "recipients-based → BASIC (§3.3.3.6)");
            return BASIC;
        }

        String[] recipientArray = recipients.trim().split("\\s+");
        for (String recipient : recipientArray) {
            if (!containsExact(extendedCapableAddresses, recipient)) {
                log.debug("ATSMHS: recipients-based → BASIC (recipient {} not extended-capable)", recipient);
                return BASIC;
            }
        }

        log.debug("ATSMHS: recipients-based → EXTENDED (all recipients capable)");
        return EXTENDED;
    }

    private boolean containsExact(String configValue, String target) {
        if (configValue == null || configValue.isBlank() || target == null || target.isBlank()) {
            return false;
        }
        String[] items = configValue.split("[,;\\s]+");
        for (String item : items) {
            if (item.trim().equalsIgnoreCase(target.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * EUR Doc 047 §3.3.3.2 - Kiểm tra tính hợp lệ của nội dung theo cấp độ dịch vụ
     * (C-10)
     *
     * Chế độ BASIC không thể xử lý nội dung nhị phân (binary) → phải từ chối
     *
     * @return true nếu nội dung hợp lệ cho cấp độ dịch vụ tương ứng
     */
    public boolean validateContent(String serviceLevel, String contentType, boolean hasBinaryContent) {
        if (BASIC.equals(serviceLevel) && hasBinaryContent) {
            log.warn("ATSMHS: BASIC mode cannot handle binary content (content-type={})", contentType);
            return false;
        }
        return true;
    }
}
