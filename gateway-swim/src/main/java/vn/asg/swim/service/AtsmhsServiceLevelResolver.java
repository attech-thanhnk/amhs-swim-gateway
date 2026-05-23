package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EUR Doc 047 §3.3.3 - Lựa chọn Cấp độ Dịch vụ ATSMHS (ATSMHS Service Level Selection)
 *
 * Xác định xem nên sử dụng cấp độ dịch vụ Extended hay Basic ATSMHS
 * khi chuyển đổi bản tin SWIM sang AMHS IPM.
 *
 * Các chế độ Cấp độ Dịch vụ (C-08):
 * - EXTENDED: Luôn sử dụng extended ATSMHS (hỗ trợ nội dung nhị phân/binary)
 * - BASIC: Luôn sử dụng basic ATSMHS (chỉ hỗ trợ văn bản/text, từ chối binary)
 * - CONTENT_BASED: Quyết định dựa theo content-type
 * - RECIPIENTS_BASED: Quyết định dựa theo khả năng hỗ trợ của người nhận (recipients)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtsmhsServiceLevelResolver {

    private final ConfigService configService;

    public static final String EXTENDED = "EXTENDED";
    public static final String BASIC = "BASIC";

    /**
     * EUR Doc 047 §3.3.3.1-5: Phân giải cấp độ dịch vụ ATSMHS
     *
     * @param contentType AMQP content-type header
     * @param recipients  Danh sách địa chỉ AFTN phân tách bằng dấu cách
     * @return EXTENDED hoặc BASIC
     */
    public String resolve(String contentType, String recipients) {
        String mode = configService.get(ConfigService.KEY_ATSMHS_SERVICE_LEVEL);

        return switch (mode.toUpperCase()) {
            case "EXTENDED" -> {
                log.debug("ATSMHS: mode=EXTENDED → EXTENDED");
                yield EXTENDED;
            }
            case "BASIC" -> {
                log.debug("ATSMHS: mode=BASIC → BASIC");
                yield BASIC;
            }
            case "CONTENT_BASED" -> resolveByContent(contentType);
            case "RECIPIENTS_BASED" -> resolveByRecipients(recipients);
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
     *
     * Lưu ý: Hiện tại đang giả định tất cả người nhận đều hỗ trợ extended.
     * Trong thực tế, cần truy vấn X.500 Directory Service hoặc cơ sở dữ liệu
     * capability cục bộ.
     */
    private String resolveByRecipients(String recipients) {
        if (recipients == null || recipients.isBlank()) {
            log.debug("ATSMHS: recipients-based → BASIC (no recipients)");
            return BASIC;
        }

        // For now, check if recipients are in known extended-capable list
        String extendedCapableAddresses = configService.get("ATSMHS_EXTENDED_CAPABLE_ADDRESSES");

        if (extendedCapableAddresses.isBlank()) {
            // Default: assume all modern AMHS units support extended
            log.debug("ATSMHS: recipients-based → EXTENDED (default assumption)");
            return EXTENDED;
        }

        String[] recipientArray = recipients.trim().split("\\s+");
        for (String recipient : recipientArray) {
            if (!extendedCapableAddresses.contains(recipient)) {
                log.debug("ATSMHS: recipients-based → BASIC (recipient {} not extended-capable)", recipient);
                return BASIC;
            }
        }

        log.debug("ATSMHS: recipients-based → EXTENDED (all recipients capable)");
        return EXTENDED;
    }

    /**
     * EUR Doc 047 §3.3.3.2 - Kiểm tra tính hợp lệ của nội dung theo cấp độ dịch vụ (C-10)
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
