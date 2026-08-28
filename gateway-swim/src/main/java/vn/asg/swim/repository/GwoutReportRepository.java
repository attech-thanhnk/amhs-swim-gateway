package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.GwoutReport;

import java.util.List;
import java.util.Optional;

@Repository
public interface GwoutReportRepository extends JpaRepository<GwoutReport, Long> {

    /** Tất cả dòng report của một bản tin — amss gom theo đây để dựng combined report. */
    List<GwoutReport> findByGwoutId(Long gwoutId);

    /**
     * Kiểm tra idempotency trước khi ghi: cùng (bản tin, recipient, loại report) chỉ được
     * tồn tại một dòng, khớp với ràng buộc UNIQUE uk_report ở tầng CSDL.
     */
    Optional<GwoutReport> findByGwoutIdAndRecipientAndReportType(Long gwoutId, String recipient,
            String reportType);

    long countByStatus(String status);
}
