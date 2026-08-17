package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.Routing;

import java.util.List;

@Repository
public interface RoutingRepository extends JpaRepository<Routing, Integer> {

        /** Tìm các luật định tuyến đang active theo hướng và ưu tiên */
    List<Routing> findByDirectionAndActiveTrueOrderByPriorityAsc(String direction);

    boolean existsByOriginatorIgnoreCaseAndDirectionAndActiveTrue(String originator, String direction);

    @Query("SELECT DISTINCT r.receiveTopic FROM Routing r WHERE r.direction = 'IN' AND r.active = true AND r.receiveTopic IS NOT NULL ORDER BY r.receiveTopic")
        List<String> findDistinctActiveInboundTopics();
}
