package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.GwAlert;

@Repository
public interface GwAlertRepository extends JpaRepository<GwAlert, Long> {

    long countByStatus(String status);
}
