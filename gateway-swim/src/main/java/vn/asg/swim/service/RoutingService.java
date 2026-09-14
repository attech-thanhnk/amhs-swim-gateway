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
     * Tìm rule chiều gửi đi (AMHS -> SWIM) theo địa chỉ AFTN của người nhận.
     */
    public Optional<Routing> findTopicForRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return Optional.empty();
        }
        String target = recipient.trim().toUpperCase(Locale.ROOT);

        List<Routing> rules = routingRepository.findByDirectionAndActiveTrueOrderByPriorityAsc("OUT");

        Routing best = null;
        int bestSpecificity = -1;

        for (Routing rule : rules) {
            if (rule.getSendTopic() == null || rule.getSendTopic().isBlank()) {
                continue;
            }
            int specificity = specificityOf(rule.getRecipients(), target);
            if (specificity > bestSpecificity) {
                bestSpecificity = specificity;
                best = rule;
            }
        }

        return Optional.ofNullable(best);
    }

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
