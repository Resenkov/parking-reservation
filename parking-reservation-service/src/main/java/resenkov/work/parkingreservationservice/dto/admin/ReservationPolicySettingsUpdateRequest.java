package resenkov.work.parkingreservationservice.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationPolicySettingsUpdateRequest {
    @NotNull(message = "bookingStepMinutes is required")
    @Min(value = 1, message = "bookingStepMinutes must be positive")
    private Integer bookingStepMinutes;

    @NotNull(message = "holdDurationMinutes is required")
    @Min(value = 1, message = "holdDurationMinutes must be positive")
    private Integer holdDurationMinutes;

    @NotNull(message = "arrivalDeadlineMinutesBeforeEnd is required")
    @Min(value = 0, message = "arrivalDeadlineMinutesBeforeEnd must be non-negative")
    private Integer arrivalDeadlineMinutesBeforeEnd;

    @NotNull(message = "standardCancellationRefundPercent is required")
    @Min(value = 0, message = "standardCancellationRefundPercent must be in range 0..100")
    @Max(value = 100, message = "standardCancellationRefundPercent must be in range 0..100")
    private Integer standardCancellationRefundPercent;

    @NotNull(message = "noShowRefundPercent is required")
    @Min(value = 0, message = "noShowRefundPercent must be in range 0..100")
    @Max(value = 100, message = "noShowRefundPercent must be in range 0..100")
    private Integer noShowRefundPercent;

    @NotNull(message = "maxBookingDurationMinutes is required")
    @Min(value = 1, message = "maxBookingDurationMinutes must be positive")
    private Integer maxBookingDurationMinutes;

    @NotNull(message = "maxBookingAheadDays is required")
    @Min(value = 1, message = "maxBookingAheadDays must be positive")
    private Integer maxBookingAheadDays;
}
