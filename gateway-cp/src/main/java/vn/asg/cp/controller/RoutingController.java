package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.CreateRoutingRequest;
import vn.asg.cp.dto.UpdateRoutingRequest;
import vn.asg.cp.entity.Routing;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.exception.ValidationException;
import vn.asg.cp.repository.RoutingRepository;

import java.util.List;

/**
 * Các API xử lý CRUD cho cấu hình định tuyến (Routing).
 */
@RestController
@RequestMapping("/api/v1/routing")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingRepository routingRepository;

    @GetMapping
    public ResponseEntity<List<Routing>> list() {
        return ResponseEntity.ok(routingRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Routing> getOne(@PathVariable("id") Integer id) {
        Routing routing = routingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routing", id));
        return ResponseEntity.ok(routing);
    }

    @PostMapping
    public ResponseEntity<Routing> create(@RequestBody CreateRoutingRequest request) {
        if (request.getDirection() == null || request.getDirection().isBlank()) {
            throw new ValidationException("direction is required (IN/OUT)");
        }

        Routing routing = new Routing();
        routing.setDirection(request.getDirection());
        routing.setReceiveTopic(request.getReceiveTopic());
        routing.setMessageFilter(request.getMessageFilter());
        routing.setRecipients(request.getRecipients());
        routing.setOriginator(request.getOriginator());
        routing.setMessageType(request.getMessageType());
        routing.setSendTopic(request.getSendTopic());
        routing.setPriority(request.getPriority() != null ? request.getPriority() : 100);
        routing.setActive(request.getActive() != null ? request.getActive() : true);
        routing.setConvertToJson(request.getConvertToJson() != null ? request.getConvertToJson() : true);
        routing.setNote(request.getNote());

        sanitizeTopic(routing);
        validateNoConflict(routing); // Kiểm tra xung đột cấu hình trước khi lưu
        return ResponseEntity.status(HttpStatus.CREATED).body(routingRepository.save(routing));
    }

    private void sanitizeTopic(Routing routing) {
        if (routing.getSendTopic() != null) {
            routing.setSendTopic(routing.getSendTopic().replace('.', '/'));
        }
        if (routing.getReceiveTopic() != null) {
            routing.setReceiveTopic(routing.getReceiveTopic().replace('.', '/'));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Routing> update(@PathVariable("id") Integer id, @RequestBody UpdateRoutingRequest request) {
        Routing existing = routingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routing", id));

        if (request.getDirection() != null)
            existing.setDirection(request.getDirection());
        if (request.getReceiveTopic() != null)
            existing.setReceiveTopic(request.getReceiveTopic());
        if (request.getMessageFilter() != null)
            existing.setMessageFilter(request.getMessageFilter());
        if (request.getRecipients() != null)
            existing.setRecipients(request.getRecipients());
        if (request.getOriginator() != null)
            existing.setOriginator(request.getOriginator());
        if (request.getMessageType() != null)
            existing.setMessageType(request.getMessageType());
        if (request.getSendTopic() != null)
            existing.setSendTopic(request.getSendTopic());
        if (request.getPriority() != null)
            existing.setPriority(request.getPriority());
        if (request.getActive() != null)
            existing.setActive(request.getActive());
        if (request.getConvertToJson() != null)
            existing.setConvertToJson(request.getConvertToJson());
        if (request.getNote() != null)
            existing.setNote(request.getNote());

        sanitizeTopic(existing);
        validateNoConflict(existing); // Kiểm tra xung đột cấu hình trước khi cập nhật
        return ResponseEntity.ok(routingRepository.save(existing));
    }

    /**
     * Kiểm tra không trùng lặp (xung đột) cấu hình với các luật khác.
     */
    private void validateNoConflict(Routing newRule) {
        // Bỏ qua kiểm tra nếu luật định tuyến đang ở trạng thái không hoạt động
        if (newRule.getActive() == null || !newRule.getActive()) {
            return;
        }

        // Tối ưu hóa: Truy vấn DB theo bộ lọc thay vì tải toàn bộ bản ghi
        List<Routing> existing = routingRepository.findByDirectionAndActiveTrueOrderByPriorityAsc(newRule.getDirection());

        for (Routing rule : existing) {
            // Bỏ qua chính bản ghi đang cập nhật
            if (rule.getId() != null && rule.getId().equals(newRule.getId())) {
                continue;
            }

            // Kiểm tra trùng lặp: cùng chiều và độ ưu tiên
            if (rule.getPriority() != null && rule.getPriority().equals(newRule.getPriority())) {
                // Hướng OUT: kiểm tra xung đột loại bản tin
                if ("OUT".equalsIgnoreCase(newRule.getDirection())) {
                    if (rule.getMessageType() != null && newRule.getMessageType() != null
                            && rule.getMessageType().equalsIgnoreCase(newRule.getMessageType())) {
                        throw new ValidationException(
                                String.format("Conflict với rule#%d: cùng OUT + priority=%d + messageType=%s",
                                        rule.getId(), rule.getPriority(), rule.getMessageType()));
                    }
                }
                // Hướng IN: kiểm tra xung đột topic nhận về
                else if ("IN".equalsIgnoreCase(newRule.getDirection())) {
                    if (rule.getReceiveTopic() != null && newRule.getReceiveTopic() != null
                            && rule.getReceiveTopic().equalsIgnoreCase(newRule.getReceiveTopic())) {
                        throw new ValidationException(
                                String.format("Conflict với rule#%d: cùng IN + priority=%d + receiveTopic=%s",
                                        rule.getId(), rule.getPriority(), rule.getReceiveTopic()));
                    }
                }
            }
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Integer id) {
        if (!routingRepository.existsById(id)) {
            throw new ResourceNotFoundException("Routing", id);
        }
        routingRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
