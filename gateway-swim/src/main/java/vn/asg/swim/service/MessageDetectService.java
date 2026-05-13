package vn.asg.swim.service;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            List<MessageTypeRegistry> all = registryRepository.findByActiveTrue();
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

        // Split by lines to handle AFTN headers (ZCZC, GG, etc.)
        String[] lines = body.split("\\r?\\n");
        String contentToMatch = "";
        
        // Skip common AFTN header lines to find the start of the actual message content
        boolean foundStart = false;
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            
            // 1. Skip ZCZC line (Channel Sequence Number)
            if (l.startsWith("ZCZC")) continue;
            
            // 2. Skip Priority & Address lines (SS, DD, FF, GG, KK)
            if (l.matches("^(SS|DD|FF|GG|KK)\\s+.*")) continue;
            
            // 3. Skip Filing Time & Originator line (e.g., 131010 VNBBYOYX)
            if (l.matches("^\\d{6}\\s+[A-Z]{8}.*")) continue;
            
            contentToMatch = l;
            foundStart = true;
            break;
        }

        if (!foundStart) {
            contentToMatch = body.stripLeading();
        }

        String normalizedBody = body.replaceAll("\\s+", "");
        log.debug("MessageDetectService: normalizedBody length={}, content preview={}", 
                  normalizedBody.length(), normalizedBody.substring(0, Math.min(50, normalizedBody.length())));
        
        // Debug: Print hex bytes of first 10 chars to detect invisible characters
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(10, body.length()); i++) {
            hex.append(String.format("%02X ", (int) body.charAt(i)));
        }
        log.debug("MessageDetectService: Body Start Hex: {}", hex.toString());

        for (MessageTypeRegistry reg : cache) {
            String pattern = reg.getDetectPattern();
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            
            log.debug("MessageDetectService: Checking type={} with pattern=[{}]", reg.getMessageType(), pattern);
            
            // If pattern looks like JSON or XML (contains quotes, braces, or brackets)
            if (pattern.contains("\"") || pattern.contains("{") || pattern.contains("<")) {
                // Extreme normalization: remove spaces AND quotes for maximum resilience
                String normalizedPattern = pattern.replaceAll("[\\s+\"\']", "").toLowerCase();
                String bodyToCompare = normalizedBody.replaceAll("[\"\']", "").toLowerCase();
                boolean match = bodyToCompare.contains(normalizedPattern);
                
                log.debug("MessageDetectService: Strict check: body contains '{}' ? {}", normalizedPattern, match);
                
                if (match) {
                    log.debug("MessageDetectService: Match found! type={}, pattern={}", reg.getMessageType(), pattern);
                    return reg.getMessageType();
                }
            } else {
                // Fallback for TAC messages (match only the first non-header line)
                if (contentToMatch.regionMatches(true, 0, pattern, 0, pattern.length())) {
                    return reg.getMessageType();
                }
            }
        }
        return "UNKNOWN";
    }

}
