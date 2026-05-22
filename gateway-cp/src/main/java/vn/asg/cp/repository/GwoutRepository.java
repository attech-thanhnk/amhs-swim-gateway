package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import vn.asg.cp.entity.Gwout;

import java.util.List;

@Repository
public interface GwoutRepository extends JpaRepository<Gwout, Long>, JpaSpecificationExecutor<Gwout> {
        long countByStatus(int status);

        @Query("""
        SELECT COUNT(msgid) FROM Gwout g
        ORDER BY g.time DESC
        """)
        Long countAll();

        @Query("""
        SELECT COUNT(msgid) FROM Gwout g
        WHERE g.errorType = :errorType
        ORDER BY g.time DESC
        """)
        Long countByErrorType(@Param("errorType") int errorType);

        List<Gwout> findTop100ByOrderByTimeDesc();
}
