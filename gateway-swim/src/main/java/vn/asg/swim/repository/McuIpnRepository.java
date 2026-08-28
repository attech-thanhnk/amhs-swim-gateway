package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.McuIpn;

import java.util.List;

@Repository
public interface McuIpnRepository extends JpaRepository<McuIpn, Long> {

    /**
     * Lấy một lô IPN chưa xử lý do AMHS Component ghi vào (CTSW014/CTSW015).
     */
    @Query(value = """
            SELECT * FROM mtcu_ipn
            WHERE status IS NULL OR status = 'PENDING'
            ORDER BY id ASC
            LIMIT :batchSize
            FOR UPDATE
            """, nativeQuery = true)
    List<McuIpn> findPendingBatch(@Param("batchSize") int batchSize);
}
