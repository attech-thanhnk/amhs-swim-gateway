package vn.asg.swim.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.Routing;

import java.util.List;

@Repository
public interface RoutingRepository extends JpaRepository<Routing, Integer> {

        @Query("""
                        SELECT r FROM Routing r
                        WHERE r.direction = 'OUT'
                          AND r.active = true
                          AND r.messageType = :type
                        ORDER BY r.priority ASC
                        """)
        List<Routing> findBestMatchOut(@Param("type") String type, Pageable pageable);

        /** Tìm các luật định tuyến đang active theo hướng và ưu tiên */
    List<Routing> findByDirectionAndActiveTrueOrderByPriorityAsc(String direction);

    @Query("""
                        SELECT r FROM Routing r
                        WHERE r.direction = 'IN'
                          AND r.active = true
                          AND r.receiveTopic = :topic
                          AND (r.messageFilter = :filter OR r.messageFilter IS NULL)
                        ORDER BY r.priority ASC
                        """)
        List<Routing> findBestMatchIn(@Param("topic") String topic,
                        @Param("filter") String filter,
                        Pageable pageable);

        @Query("SELECT DISTINCT r.receiveTopic FROM Routing r WHERE r.direction = 'IN' AND r.active = true AND r.receiveTopic IS NOT NULL ORDER BY r.receiveTopic")
        List<String> findDistinctActiveInboundTopics();
}
