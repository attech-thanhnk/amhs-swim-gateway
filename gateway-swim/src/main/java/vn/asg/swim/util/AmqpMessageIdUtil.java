package vn.asg.swim.util;

/**
 * Cắt tiền tố mà tầng JMS gắn thêm vào message-id, trả về phần định danh thuần.
 *
 * <p>Chuẩn JMS bắt buộc mọi {@code JMSMessageID} bắt đầu bằng "ID:". Client Qpid JMS
 * (AMQP 1.0) còn chèn thêm một tiền tố kiểu dữ liệu khi message-id gốc không phải chuỗi,
 * vd. "ID:AMQP_UUID:...". Cả hai đều là chi tiết của tầng vận chuyển, không thuộc
 * định danh nghiệp vụ, nên không lưu vào cơ sở dữ liệu.
 *
 * <p>Dùng chung cho cả hai chiều để {@code gwin.message_id} và {@code gwout.amqp_message_id}
 * cùng một dạng — màn hình Control Position tìm kiếm bằng LIKE trên cả hai cột.
 */
public class AmqpMessageIdUtil {

    /** Xét theo thứ tự: tiền tố kiểu dữ liệu trước, "ID:" trần sau cùng. */
    private static final String[] JMS_PREFIXES = {
        "ID:AMQP_NO_PREFIX:",
        "ID:AMQP_STRING:",
        "ID:AMQP_BINARY:",
        "ID:AMQP_ULONG:",
        "ID:AMQP_UUID:",
        "ID:"
    };

    private AmqpMessageIdUtil() {
    }

    /**
     * @param raw giá trị thô lấy từ {@code getJMSMessageID()} hoặc từ một property
     * @return phần định danh đã cắt tiền tố; null nếu rỗng, chỉ có tiền tố, hoặc là chuỗi "null"
     */
    public static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String cleaned = raw.trim();
        for (String prefix : JMS_PREFIXES) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length()).trim();
                break;
            }
        }

        if (cleaned.isBlank() || "null".equalsIgnoreCase(cleaned)) {
            return null;
        }
        return cleaned;
    }
}
