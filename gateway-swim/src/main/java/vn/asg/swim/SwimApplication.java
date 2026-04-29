package vn.asg.swim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SwimApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwimApplication.class, args);
    }
}
