package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.MessageConversionLog;

import java.time.LocalDateTime;

@Repository
public interface MessageConversionLogRepository
        extends JpaRepository<MessageConversionLog, Long>, JpaSpecificationExecutor<MessageConversionLog> {
    java.util.Optional<MessageConversionLog> findFirstByReferenceIdOrderByIdDesc(Long referenceId);
    java.util.Optional<MessageConversionLog> findFirstByAmqpMessageIdOrderByIdDesc(String amqpMessageId);
    java.util.Optional<MessageConversionLog> findFirstByMessageIdOrderByIdDesc(String messageId);

    @Modifying
    @Query(value = "DELETE FROM message_conversion_log WHERE converted_time < :cutoff", nativeQuery = true)
    int deleteByConvertedTimeBefore(@Param("cutoff") LocalDateTime cutoff);
}
