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
         * Poll a batch of TRANSFORMED records (status = 1) chưa có dòng gwout_dispatch nào,
         * để tạo lệnh phân phối cho từng recipient.
         * <p>
         * Điều kiện NOT EXISTS là bắt buộc: gwout giữ nguyên status = 1 cho tới khi MỌI dispatch
         * kết thúc, nên nếu publish lỗi và dispatch chuyển FAILED + next_retry_at ở tương lai,
         * lượt poll kế tiếp (POLL_INTERVAL_MS) sẽ chọn lại chính bản tin đó và tạo THÊM một bộ
         * dispatch đầy đủ. Các dòng cũ đến hạn retry sau đó sẽ publish lại cùng một IPM, vi phạm
         * §4.4.3.4.4 ("1 IPM AMHS chỉ sinh ra 1 message AMQP duy nhất").
         * <p>
         * Ràng buộc UNIQUE uk_dispatch (gwout_id, recipient) ở tầng CSDL là lưới đỡ thứ hai.
         */
        @Query(value = """
                        SELECT * FROM gwout g
                        WHERE g.status = 1
                          AND NOT EXISTS (
                              SELECT 1 FROM gwout_dispatch d WHERE d.gwout_id = g.msgid
                          )
                        ORDER BY g.swim_priority ASC, g.time ASC
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

