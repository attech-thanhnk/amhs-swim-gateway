package vn.asg.swim.service;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.asg.swim.entity.MessageTypeRegistry;
import vn.asg.swim.repository.MessageTypeRegistryRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

        // Thu thập các dòng ứng viên (bỏ tiêu đề AFTN). Một bản tin thực tế có thể có
        // các dòng "header nghiệp vụ" đứng trước thân thật (FLW REC, dòng origin,
        // bulletin WMO 'SAVS31 VVPQ 240430', 'PART 1', '== ... =='), nên ta giữ lại
        // TẤT CẢ dòng ứng viên và khớp mẫu trên từng dòng thay vì chỉ dòng đầu tiên.
        String[] lines = body.split("\\r?\\n");
        List<String> candidates = new ArrayList<>();
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;

            // Bỏ qua các dòng tiêu đề AFTN
            if (l.startsWith("ZCZC")) continue;
            if (l.matches("^(SS|DD|FF|GG|KK)\\s+.*")) continue;
            if (l.matches("^\\d{6}\\s+[A-Z]{8}.*")) continue;

            candidates.add(l);
        }
        if (candidates.isEmpty()) {
            candidates.add(body.stripLeading());
        }

        String normalizedBody = body.replaceAll("\\s+", "");
        log.debug("Detect: normalized length={}, candidates={}", normalizedBody.length(), candidates.size());

        // Khớp với từng mẫu trong cache (đã sắp xếp theo độ dài giảm dần)
        for (MessageTypeRegistry reg : cache) {
            String pattern = reg.getDetectPattern();
            if (pattern == null || pattern.isEmpty()) continue;

            // Khớp mẫu định dạng JSON/XML (so trên toàn bộ body đã chuẩn hóa)
            if (pattern.contains("\"") || pattern.contains("{") || pattern.contains("<")) {
                String normalizedPattern = pattern.replaceAll("[\\s+\"\']", "").toLowerCase();
                String bodyToCompare = normalizedBody.replaceAll("[\"\']", "").toLowerCase();

                if (bodyToCompare.contains(normalizedPattern)) {
                    log.info("Detected type={} from JSON/XML pattern", reg.getMessageType());
                    return reg.getMessageType();
                }
            } else {
                // Khớp mẫu định dạng TAC (tiền tố, không phân biệt hoa thường) trên từng dòng ứng viên.
                // Dòng header không khớp mẫu nào sẽ bị bỏ qua tự nhiên.
                for (String candidate : candidates) {
                    if (matchesTacPattern(candidate, pattern)) {
                        log.info("Detected type={} from TAC pattern", reg.getMessageType());
                        return reg.getMessageType();
                    }
                }
            }
        }

        // Ghi nhận cảnh báo nếu không nhận dạng được loại bản tin
        String preview = candidates.get(0);
        log.warn("Cannot detect message type. Content preview: {}",
                 preview.substring(0, Math.min(100, preview.length())));
        return "UNKNOWN";
    }

    private boolean matchesTacPattern(String candidate, String pattern) {
        if (candidate.regionMatches(true, 0, pattern, 0, pattern.length())) {
            return true;
        }

        if (pattern.startsWith("(")) {
            return false;
        }

        String upperCandidate = candidate.toUpperCase(Locale.ROOT);
        String upperPattern = pattern.toUpperCase(Locale.ROOT);
        int idx = upperCandidate.indexOf(upperPattern);
        while (idx >= 0) {
            if (idx == 0 || Character.isWhitespace(upperCandidate.charAt(idx - 1)) || hasInlineAftnAddressBefore(upperCandidate, idx)) {
                return true;
            }
            idx = upperCandidate.indexOf(upperPattern, idx + 1);
        }
        return false;
    }

    private boolean hasInlineAftnAddressBefore(String value, int endIndex) {
        if (endIndex < 8) {
            return false;
        }

        for (int i = endIndex - 8; i < endIndex; i++) {
            char ch = value.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                return false;
            }
        }
        return true;
    }

}
