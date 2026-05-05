package resenkov.work.parkingreservationservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name="parking_spot")
@Setter
@Getter
public class ParkingSpot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    private boolean occupied;

    private BigDecimal price;

    private String zone;

    private String level;

    @Column(name = "zone_id")
    private Long zoneId;
}
