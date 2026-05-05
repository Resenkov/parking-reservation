package resenkov.work.parkingreservationservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ReservationCatalogResponse {
    private final List<ReservationLevelOptionResponse> levels;
    private final List<ReservationZoneOptionResponse> zones;
}
