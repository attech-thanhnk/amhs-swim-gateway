package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.McuReport;

import java.util.List;

@Repository
public interface McuReportRepository extends JpaRepository<McuReport, Long> {

    /**
     * Lấy các report (DR/NDR) chưa xử lý do AMHS Component ghi vào.
     */
    @Query(value = """
            SELECT * FROM mtcu_report
            WHERE status IS NULL OR status = 'PENDING'
            ORDER BY id ASC
            LIMIT :batchSize
            FOR UPDATE
            """, nativeQuery = true)
    List<McuReport> findPendingBatch(@Param("batchSize") int batchSize);
}
