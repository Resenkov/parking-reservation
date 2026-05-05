package resenkov.work.parkingreservationservice.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParkingLotResponse {
    private final Long id;
    private final String code;
    private final String name;
    private final String address;
    private final boolean active;
}
