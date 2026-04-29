package vn.asg.swim.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.asg.swim.entity.GatewayConfig;

import java.util.Optional;

@Repository
public interface GatewayConfigRepository extends JpaRepository<GatewayConfig, String> {
    Optional<GatewayConfig> findByConfigKey(String key);
}
