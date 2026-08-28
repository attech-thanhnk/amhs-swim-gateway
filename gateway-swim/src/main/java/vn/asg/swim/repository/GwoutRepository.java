package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.Gwout;

import java.util.List;

@Repository
public interface GwoutRepository extends JpaRepository<Gwout, Long> {

        /**
         * Poll a batch of PENDING records (status = 0 or NULL) to forward.
         */
        @Query(value = """
                        SELECT * FROM gwout
                        WHERE status = 0
                        ORDER BY FIELD(coalesce(amhs_priority, 'KK'), 'SS', 'DD', 'FF', 'GG', 'KK') ASC, time ASC
                        LIMIT :batchSize
                        FOR UPDATE
                        """, nativeQuery = true)
        List<Gwout> findPendingForwardBatch(@Param("batchSize") int batchSize);

        /**
         * Poll a batch of TRANSFORMED records (status = 1) for publishing to Solace.
         */
        @Query(value = """
                        SELECT * FROM gwout
                        WHERE status = 1
                        ORDER BY swim_priority ASC, time ASC
                        LIMIT :batchSize
                        FOR UPDATE
                        """, nativeQuery = true)
        List<Gwout> findPendingPublishBatch(@Param("batchSize") int batchSize);

        long countByStatus(int status);

        /**
         * Tra bản tin đã đi qua gateway theo IPM-Identifier, phục vụ xử lý IPN đến
         * (EUR Doc 047 §4.4.7.1 / CTSW014, CTSW015).
         */
        List<Gwout> findByIpmId(String ipmId);

        /** Tra theo MTS-Identifier khi IPN không mang IPM-Identifier. */
        List<Gwout> findByAmhsid(String amhsid);
}

