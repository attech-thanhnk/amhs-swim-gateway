package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.asg.cp.dto.ApiResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getRoles() {
        List<Map<String, String>> roles = List.of(
                Map.of("code", vn.asg.cp.entity.UserRole.admin.name(), "name", "Quản trị viên"),
                Map.of("code", vn.asg.cp.entity.UserRole.viewer.name(), "name", "Người dùng xem tin"));
        return ResponseEntity.ok(ApiResponse.ok(roles));
    }
}

