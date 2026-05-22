package vn.asg.cp.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import vn.asg.cp.entity.ServerInfo;
import vn.asg.cp.provider.AppVersionProvider;
import vn.asg.cp.repository.ServerInfoRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServerInfoInitializer implements ApplicationRunner {

    private final ServerInfoRepository serverInfoRepository; // Bạn tự tạo Interface JpaRepository nhé
    private final AppVersionProvider versionProvider;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("==> Bat dau quet va cap nhat thong tin Server...");

        // 1. Lay thong tin moi nhat tu thiet bi dang chay
        String ipAddress = InetAddress.getLocalHost().getHostAddress();
        String serverName = InetAddress.getLocalHost().getHostName();
        
        String currentVersion = versionProvider.getVersion();
        String currentDescription = versionProvider.getDescription();

        // 2. Kiem tra thiet bi nay da tung co trong DB chua dua vao IP va Version
        Optional<ServerInfo> existingServerWithSameVersion = 
                serverInfoRepository.findByIpAddressAndVersion(ipAddress, currentVersion);

        if (existingServerWithSameVersion.isPresent()) {
            // TRƯỜNG HỢP 1: Van la thiet bi nay, phien ban nay -> Chi update thong tin dong (CPU,...)
            ServerInfo serverToUpdate = existingServerWithSameVersion.get();
            serverToUpdate.setServerName(serverName);
            serverToUpdate.setDescription(currentDescription); // Cap nhat description neu co doi
            
            serverInfoRepository.save(serverToUpdate);
            log.info("==> Da cap nhat thong tin CPU cho Server hiện tại (Version: {})", currentVersion);
        } else {
            // TRƯỜNG HỢP 2: Thiet bi moi HOAC Thiet bi cu nhung mang Phien ban (Version) moi
            ServerInfo newServerRecord = ServerInfo.builder()
                    .uuid(UUID.randomUUID().toString()) // Tao UUID moi cho phien ban moi
                    .ipAddress(ipAddress)
                    .serverName(serverName)
                    .version(currentVersion)
                    .description(currentDescription)
                    .build();

            serverInfoRepository.save(newServerRecord);
            log.info("==> Da tao ban ghi moi cho Server (IP: {}, Version: {})", ipAddress, currentVersion);
        }
    }
}