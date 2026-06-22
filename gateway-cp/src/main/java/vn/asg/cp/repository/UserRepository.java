package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.cp.entity.User;
import vn.asg.cp.entity.UserRole;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findByRole(UserRole role);

    List<User> findByIsActiveTrue();

    List<User> findByUsernameContainingOrEmailContaining(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
