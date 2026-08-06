package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.repository.RoutingRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Dịch vụ định tuyến - tìm kiếm rule phù hợp theo direction, topic/messageType và priority.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingService {

    private final RoutingRepository routingRepository;

    /**
     * Tìm kiếm rule phù hợp cho chiều gửi đi (Outbound) theo loại bản tin.
     */
    public Optional<Routing> findBestMatchOut(String messageType) {
        if (messageType == null)
            return Optional.empty();

        List<Routing> rules = routingRepository.findByDirectionAndActiveTrueOrderByPriorityAsc("OUT");

        // Thử lần 1: So khớp chính xác (không phân biệt hoa thường)
        Optional<Routing> match = rules.stream()
                .filter(r -> r.getMessageType() != null && messageType.equalsIgnoreCase(r.getMessageType()))
                .findFirst();

        if (match.isPresent()) {
            return match;
        }

        // Thử lần 2: So khớp linh hoạt hai chiều (ví dụ: rType METAR_TEXT khớp mType METAR)
        return rules.stream()
                .filter(r -> {
                    if (r.getMessageType() == null) return false;
                    String rType = r.getMessageType().toUpperCase();
                    String mType = messageType.toUpperCase();
                    return rType.startsWith(mType + "_") || rType.startsWith(mType + " ") || rType.startsWith(mType)
                        || mType.startsWith(rType + "_") || mType.startsWith(rType + " ") || mType.startsWith(rType);
                })
                .findFirst();
    }

    /**
     * Tìm kiếm rule phù hợp cho chiều nhận về (Inbound) theo topic và filter.
     */
    public Optional<Routing> findBestMatchIn(String topic, String filter) {
        if (topic == null)
            return Optional.empty();

        List<Routing> rules = routingRepository.findByDirectionAndActiveTrueOrderByPriorityAsc("IN");

        // Chuẩn hóa topic (thay dấu chấm bằng dấu gạch chéo)
        String normalizedTopic = topic.replace('.', '/');

        return rules.stream()
                .filter(r -> normalizedTopic.equalsIgnoreCase(r.getReceiveTopic()))
                .filter(r -> {
                    if (filter == null || r.getMessageFilter() == null) return true;
                    return filter.equalsIgnoreCase(r.getMessageFilter());
                })
                .findFirst();
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

    /**
     * Kiểm tra xem địa chỉ người nhận AMHS có cấu hình trong bất kỳ rule IN nào hay không.
     */
    public boolean isRecipientConfigured(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return false;
        }
        List<Routing> rules = routingRepository.findByDirectionAndActiveTrueOrderByPriorityAsc("IN");
        for (Routing r : rules) {
            if (r.getRecipients() != null && containsExact(r.getRecipients(), recipient)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kiểm tra xem originator có tồn tại trong các rule OUT (AMHS -> SWIM) đang hoạt động hay không.
     */
    public boolean existsOriginatorOut(String originator) {
        if (originator == null || originator.isBlank()) {
            return false;
        }
        return routingRepository.existsByOriginatorIgnoreCaseAndDirectionAndActiveTrue(originator, "OUT");
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
}
