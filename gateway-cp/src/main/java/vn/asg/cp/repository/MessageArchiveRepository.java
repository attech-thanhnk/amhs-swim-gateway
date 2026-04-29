package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.MessageArchive;

@Repository
public interface MessageArchiveRepository
        extends JpaRepository<MessageArchive, String>, JpaSpecificationExecutor<MessageArchive> {
}
