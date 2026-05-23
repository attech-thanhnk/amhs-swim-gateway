package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.repository.RoutingRepository;

import java.util.List;
import java.util.Optional;

/**
 * Dịch vụ định tuyến đơn giản (Simple Routing Service).
 * So khớp message type và scope để tìm routing rule phù hợp.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingService {

    private final RoutingRepository routingRepository;

    /**
     * So khớp rule chiều OUTBOUND dựa theo message type.
     */
    public Optional<Routing> findBestMatchOut(String messageType) {
        if (messageType == null)
            return Optional.empty();

        List<Routing> rules = routingRepository.findByDirectionAndActiveTrueOrderByPriorityAsc("OUT");

        // Thử lần 1: so khớp chính xác messageType
        Optional<Routing> match = rules.stream()
                .filter(r -> messageType.equalsIgnoreCase(r.getMessageType()))
                .findFirst();

        if (match.isPresent()) {
            return match;
        }

        // Thử lần 2: so khớp messageType bắt đầu bằng dấu phân tách (ví dụ: METAR_TEXT khớp với METAR)
        return rules.stream()
                .filter(r -> {
                    String rType = r.getMessageType().toUpperCase();
                    String mType = messageType.toUpperCase();
                    return mType.startsWith(rType + "_") || mType.startsWith(rType + " ");
                })
                .findFirst();
    }

    /**
     * So khớp rule chiều INBOUND dựa theo topic và filter (nếu có).
     */
    public Optional<Routing> findBestMatchIn(String topic, String filter) {
        if (topic == null)
            return Optional.empty();

        List<Routing> rules = routingRepository.findByDirectionAndActiveTrueOrderByPriorityAsc("IN");

        // So khớp topic (thay thế dấu chấm bằng dấu gạch chéo để đảm bảo tính đồng nhất)
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
     * Lấy tất cả các active inbound topics duy nhất để thực hiện subscribe.
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
