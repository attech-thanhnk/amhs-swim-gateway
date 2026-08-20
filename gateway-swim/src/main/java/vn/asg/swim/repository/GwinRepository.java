package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.Gwin;

@Repository
public interface GwinRepository extends JpaRepository<Gwin, Long> {

    /** Check for duplicate AMQP message-id */
    boolean existsByMessageId(String messageId);

    long countByStatus(int status);
}

