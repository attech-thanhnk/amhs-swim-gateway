package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.repository.RoutingRepository;

import java.util.List;
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

        // Thử lần 1: So khớp chính xác
        Optional<Routing> match = rules.stream()
                .filter(r -> messageType.equalsIgnoreCase(r.getMessageType()))
                .findFirst();

        if (match.isPresent()) {
            return match;
        }

        // Thử lần 2: So khớp tiền tố (ví dụ: METAR_TEXT khớp METAR)
        return rules.stream()
                .filter(r -> {
                    String rType = r.getMessageType().toUpperCase();
                    String mType = messageType.toUpperCase();
                    return mType.startsWith(rType + "_") || mType.startsWith(rType + " ");
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
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }
}
