package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.McuIpn;

import java.util.List;

@Repository
public interface McuIpnRepository extends JpaRepository<McuIpn, Long> {

    List<McuIpn> findAllByOrderByIdDesc();

    List<McuIpn> findByNotificationTypeOrderByIdDesc(String notificationType);
}
