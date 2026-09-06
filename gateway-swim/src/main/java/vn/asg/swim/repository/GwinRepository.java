package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.Gwin;

import java.util.List;

@Repository
public interface GwinRepository extends JpaRepository<Gwin, Long> {

    /** Check for duplicate AMQP message-id */
    boolean existsByMessageId(String messageId);

    long countByStatus(int status);

    /**
     * Tra bản tin chủ đề của một RN/NRN bay ngược về, theo IPM-Identifier.
     * Trả về List vì cột không có ràng buộc UNIQUE — lấy dòng đầu là đủ.
     */
    List<Gwin> findByIpmId(String ipmId);

    /**
     * Tra theo MTS-Identifier do MTA cấp lúc submit. Là khoá chính của CTSW114 vì report
     * tham chiếu bản tin gốc qua MTS-Identifier chứ không qua IPM-Identifier.
     */
    List<Gwin> findByMtsId(String mtsId);
}

