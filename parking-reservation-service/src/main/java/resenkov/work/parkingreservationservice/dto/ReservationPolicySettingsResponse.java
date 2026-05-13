package resenkov.work.parkingreservationservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReservationPolicySettingsResponse {
    private final Integer bookingStepMinutes;
    private final Integer holdDurationMinutes;
    private final Integer arrivalDeadlineMinutesBeforeEnd;
    private final Integer standardCancellationRefundPercent;
    private final Integer noShowRefundPercent;
    private final Integer maxBookingDurationMinutes;
    private final Integer maxBookingAheadDays;
}
