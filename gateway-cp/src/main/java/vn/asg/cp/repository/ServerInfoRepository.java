package vn.asg.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.asg.cp.entity.ServerInfo;
import java.util.Optional;

@Repository
public interface ServerInfoRepository extends JpaRepository<ServerInfo, String> {
    Optional<ServerInfo> findByIpAddressAndVersion(String ipAddress, String version);

    @Query(value = "SELECT * FROM server_info s WHERE s.version = :version ORDER BY s.uuid DESC LIMIT 1", nativeQuery = true)
    Optional<ServerInfo> findFirstByVersionOrderByUuidDesc(@Param("version") String version);
}