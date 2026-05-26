package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.MessageTypeRegistry;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageTypeRegistryRepository extends JpaRepository<MessageTypeRegistry, Long> {

    /** Lấy tất cả loại điện văn đang active */
    List<MessageTypeRegistry> findByActiveTrue();

    /** Tra cứu theo message type key */
    Optional<MessageTypeRegistry> findByMessageType(String messageType);
}
