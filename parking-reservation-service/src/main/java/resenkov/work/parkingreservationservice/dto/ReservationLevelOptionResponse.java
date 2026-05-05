package resenkov.work.parkingreservationservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReservationLevelOptionResponse {
    private final String code;
    private final long spotCount;
}
