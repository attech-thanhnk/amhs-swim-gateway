package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.GwoutHistory;

@Repository
public interface GwoutHistoryRepository extends JpaRepository<GwoutHistory, Long>, JpaSpecificationExecutor<GwoutHistory> {
    long countByStatus(int status);
    long countByErrorType(int errorType);
}
