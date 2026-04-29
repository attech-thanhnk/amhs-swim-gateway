package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.MessageTypeRegistry;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageTypeRegistryRepository extends JpaRepository<MessageTypeRegistry, Long> {

    /** Lấy tất cả loại điện văn đang active (dùng khi khởi động để cache) */
    List<MessageTypeRegistry> findByActiveTrueOrderByPhaseAsc();

    /** Tra cứu theo message type key */
    Optional<MessageTypeRegistry> findByMessageType(String messageType);
}
