package resenkov.work.parkingreservationservice.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ParkingSpotResponse {
    private final Long id;
    private final Long zoneId;
    private final String zoneCode;
    private final String zoneName;
    private final Long lotId;
    private final String lotCode;
    private final String code;
    private final boolean occupied;
    private final BigDecimal price;
    private final String level;
}
