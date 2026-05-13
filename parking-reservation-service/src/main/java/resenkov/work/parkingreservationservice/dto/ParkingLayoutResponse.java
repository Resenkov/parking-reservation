package resenkov.work.parkingreservationservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ParkingLayoutResponse {
    private final LocalDateTime generatedAt;
    private final LocalDateTime from;
    private final LocalDateTime to;
    private final List<ParkingLayoutSpotResponse> spots;
}
