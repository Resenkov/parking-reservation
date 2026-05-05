package resenkov.work.parkingreservationservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReservationZoneOptionResponse {
    private final String code;
    private final String name;
    private final String level;
    private final long spotCount;
}
