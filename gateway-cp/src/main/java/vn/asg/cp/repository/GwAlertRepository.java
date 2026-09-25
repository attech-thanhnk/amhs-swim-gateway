package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.GwAlert;

@Repository
public interface GwAlertRepository extends JpaRepository<GwAlert, Long>, JpaSpecificationExecutor<GwAlert> {

    long countByStatus(String status);

    java.util.List<GwAlert> findByStatus(String status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM GwAlert a WHERE a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@org.springframework.data.repository.query.Param("cutoff") java.time.LocalDateTime cutoff);
}
