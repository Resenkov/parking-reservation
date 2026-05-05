package resenkov.work.parkingreservationservice.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParkingLotRequest {
    @NotBlank(message = "lot code is required")
    private String code;

    @NotBlank(message = "lot name is required")
    private String name;

    private String address;

    private boolean active = true;
}
