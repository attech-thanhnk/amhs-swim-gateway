package vn.asg.swim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.asg.converter.ConverterFacade;

@SpringBootApplication
@EnableScheduling
public class SwimApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwimApplication.class, args);
    }

    @Bean
    public ConverterFacade converterFacade() {
        return new ConverterFacade();
    }
}

