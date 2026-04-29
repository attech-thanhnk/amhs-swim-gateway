package vn.asg.cp.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.Gwin;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GwinRepository extends JpaRepository<Gwin, Long>, JpaSpecificationExecutor<Gwin> {
        /** Kiểm tra duplicate AMQP message-id */
        boolean existsByMessageId(String messageId);

        /** Count by status */
        long countByStatus(int status);

        /** Get latest messages */
        List<Gwin> findTop100ByOrderByTimeDesc();

        /** Find by status với pagination */
        Page<Gwin> findByStatus(int status, Pageable pageable);

        /** Find by status và source */
        Page<Gwin> findByStatusAndSource(int status, String source, Pageable pageable);

        /** Find by status trong time range */
        Page<Gwin> findByStatusAndTimeBetween(int status, LocalDateTime fromTime, LocalDateTime toTime,
                        Pageable pageable);

        /** Find by status, time range và source */
        Page<Gwin> findByStatusAndTimeBetweenAndSource(
                        int status, LocalDateTime fromTime, LocalDateTime toTime, String source, Pageable pageable);

        /** Count by status trong time range */
        long countByStatusAndTimeBetween(int status, LocalDateTime fromTime, LocalDateTime toTime);

        /** Count total messages trong time range */
        long countByTimeBetween(LocalDateTime fromTime, LocalDateTime toTime);

        /** Find by addressing source (for statistics) */
        @Query("SELECT g.addressingSource, COUNT(g) FROM Gwin g " +
                        "WHERE g.time BETWEEN :fromTime AND :toTime " +
                        "GROUP BY g.addressingSource")
        List<Object[]> countByAddressingSourceBetween(
                        @Param("fromTime") LocalDateTime fromTime,
                        @Param("toTime") LocalDateTime toTime);
}
