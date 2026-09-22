package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.Gwout;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GwoutRepository extends JpaRepository<Gwout, Long>, JpaSpecificationExecutor<Gwout> {
        long countByStatus(int status);

        @Query("""
        SELECT COUNT(msgid) FROM Gwout g
        ORDER BY g.time DESC
        """)
        Long countAll();

        List<Gwout> findTop100ByOrderByTimeDesc();

        /** Xóa các bản tin cũ hơn cutoff (dùng cho data retention). */
        @Modifying
        @Query(value = "DELETE FROM gwout WHERE time < :cutoff", nativeQuery = true)
        int deleteByTimeBefore(@Param("cutoff") LocalDateTime cutoff);
}
