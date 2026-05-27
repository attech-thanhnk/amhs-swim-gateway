package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.asg.cp.repository.MessageTypeRegistryRepository;

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MessageTypeRegistryRepository messageTypeRegistryRepository;

    @GetMapping("/message-types")
    public ResponseEntity<?> getMessageTypes() {
        return ResponseEntity.ok(messageTypeRegistryRepository.findAll());
    }

    @GetMapping("/roles")
    public ResponseEntity<?> getRoles() {
        return ResponseEntity.ok(java.util.List.of(
                java.util.Map.of("code", "ADMIN", "name", "Quản trị viên"),
                java.util.Map.of("code", "OPERATOR", "name", "Nhân viên vận hành"),
                java.util.Map.of("code", "USER", "name", "Người dùng xem tin")));
    }
}
