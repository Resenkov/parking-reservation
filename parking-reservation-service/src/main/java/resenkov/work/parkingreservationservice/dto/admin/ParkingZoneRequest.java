package resenkov.work.parkingreservationservice.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParkingZoneRequest {
    @NotNull(message = "lotId is required")
    @Positive(message = "lotId must be positive")
    private Long lotId;

    @NotBlank(message = "zone code is required")
    private String code;

    @NotBlank(message = "zone name is required")
    private String name;

    private String level;

    private boolean active = true;
}
