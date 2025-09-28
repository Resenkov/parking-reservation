package resenkov.work.parkingeurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class ParkingEurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkingEurekaServerApplication.class, args);
    }

}
