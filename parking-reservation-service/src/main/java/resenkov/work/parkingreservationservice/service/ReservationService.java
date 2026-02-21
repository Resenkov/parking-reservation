package resenkov.work.parkingreservationservice.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkingreservationservice.dto.ReservationCreatedEvent;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.kafka.ReservationEventProducer;
import resenkov.work.parkingreservationservice.repository.ParkingSpotRepository;
import resenkov.work.parkingreservationservice.repository.ReservationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);
    private static final Duration ARRIVAL_WINDOW = Duration.ofMinutes(15);
    private static final Duration SLOT_STEP = Duration.ofMinutes(15);
    private static final Duration MAX_BOOKING_DURATION = Duration.ofHours(12);
    private static final Duration MAX_BOOKING_AHEAD = Duration.ofDays(7);

    private final ParkingSpotRepository spotRepo;
    private final ReservationRepository resRepo;
    private final ReservationEventProducer producer;

    public ReservationService(ParkingSpotRepository spotRepo,
                              ReservationRepository resRepo,
                              ReservationEventProducer producer) {
        this.spotRepo = spotRepo;
        this.resRepo = resRepo;
        this.producer = producer;
    }

    @Transactional
    public Reservation createReservation(String userEmail, String spotCode, LocalDateTime from, LocalDateTime to) {
        LocalDateTime now = LocalDateTime.now();
        validateBookingWindow(now, from, to);

        ParkingSpot spot = spotRepo.findByCode(spotCode)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));

        List<Reservation> overlaps = resRepo.findOverlappingReservations(
                spot.getId(), from, to,
                List.of(Reservation.ReservationStatus.HOLD,
                        Reservation.ReservationStatus.CONFIRMED,
                        Reservation.ReservationStatus.ACTIVE)
        );
        if (!overlaps.isEmpty()) {
            throw new IllegalStateException("Spot already reserved for selected time");
        }

        Reservation r = new Reservation();
        r.setSpotId(spot.getId());
        r.setSpotCode(spot.getCode());
        r.setUserEmail(userEmail);
        r.setStartTime(from);
        r.setEndTime(to);
        r.setHoldExpiresAt(now.plus(HOLD_DURATION));
        r.setArrivalDeadline(from.plus(ARRIVAL_WINDOW));

        BigDecimal amount = calculateAmount(spot.getPrice(), from, to);
        r.setTotalAmount(amount);
        r.setRefundAmount(BigDecimal.ZERO);
        r.setRefundPercent(0);
        r.setStatus(Reservation.ReservationStatus.HOLD);


        Reservation saved = resRepo.save(r);

        ReservationCreatedEvent event = new ReservationCreatedEvent(
                saved.getId(), saved.getSpotCode(), saved.getUserEmail(), saved.getEndTime());
        producer.publishReservationCreated(event);

        return saved;
    }

    public List<Reservation> findByUserEmail(String email) {
        return resRepo.findByUserEmail(email);
    }

    @Transactional
    public Reservation confirmReservation(Long reservationId, String userEmail) {
        Reservation r = getOwnedReservation(reservationId, userEmail);
        if (r.getStatus() != Reservation.ReservationStatus.HOLD) {
            throw new IllegalStateException("Only HOLD reservation can be confirmed");
        }
        if (LocalDateTime.now().isAfter(r.getHoldExpiresAt())) {
            expireReservation(r);
            throw new IllegalStateException("Hold time expired");
        }
        r.setStatus(Reservation.ReservationStatus.CONFIRMED);
        return resRepo.save(r);
    }

    @Transactional
    public Reservation activateReservation(Long reservationId, String userEmail) {
        Reservation r = getOwnedReservation(reservationId, userEmail);
        LocalDateTime now = LocalDateTime.now();

        if (r.getStatus() != Reservation.ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED reservation can be activated");
        }
        if (now.isAfter(r.getArrivalDeadline())) {
            markNoShow(r);
            throw new IllegalStateException("Arrival window missed. Reservation marked as NO_SHOW");
        }

        r.setStatus(Reservation.ReservationStatus.ACTIVE);
        markSpotOccupied(r.getSpotId());
        return resRepo.save(r);
    }

    @Transactional
    public Reservation completeReservation(Long reservationId, String userEmail) {
        Reservation r = getOwnedReservation(reservationId, userEmail);
        if (r.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE reservation can be completed");
        }
        r.setStatus(Reservation.ReservationStatus.COMPLETED);
        releaseSpot(r.getSpotId());
        return resRepo.save(r);
    }

    @Transactional
    public Reservation cancelReservation(Long reservationId, String userEmail) {
        Reservation r = getOwnedReservation(reservationId, userEmail);
        LocalDateTime now = LocalDateTime.now();

        if (r.getStatus() == Reservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("If place is occupied, cancellation is not allowed. Complete reservation instead");
        }
        if (r.getStatus() == Reservation.ReservationStatus.COMPLETED ||
                r.getStatus() == Reservation.ReservationStatus.CANCELLED ||
                r.getStatus() == Reservation.ReservationStatus.EXPIRED ||
                r.getStatus() == Reservation.ReservationStatus.NO_SHOW) {
            throw new IllegalStateException("Reservation already finalized");
        }

        int percent = calculateCancellationRefundPercent(r, now);
        BigDecimal refund = calculateRefund(r.getTotalAmount(), percent);

        r.setStatus(Reservation.ReservationStatus.CANCELLED);
        r.setRefundPercent(percent);
        r.setRefundAmount(refund);

        releaseSpot(r.getSpotId());
        return resRepo.save(r);
    }

    @Transactional
    @Scheduled(fixedDelay = 30_000)
    public void expireHolds() {
        List<Reservation> expired = resRepo.findByStatusAndHoldExpiresAtBefore(
                Reservation.ReservationStatus.HOLD,
                LocalDateTime.now()
        );
        for (Reservation reservation : expired) {
            expireReservation(reservation);
        }
    }

    @Transactional
    @Scheduled(fixedDelay = 30_000)
    public void closeNoShows() {
        List<Reservation> noShows = resRepo.findByStatusAndArrivalDeadlineBefore(
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.now()
        );
        for (Reservation reservation : noShows) {
            markNoShow(reservation);
        }
    }

    private Reservation getOwnedReservation(Long reservationId, String userEmail) {
        Reservation reservation = resRepo.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found"));
        if (!reservation.getUserEmail().equals(userEmail)) {
            throw new SecurityException("Not your reservation");
        }
        return reservation;
    }

    private void validateBookingWindow(LocalDateTime now, LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Start and end time are required");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (from.isBefore(now)) {
            throw new IllegalArgumentException("Start time cannot be in the past");
        }

        Duration duration = Duration.between(from, to);
        if (duration.compareTo(MAX_BOOKING_DURATION) > 0) {
            throw new IllegalArgumentException("Max reservation duration is 12 hours");
        }

        if (from.isAfter(now.plus(MAX_BOOKING_AHEAD))) {
            throw new IllegalArgumentException("Reservation ahead is allowed up to 7 days");
        }

        if (from.getMinute() % 15 != 0 || to.getMinute() % 15 != 0 || duration.toMinutes() % 15 != 0) {
            throw new IllegalArgumentException("Reservation step must be 15 minutes");
        }
    }

    private BigDecimal calculateAmount(BigDecimal pricePerStep, LocalDateTime from, LocalDateTime to) {
        long steps = Duration.between(from, to).toMinutes() / SLOT_STEP.toMinutes();
        return pricePerStep.multiply(BigDecimal.valueOf(steps)).setScale(2, RoundingMode.HALF_UP);
    }

    private int calculateCancellationRefundPercent(Reservation reservation, LocalDateTime now) {
        LocalDateTime start = reservation.getStartTime();

        if (now.isBefore(start.minusMinutes(60))) {
            return 100;
        }
        if (!now.isBefore(start.minusMinutes(15))) {
            if (now.isBefore(start)) {
                return 60;
            }
            return 30;
        }
        return 80;
    }

    private BigDecimal calculateRefund(BigDecimal total, int percent) {
        return total.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void expireReservation(Reservation reservation) {
        reservation.setStatus(Reservation.ReservationStatus.EXPIRED);
        reservation.setRefundAmount(BigDecimal.ZERO);
        reservation.setRefundPercent(0);
        releaseSpot(reservation.getSpotId());
        resRepo.save(reservation);
    }

    private void markNoShow(Reservation reservation) {
        reservation.setStatus(Reservation.ReservationStatus.NO_SHOW);
        reservation.setRefundAmount(BigDecimal.ZERO);
        reservation.setRefundPercent(0);
        releaseSpot(reservation.getSpotId());
        resRepo.save(reservation);
    }


    private void markSpotOccupied(Long spotId) {
        ParkingSpot spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
        spot.setOccupied(true);
        spotRepo.save(spot);
    }

    private void releaseSpot(Long spotId) {
        ParkingSpot spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
        spot.setOccupied(false);
        spotRepo.save(spot);
    }
}
