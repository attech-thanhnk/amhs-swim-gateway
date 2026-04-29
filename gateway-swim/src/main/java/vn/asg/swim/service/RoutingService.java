package vn.asg.swim.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.repository.RoutingRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingService {

    private final RoutingRepository routingRepository;

    public Optional<Routing> findBestMatchOut(String messageType) {
        List<Routing> results = routingRepository.findBestMatchOut(
                messageType, PageRequest.of(0, 1));
        if (results.isEmpty()) {
            log.warn("No OUT routing rule for messageType={}", messageType);
        }
        return results.stream().findFirst();
    }

    public Optional<Routing> findBestMatchIn(String receiveTopic, String messageFilter) {
        List<Routing> results = routingRepository.findBestMatchIn(
                receiveTopic, messageFilter, PageRequest.of(0, 1));
        if (results.isEmpty()) {
            log.warn("No IN routing rule for topic={}, filter={}", receiveTopic, messageFilter);
        }
        return results.stream().findFirst();
    }

    public List<String> getActiveInboundTopics() {
        return routingRepository.findDistinctActiveInboundTopics();
    }
}
