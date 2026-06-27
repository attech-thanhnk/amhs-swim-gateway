package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.GwinHistory;

@Repository
public interface GwinHistoryRepository extends JpaRepository<GwinHistory, Long>, JpaSpecificationExecutor<GwinHistory> {
    long countByStatus(int status);
    long countByErrorType(int errorType);
}
