package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.MessageArchive;

@Repository
public interface MessageArchiveRepository extends JpaRepository<MessageArchive, String> {
}
