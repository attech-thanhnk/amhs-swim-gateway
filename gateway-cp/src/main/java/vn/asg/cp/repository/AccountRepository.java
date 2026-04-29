package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    boolean existsByAccountName(String accountName);
}
