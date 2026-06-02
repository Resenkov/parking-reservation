package resenkov.work.parkingreservationservice.service;

import org.springframework.stereotype.Service;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.entity.ReservationPolicySettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ReservationRules {

    private static final List<Reservation.ReservationStatus> BUSY_RESERVATION_STATUSES = List.of(
            Reservation.ReservationStatus.PENDING_HOLD,
            Reservation.ReservationStatus.HOLD,
            Reservation.ReservationStatus.CONFIRMED,
            Reservation.ReservationStatus.ACTIVE
    );

    public List<Reservation.ReservationStatus> busyReservationStatuses() {
        return BUSY_RESERVATION_STATUSES;
    }

    public void validateBookingWindow(LocalDateTime now,
                                      LocalDateTime from,
                                      LocalDateTime to,
                                      ReservationPolicySettings settings) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Reservation start and end time are required");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("Reservation end time must be after start time");
        }
        if (from.isBefore(now)) {
            throw new IllegalArgumentException("Reservation start time cannot be in the past");
        }

        Duration duration = Duration.between(from, to);
        if (duration.toMinutes() > settings.getMaxBookingDurationMinutes()) {
            throw new IllegalArgumentException("Maximum reservation duration is " + settings.getMaxBookingDurationMinutes() + " minutes");
        }
        if (from.isAfter(now.plusDays(settings.getMaxBookingAheadDays()))) {
            throw new IllegalArgumentException("Advance reservation is limited to " + settings.getMaxBookingAheadDays() + " days");
        }
        if (!isAlignedToStep(from, settings.getBookingStepMinutes())
                || !isAlignedToStep(to, settings.getBookingStepMinutes())
                || duration.toMinutes() % settings.getBookingStepMinutes() != 0) {
            throw new IllegalArgumentException("Reservation step must be aligned to " + settings.getBookingStepMinutes() + " minutes");
        }
    }

    public void validateSpotSearchWindow(LocalDateTime now,
                                         LocalDateTime from,
                                         LocalDateTime to,
                                         ReservationPolicySettings settings) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Search interval is required");
        }
        validateBookingWindow(now, from, to, settings);
    }

    public BigDecimal calculateAmount(BigDecimal pricePerStep,
                                      LocalDateTime from,
                                      LocalDateTime to,
                                      ReservationPolicySettings settings) {
        long steps = Duration.between(from, to).toMinutes() / settings.getBookingStepMinutes();
        return pricePerStep.multiply(BigDecimal.valueOf(steps)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateRefund(BigDecimal total, int percent) {
        return total.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public LocalDateTime calculateArrivalDeadline(LocalDateTime endTime, ReservationPolicySettings settings) {
        return endTime.minusMinutes(settings.getArrivalDeadlineMinutesBeforeEnd());
    }

    public boolean hasReachedOrPassed(LocalDateTime now, LocalDateTime deadline) {
        return deadline != null && !now.isBefore(deadline);
    }

    public String operationId(Long reservationId, String action) {
        return "reservation-" + reservationId + "-" + action;
    }

    public String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public String findLevelForZone(List<ParkingSpot> spots, String zoneCode) {
        return spots.stream()
                .filter(spot -> spot.getZone() != null && zoneCode.equalsIgnoreCase(spot.getZone()))
                .map(ParkingSpot::getLevel)
                .map(this::normalizeFilter)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean isAlignedToStep(LocalDateTime value, int stepMinutes) {
        if (value.getSecond() != 0 || value.getNano() != 0) {
            return false;
        }
        long totalMinutes = value.toLocalDate().toEpochDay() * 24 * 60L
                + value.getHour() * 60L
                + value.getMinute();
        return totalMinutes % stepMinutes == 0;
    }
}
