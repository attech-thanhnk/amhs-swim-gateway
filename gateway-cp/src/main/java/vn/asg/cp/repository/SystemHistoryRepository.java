package vn.asg.cp.repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.SystemHistory;

@Repository
public interface SystemHistoryRepository
        extends JpaRepository<SystemHistory, Long> {

    Page<SystemHistory> findAllByOrderByEventTimeDesc(Pageable pageable);
}
