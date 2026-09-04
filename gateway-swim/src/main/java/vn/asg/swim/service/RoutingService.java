package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.repository.RoutingRepository;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Dịch vụ định tuyến - tìm kiếm rule phù hợp theo direction, topic/recipient và priority.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingService {

    private final RoutingRepository routingRepository;

    /**
     * Tìm rule chiều gửi đi (AMHS -> SWIM) theo ĐỊA CHỈ AFTN của người nhận.
     * <p>
     * EUR Doc 047 Appendix A §2.2 xác định các AMQP consumer là "configuration parameters which
     * are jointly set up", và CTSW009 kiểm tra việc phân phối dựa trên địa chỉ recipient chứ
     * không dựa trên nội dung bản tin. Vì vậy đích publish được tra thẳng từ cột
     * {@code routing.recipients} thay vì đoán loại bản tin từ thân điện văn.
     * <p>
     * Cột {@code recipients} chứa danh sách địa chỉ phân cách bằng dấu phẩy hoặc khoảng trắng.
     * Mỗi mục có thể là:
     * <ul>
     *   <li>địa chỉ AFTN đầy đủ 8 ký tự - khớp chính xác, không phân biệt hoa thường</li>
     *   <li>tiền tố kết thúc bằng {@code *} (ví dụ {@code VVTS*}) - khớp mọi địa chỉ bắt đầu
     *       bằng tiền tố đó; riêng {@code *} khớp mọi địa chỉ</li>
     * </ul>
     * Thứ tự ưu tiên: khớp chính xác thắng wildcard; giữa các wildcard thì tiền tố dài hơn
     * thắng; cuối cùng mới xét cột {@code priority} (nhỏ hơn thắng).
     *
     * @param recipient địa chỉ AFTN của MỘT người nhận
     * @return rule khớp, hoặc rỗng nếu không địa chỉ nào được cấu hình cho recipient này
     */
    public Optional<Routing> findTopicForRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return Optional.empty();
        }
        String target = recipient.trim().toUpperCase(Locale.ROOT);

        List<Routing> rules = routingRepository.findByDirectionAndActiveTrueOrderByPriorityAsc("OUT");

        Routing best = null;
        // -1 = chưa có gì; Integer.MAX_VALUE = khớp chính xác; còn lại = độ dài tiền tố wildcard
        int bestSpecificity = -1;

        for (Routing rule : rules) {
            if (rule.getSendTopic() == null || rule.getSendTopic().isBlank()) {
                continue;
            }
            int specificity = specificityOf(rule.getRecipients(), target);
            // Danh sách đã sắp theo priority tăng dần nên dùng ">" (không phải ">=") để rule
            // đứng trước thắng khi hai rule có cùng độ đặc hiệu.
            if (specificity > bestSpecificity) {
                bestSpecificity = specificity;
                best = rule;
            }
        }

        return Optional.ofNullable(best);
    }

    /**
     * Độ đặc hiệu của một rule đối với địa chỉ cần tra: -1 nếu không khớp,
     * {@link Integer#MAX_VALUE} nếu khớp chính xác, ngược lại là độ dài tiền tố wildcard.
     */
    private int specificityOf(String recipientsColumn, String target) {
        if (recipientsColumn == null || recipientsColumn.isBlank()) {
            return -1;
        }
        int best = -1;
        for (String raw : recipientsColumn.split("[,;\s]+")) {
            String entry = raw.trim().toUpperCase(Locale.ROOT);
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.endsWith("*")) {
                String prefix = entry.substring(0, entry.length() - 1);
                if (target.startsWith(prefix)) {
                    best = Math.max(best, prefix.length());
                }
            } else if (entry.equals(target)) {
                return Integer.MAX_VALUE;
            }
        }
        return best;
    }

    /**
     * Lấy danh sách active inbound topics (duy nhất) để subscribe.
     */
    public List<String> getActiveInboundTopics() {
        return routingRepository.findByDirectionAndActiveTrueOrderByPriorityAsc("IN")
                .stream()
                .map(Routing::getReceiveTopic)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

}
