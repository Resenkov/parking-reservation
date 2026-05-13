package resenkov.work.parkingreservationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reservation_policy_settings")
@Getter
@Setter
public class ReservationPolicySettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "booking_step_minutes", nullable = false)
    private Integer bookingStepMinutes;

    @Column(name = "hold_duration_minutes", nullable = false)
    private Integer holdDurationMinutes;

    @Column(name = "arrival_deadline_minutes_before_end", nullable = false)
    private Integer arrivalDeadlineMinutesBeforeEnd;

    @Column(name = "standard_cancellation_refund_percent", nullable = false)
    private Integer standardCancellationRefundPercent;

    @Column(name = "no_show_refund_percent", nullable = false)
    private Integer noShowRefundPercent;

    @Column(name = "max_booking_duration_minutes", nullable = false)
    private Integer maxBookingDurationMinutes;

    @Column(name = "max_booking_ahead_days", nullable = false)
    private Integer maxBookingAheadDays;
}
