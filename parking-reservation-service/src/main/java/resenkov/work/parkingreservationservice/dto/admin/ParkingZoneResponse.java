package resenkov.work.parkingreservationservice.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParkingZoneResponse {
    private final Long id;
    private final Long lotId;
    private final String lotCode;
    private final String code;
    private final String name;
    private final String level;
    private final boolean active;
}
