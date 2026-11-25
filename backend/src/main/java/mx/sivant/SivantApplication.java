package mx.sivant;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SivantApplication {

    public static void main(String[] args) {
        SpringApplication.run(SivantApplication.class, args);
    }

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
