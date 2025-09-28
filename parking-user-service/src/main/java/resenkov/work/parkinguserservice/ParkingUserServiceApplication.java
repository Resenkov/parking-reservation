package resenkov.work.parkinguserservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableDiscoveryClient
@EntityScan(basePackages = "resenkov.work.parkinguserservice.entity")
@EnableJpaRepositories(basePackages = "resenkov.work.parkinguserservice.repository")
public class ParkingUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkingUserServiceApplication.class, args);
    }

}
