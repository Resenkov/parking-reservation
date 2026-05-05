package resenkov.work.parkingreservationservice.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ParkingSpotRequest {
    @NotNull(message = "zoneId is required")
    @Positive(message = "zoneId must be positive")
    private Long zoneId;

    @NotBlank(message = "spot code is required")
    private String code;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "price must be non-negative")
    private BigDecimal price;
}
