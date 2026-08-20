package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.asg.cp.dto.ApiResponse;
import vn.asg.cp.entity.Routing;
import vn.asg.cp.repository.RoutingRepository;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final RoutingRepository routingRepository;

    /**
     * Danh sách loại bản tin AMHS -> SWIM nhận diện được, lấy từ các rule routing
     * direction=OUT có detect_pattern (thay cho bảng message_type_registry cũ).
     */
    @GetMapping("/message-types")
    public ResponseEntity<ApiResponse<List<Routing>>> getMessageTypes() {
        List<Routing> types = routingRepository.findByDirection("OUT").stream()
                .filter(r -> r.getMessageType() != null)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(types));
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getRoles() {
        List<Map<String, String>> roles = List.of(
                Map.of("code", vn.asg.cp.entity.UserRole.admin.name(), "name", "Quản trị viên"),
                Map.of("code", vn.asg.cp.entity.UserRole.viewer.name(), "name", "Người dùng xem tin"));
        return ResponseEntity.ok(ApiResponse.ok(roles));
    }
}

