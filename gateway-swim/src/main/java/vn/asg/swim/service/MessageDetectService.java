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
 * Tự động nhận dạng loại bản tin từ nội dung.
 * Sử dụng khớp mẫu và tự động làm mới bộ nhớ đệm (cache) mỗi 5 phút.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageDetectService {

    private final MessageTypeRegistryRepository registryRepository;

    // Bộ nhớ đệm chứa các loại bản tin, sắp xếp theo độ dài mẫu giảm dần để tránh khớp nhầm
    private final List<MessageTypeRegistry> cache = new CopyOnWriteArrayList<>();

    /**
     * Nạp lại cấu hình từ cơ sở dữ liệu mỗi 5 phút.
     */
    @PostConstruct
    @Scheduled(fixedDelay = 300_000)
    public void reloadCache() {
        try {
            List<MessageTypeRegistry> all = registryRepository.findByActiveTrue().stream()
                    .filter(r -> r.getDetectPattern() != null && !r.getDetectPattern().isEmpty())
                    .sorted(Comparator.comparingInt(r -> -r.getDetectPattern().length()))
                    .toList();

            cache.clear();
            cache.addAll(all);
            log.debug("Reload message type cache: {} types", all.size());
        } catch (Exception e) {
            log.error("Failed to reload message_type_registry: {}", e.getMessage());
        }
    }

    /**
     * Nhận dạng loại bản tin từ nội dung body.
     * Loại bỏ tiêu đề AFTN (ZCZC, độ ưu tiên, thời gian nộp) trước khi khớp mẫu.
     *
     * @param body Nội dung bản tin (dạng văn bản hoặc JSON/XML)
     * @return Loại bản tin tương ứng hoặc "UNKNOWN"
     */
    public String detect(String body) {
        if (body == null || body.isBlank()) {
            log.warn("Detect called with null/blank body");
            return "UNKNOWN";
        }

        // Loại bỏ tiêu đề AFTN để lấy nội dung thực tế
        String[] lines = body.split("\\r?\\n");
        String contentToMatch = "";

        boolean foundStart = false;
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;

            // Bỏ qua các dòng tiêu đề AFTN
            if (l.startsWith("ZCZC")) continue;
            if (l.matches("^(SS|DD|FF|GG|KK)\\s+.*")) continue;
            if (l.matches("^\\d{6}\\s+[A-Z]{8}.*")) continue;

            contentToMatch = l;
            foundStart = true;
            break;
        }

        if (!foundStart) {
            contentToMatch = body.stripLeading();
        }

        String normalizedBody = body.replaceAll("\\s+", "");
        log.debug("Detect: normalized length={}, preview={}",
                  normalizedBody.length(),
                  normalizedBody.substring(0, Math.min(50, normalizedBody.length())));

        // Khớp với từng mẫu trong cache
        for (MessageTypeRegistry reg : cache) {
            String pattern = reg.getDetectPattern();
            if (pattern == null || pattern.isEmpty()) continue;

            log.debug("Checking type={} pattern=[{}]", reg.getMessageType(), pattern);

            // Khớp mẫu định dạng JSON/XML
            if (pattern.contains("\"") || pattern.contains("{") || pattern.contains("<")) {
                String normalizedPattern = pattern.replaceAll("[\\s+\"\']", "").toLowerCase();
                String bodyToCompare = normalizedBody.replaceAll("[\"\']", "").toLowerCase();

                if (bodyToCompare.contains(normalizedPattern)) {
                    log.info("Detected type={} from JSON/XML pattern", reg.getMessageType());
                    return reg.getMessageType();
                }
            } else {
                // Khớp mẫu định dạng TAC (so khớp tiền tố không phân biệt chữ hoa thường)
                if (contentToMatch.regionMatches(true, 0, pattern, 0, pattern.length())) {
                    log.info("Detected type={} from TAC pattern", reg.getMessageType());
                    return reg.getMessageType();
                }
            }
        }

        // Ghi nhận cảnh báo nếu không nhận dạng được loại bản tin
        log.warn("Cannot detect message type. Content preview: {}",
                 contentToMatch.substring(0, Math.min(100, contentToMatch.length())));
        return "UNKNOWN";
    }

}
