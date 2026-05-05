package resenkov.work.parkingreservationservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ParkingSpotAvailabilityResponse {
    private final Long id;
    private final String code;
    private final String zone;
    private final String level;
    private final BigDecimal price;
    private final boolean occupied;
}
