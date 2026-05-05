package resenkov.work.parkingreservationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
@EnableDiscoveryClient
@EntityScan(basePackages = "resenkov.work.parkingreservationservice.entity")
@EnableJpaRepositories(basePackages = "resenkov.work.parkingreservationservice.repository")
@EnableScheduling
public class ParkingReservationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkingReservationServiceApplication.class, args);
    }

    @Bean
    public Clock systemClock(@Value("${app.reservation.time-zone:Europe/Moscow}") String timeZoneId) {
        return Clock.system(ZoneId.of(timeZoneId));
    }

}
