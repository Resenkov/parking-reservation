package resenkov.work.parkingreservationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableDiscoveryClient
@EntityScan(basePackages = "resenkov.work.parkingreservationservice.entity")
@EnableJpaRepositories(basePackages = "resenkov.work.parkingreservationservice.repository")
public class ParkingReservationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkingReservationServiceApplication.class, args);
    }

}
