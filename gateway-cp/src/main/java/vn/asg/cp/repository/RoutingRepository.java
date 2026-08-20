package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.Routing;

import java.util.List;

@Repository
public interface RoutingRepository extends JpaRepository<Routing, Integer>, JpaSpecificationExecutor<Routing> {

  List<Routing> findByDirection(String direction);
}
