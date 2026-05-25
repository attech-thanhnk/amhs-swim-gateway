package vn.asg.cp.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppVersionProvider {

    // Spring Boot sẽ tự động map giá trị đã filter từ Maven vào đây
    @Value("${server.source.version}")
    private String version;

    @Value("${server.source.description}")
    private String description;

    public String getVersion() { 
        return version; 
    }
    
    public String getDescription() { 
        return description; 
    }
}