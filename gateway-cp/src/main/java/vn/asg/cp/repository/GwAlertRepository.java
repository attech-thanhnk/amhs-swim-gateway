package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.GwAlert;

@Repository
public interface GwAlertRepository extends JpaRepository<GwAlert, Long> {

    long countByStatus(String status);

    java.util.List<GwAlert> findByStatus(String status);
}
