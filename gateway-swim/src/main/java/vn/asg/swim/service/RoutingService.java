package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.repository.RoutingRepository;

import java.util.List;
import java.util.Optional;

/**
 * Simple Routing Service.
 * Matches message type and scope to find a routing rule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingService {

    private final RoutingRepository routingRepository;

    /**
     * Matches OUTBOUND rule based on message type.
     */
    public Optional<Routing> findBestMatchOut(String messageType) {
        if (messageType == null)
            return Optional.empty();

        List<Routing> rules = routingRepository.findByDirectionAndActiveTrueOrderByPriorityAsc("OUT");

        // First attempt: match messageType
        Optional<Routing> match = rules.stream()
                .filter(r -> messageType.equalsIgnoreCase(r.getMessageType()))
                .findFirst();

        if (match.isPresent()) {
            return match;
        }

        // Second attempt: match messageType starts with delimiter (e.g. METAR_TEXT matches METAR)
        return rules.stream()
                .filter(r -> {
                    String rType = r.getMessageType().toUpperCase();
                    String mType = messageType.toUpperCase();
                    return mType.startsWith(rType + "_") || mType.startsWith(rType + " ");
                })
                .findFirst();
    }

    /**
     * Matches INBOUND rule based on topic and optional filter.
     */
    public Optional<Routing> findBestMatchIn(String topic, String filter) {
        if (topic == null)
            return Optional.empty();

        List<Routing> rules = routingRepository.findByDirectionAndActiveTrueOrderByPriorityAsc("IN");

        // Topic matching (dots replaced by slashes for consistency)
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
     * Gets all unique active inbound topics for subscription.
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
