package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.GwinDispatch;

import java.util.List;

@Repository
public interface GwinDispatchRepository extends JpaRepository<GwinDispatch, Long> {
  long countByStatus(String status);

  List<GwinDispatch> findByGwinId(Long gwinId);
}
