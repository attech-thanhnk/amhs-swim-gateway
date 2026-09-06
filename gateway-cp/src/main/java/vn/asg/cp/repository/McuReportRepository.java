package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.McuReport;

import java.util.List;

@Repository
public interface McuReportRepository extends JpaRepository<McuReport, Long> {

    List<McuReport> findAllByOrderByIdDesc();

    List<McuReport> findByReportTypeOrderByIdDesc(String reportType);
}
