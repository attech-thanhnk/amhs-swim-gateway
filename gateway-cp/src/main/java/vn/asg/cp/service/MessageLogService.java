// ================================
// MessageLogService.java
// ================================

package vn.asg.cp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.asg.cp.entity.MessageLog;
import vn.asg.cp.repository.MessageLogRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageLogService {

    private final MessageLogRepository repository;

    public MessageLog save(MessageLog log) {
        return repository.save(log);
    }

    public List<MessageLog> getLatestLogs() {
        return repository.findTop20ByOrderByCreatedAtDesc();
    }

    public Page<MessageLog> getAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public MessageLog getById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public Page<MessageLog> getByStatus(
            String status,
            Pageable pageable) {

        return repository.findByProcessingStatus(status, pageable);
    }

    public Page<MessageLog> getByDirection(
            String direction,
            Pageable pageable) {

        return repository.findByDirection(direction, pageable);
    }

    public Page<MessageLog> getByMessageType(
            String type,
            Pageable pageable) {

        return repository.findByAmhsMessageType(type, pageable);
    }

    public Page<MessageLog> getByDateRange(
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {

        return repository.findByCreatedAtBetween(from, to, pageable);
    }
}
