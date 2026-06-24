package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.CpUser;

import java.util.Optional;

@Repository
public interface CpUserRepository extends JpaRepository<CpUser, String>, JpaSpecificationExecutor<CpUser> {
    Optional<CpUser> findByUsername(String username);
}
