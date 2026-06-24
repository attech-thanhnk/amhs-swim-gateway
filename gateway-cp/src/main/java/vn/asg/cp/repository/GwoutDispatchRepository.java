package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.GwoutDispatch;

import java.util.List;

@Repository
public interface GwoutDispatchRepository extends JpaRepository<GwoutDispatch, Long>, JpaSpecificationExecutor<GwoutDispatch> {
    List<GwoutDispatch> findByGwoutId(Long gwoutId);
    long countByStatus(String status);
}
