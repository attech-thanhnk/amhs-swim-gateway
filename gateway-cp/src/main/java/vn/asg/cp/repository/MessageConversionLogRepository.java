package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.MessageConversionLog;

@Repository
public interface MessageConversionLogRepository
        extends JpaRepository<MessageConversionLog, Long>, JpaSpecificationExecutor<MessageConversionLog> {
    java.util.Optional<MessageConversionLog> findFirstByReferenceIdOrderByIdDesc(Long referenceId);
    java.util.Optional<MessageConversionLog> findFirstByAmqpMessageIdOrderByIdDesc(String amqpMessageId);
    java.util.Optional<MessageConversionLog> findFirstByMessageIdOrderByIdDesc(String messageId);
}
