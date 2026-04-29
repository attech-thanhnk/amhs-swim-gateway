package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.GwoutDispatch;

import java.util.List;

@Repository
public interface GwoutDispatchRepository extends JpaRepository<GwoutDispatch, Long> {
  long countByStatus(String status);

  List<GwoutDispatch> findByGwoutId(Long gwoutId);
}
