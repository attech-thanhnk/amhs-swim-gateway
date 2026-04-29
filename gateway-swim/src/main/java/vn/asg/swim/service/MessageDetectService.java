package vn.asg.swim.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.MessageTypeRegistry;
import vn.asg.swim.repository.MessageTypeRegistryRepository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Identifies the message type from the body text.
 * Looks up the message_type_registry table based on detect_pattern.
 * In-memory cache reloaded every 5 minutes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageDetectService {

    private final MessageTypeRegistryRepository registryRepository;

    /**
     * Cache of active message types, sorted by pattern length (longest first)
     */
    private final List<MessageTypeRegistry> cache = new CopyOnWriteArrayList<>();

    @PostConstruct
    @Scheduled(fixedDelay = 300_000) // Reload every 5 minutes
    public void reloadCache() {
        try {
            List<MessageTypeRegistry> all = registryRepository.findByActiveTrueOrderByPhaseAsc();
            // Sort: longest pattern first to avoid false matches (e.g., "SIGMET" > "SIG")
            all.sort(Comparator.comparingInt(r -> -r.getDetectPattern().length()));
            cache.clear();
            cache.addAll(all);
            log.debug("MessageDetectService: loaded {} types from DB", all.size());
        } catch (Exception e) {
            log.error("Failed to reload message_type_registry: {}", e.getMessage());
        }
    }

    /**
     * Identifies the message type from body text.
     * Compares the start of the body with the detect_pattern of each type
     * (case-insensitive).
     *
     * @param body plain text content of the message
     * @return messageType. e.g., "METAR", "FPL". Returns "UNKNOWN" if no match is
     *         found.
     */
    public String detect(String body) {
        if (body == null || body.isBlank()) {
            return "UNKNOWN";
        }
        String trimmed = body.stripLeading();
        for (MessageTypeRegistry reg : cache) {
            String pattern = reg.getDetectPattern();
            if (pattern == null || pattern.isEmpty()) {
                continue; // ignore UNKNOWN (empty pattern) — handled later
            }
            if (pattern.startsWith("<")) {
                // XML messages (e.g. <iwxxm:METAR, <fx:FlightPlan) might not be at index 0 due
                // to <?xml ...?>
                if (trimmed.contains(pattern)) {
                    return reg.getMessageType();
                }
            } else {
                // Plain text AFTN messages match exactly at the beginning
                if (trimmed.regionMatches(true, 0, pattern, 0, pattern.length())) {
                    return reg.getMessageType();
                }
            }
        }
        return "UNKNOWN";
    }

}
