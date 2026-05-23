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
 * Nhận dạng loại bản tin (message type) từ nội dung văn bản body.
 * Tra cứu bảng message_type_registry dựa theo trường detect_pattern.
 * Bộ đệm trong bộ nhớ (in-memory cache) được tải lại tự động sau mỗi 5 phút.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageDetectService {

    private final MessageTypeRegistryRepository registryRepository;

    /**
     * Bộ đệm cache của các message type đang hoạt động, sắp xếp theo độ dài pattern (dài nhất trước)
     */
    private final List<MessageTypeRegistry> cache = new CopyOnWriteArrayList<>();

    /**
     * Tải lại message type registry cache từ cơ sở dữ liệu.
     */
    @PostConstruct
    @Scheduled(fixedDelay = 300_000) // Tải lại cache mỗi 5 phút
    public void reloadCache() {
        try {
            List<MessageTypeRegistry> all = registryRepository.findByActiveTrue();
            // Sắp xếp: pattern dài nhất lên trước để tránh so khớp nhầm (ví dụ: "SIGMET" > "SIG")
            all.sort(Comparator.comparingInt(r -> -r.getDetectPattern().length()));
            cache.clear();
            cache.addAll(all);
            log.debug("MessageDetectService: loaded {} types from DB", all.size());
        } catch (Exception e) {
            log.error("Failed to reload message_type_registry: {}", e.getMessage());
        }
    }

    /**
     * Nhận dạng loại bản tin từ nội dung body.
     * So sánh phần đầu của body với detect_pattern của từng loại đăng ký
     * (không phân biệt chữ hoa chữ thường).
     *
     * @param body Nội dung văn bản thuần (plain text) của bản tin
     * @return messageType. Ví dụ: "METAR", "FPL". Trả về "UNKNOWN" nếu không có so khớp nào.
     */
    public String detect(String body) {
        if (body == null || body.isBlank()) {
            return "UNKNOWN";
        }

        // Phân tách theo dòng để xử lý các tiêu đề AFTN (ZCZC, GG, v.v.)
        String[] lines = body.split("\\r?\\n");
        String contentToMatch = "";
        
        // Bỏ qua AFTN header thông dụng để tìm điểm bắt đầu của message body.
        boolean foundStart = false;
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            
            // 1. Bỏ qua ZCZC line.
            if (l.startsWith("ZCZC")) continue;
            
            // 2. Bỏ qua priority và address line (SS, DD, FF, GG, KK).
            if (l.matches("^(SS|DD|FF|GG|KK)\\s+.*")) continue;
            
            // 3. Bỏ qua filing time và originator line.
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

        for (MessageTypeRegistry reg : cache) {
            String pattern = reg.getDetectPattern();
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            
            log.debug("MessageDetectService: Checking type={} with pattern=[{}]", reg.getMessageType(), pattern);
            
            // So khớp nếu pattern có dạng JSON/XML.
            if (pattern.contains("\"") || pattern.contains("{") || pattern.contains("<")) {
                // Chuẩn hóa pattern và body để so khớp chính xác.
                String normalizedPattern = pattern.replaceAll("[\\s+\"\']", "").toLowerCase();
                String bodyToCompare = normalizedBody.replaceAll("[\"\']", "").toLowerCase();
                boolean match = bodyToCompare.contains(normalizedPattern);
                
                log.debug("MessageDetectService: Strict check: body contains '{}' ? {}", normalizedPattern, match);
                
                if (match) {
                    log.debug("MessageDetectService: Match found! type={}, pattern={}", reg.getMessageType(), pattern);
                    return reg.getMessageType();
                }
            } else {
                // So khớp cho TAC message.
                if (contentToMatch.regionMatches(true, 0, pattern, 0, pattern.length())) {
                    return reg.getMessageType();
                }
            }
        }
        return "UNKNOWN";
    }

}
