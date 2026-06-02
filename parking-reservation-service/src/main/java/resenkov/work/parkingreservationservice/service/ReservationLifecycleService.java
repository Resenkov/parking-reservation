package resenkov.work.parkingreservationservice.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import resenkov.work.parkingreservationservice.client.AccountServiceClient;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.repository.ReservationRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationLifecycleService {

    private static final int FULL_REFUND_PERCENT = 100;

    private final ReservationRepository reservationRepository;
    private final AccountServiceClient accountServiceClient;
    private final ReservationPolicyService reservationPolicyService;
    private final ReservationRules rules;
    private final ReservationHistoryRecorder historyRecorder;
    private final ParkingOccupancyService occupancyService;
    private final Clock clock;

    public ReservationLifecycleService(ReservationRepository reservationRepository,
                                       AccountServiceClient accountServiceClient,
                                       ReservationPolicyService reservationPolicyService,
                                       ReservationRules rules,
                                       ReservationHistoryRecorder historyRecorder,
                                       ParkingOccupancyService occupancyService,
                                       Clock clock) {
        this.reservationRepository = reservationRepository;
        this.accountServiceClient = accountServiceClient;
        this.reservationPolicyService = reservationPolicyService;
        this.rules = rules;
        this.historyRecorder = historyRecorder;
        this.occupancyService = occupancyService;
        this.clock = clock;
    }

    public Reservation confirmReservation(Long reservationId, String userEmail) {
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        if (reservation.getStatus() != Reservation.ReservationStatus.HOLD) {
            throw new IllegalStateException("Only HOLD reservations can be confirmed");
        }
        if (rules.hasReachedOrPassed(currentTime(), reservation.getHoldExpiresAt())) {
            return expireReservation(reservation);
        }

        reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);
        Reservation saved = reservationRepository.save(reservation);
        historyRecorder.saveHistory(saved, "CONFIRM", userEmail, "reservation confirmed");
        return saved;
    }

    public Reservation activateReservation(Long reservationId, String userEmail) {
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        LocalDateTime now = currentTime();

        if (reservation.getStatus() != Reservation.ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED reservations can be activated");
        }
        if (now.isBefore(reservation.getStartTime())) {
            throw new IllegalStateException("Check-in is available only after reservation start time");
        }
        if (rules.hasReachedOrPassed(now, reservation.getArrivalDeadline())) {
            return markNoShow(reservation);
        }

        String captureOperationId = rules.operationId(reservation.getId(), "capture");
        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.ACTIVE,
                null,
                captureOperationId
        );

        reservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        occupancyService.markSpotOccupied(reservation.getSpotId());
        Reservation saved = reservationRepository.save(reservation);
        historyRecorder.saveHistory(saved, "ACTIVATE", userEmail, "reservation activated");
        return saved;
    }

    public Reservation completeReservation(Long reservationId, String userEmail) {
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE reservations can be completed");
        }

        return finalizeCompletedReservation(
                reservation,
                "COMPLETE",
                userEmail,
                "reservation completed by user"
        );
    }

    public Reservation cancelReservation(Long reservationId, String userEmail) {
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        LocalDateTime now = currentTime();

        if (reservation.getStatus() == Reservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Active reservation cannot be cancelled");
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.HOLD_FAILED
                || reservation.getStatus() == Reservation.ReservationStatus.PENDING_HOLD) {
            Reservation.ReservationStatus previousStatus = reservation.getStatus();
            reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
            reservation.setRefundPercent(0);
            reservation.setRefundAmount(BigDecimal.ZERO);
            occupancyService.refreshSpotOccupancy(reservation.getSpotId());

            Reservation saved = reservationRepository.save(reservation);
            historyRecorder.saveHistory(saved, "CANCEL", userEmail, "billing was not started, previousStatus=" + previousStatus);
            return saved;
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.COMPLETED
                || reservation.getStatus() == Reservation.ReservationStatus.CANCELLED
                || reservation.getStatus() == Reservation.ReservationStatus.EXPIRED
                || reservation.getStatus() == Reservation.ReservationStatus.NO_SHOW) {
            throw new IllegalStateException("Reservation is already finalized");
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.HOLD
                && rules.hasReachedOrPassed(now, reservation.getHoldExpiresAt())) {
            return expireReservation(reservation);
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.HOLD) {
            return cancelReservationWithRefund(reservation, userEmail, FULL_REFUND_PERCENT);
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.CONFIRMED
                && rules.hasReachedOrPassed(now, reservation.getArrivalDeadline())) {
            return markNoShow(reservation);
        }

        return cancelReservationWithRefund(
                reservation,
                userEmail,
                reservationPolicyService.getSettings().getStandardCancellationRefundPercent()
        );
    }

    public void expireHolds() {
        List<Reservation> expired = reservationRepository.findByStatusAndHoldExpiresAtLessThanEqual(
                Reservation.ReservationStatus.HOLD,
                currentTime()
        );
        for (Reservation reservation : expired) {
            expireReservation(reservation);
        }
    }

    public void closeNoShows() {
        List<Reservation> noShows = reservationRepository.findByStatusAndArrivalDeadlineLessThanEqual(
                Reservation.ReservationStatus.CONFIRMED,
                currentTime()
        );
        for (Reservation reservation : noShows) {
            markNoShow(reservation);
        }
    }

    public void completeFinishedReservations() {
        List<Reservation> completed = reservationRepository.findByStatusAndEndTimeLessThanEqual(
                Reservation.ReservationStatus.ACTIVE,
                currentTime()
        );
        for (Reservation reservation : completed) {
            completeExpiredActiveReservation(reservation);
        }
    }

    private Reservation getOwnedReservation(Long reservationId, String userEmail) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found"));
        if (!reservation.getUserEmail().equals(userEmail)) {
            throw new SecurityException("Reservation belongs to another user");
        }
        return reservation;
    }

    private Reservation expireReservation(Reservation reservation) {
        String operationId = rules.operationId(reservation.getId(), "expired");
        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.EXPIRED,
                0,
                operationId
        );

        reservation.setStatus(Reservation.ReservationStatus.EXPIRED);
        reservation.setRefundAmount(BigDecimal.ZERO);
        reservation.setRefundPercent(0);
        occupancyService.refreshSpotOccupancy(reservation.getSpotId());
        Reservation saved = reservationRepository.save(reservation);
        historyRecorder.saveHistory(saved, "EXPIRE_HOLD", "SYSTEM", "hold expired");
        return saved;
    }

    private Reservation markNoShow(Reservation reservation) {
        int refundPercent = reservationPolicyService.getSettings().getNoShowRefundPercent();
        BigDecimal refundAmount = rules.calculateRefund(reservation.getTotalAmount(), refundPercent);
        String operationId = rules.operationId(reservation.getId(), "no-show");
        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.NO_SHOW,
                refundPercent,
                operationId
        );

        reservation.setStatus(Reservation.ReservationStatus.NO_SHOW);
        reservation.setRefundAmount(refundAmount);
        reservation.setRefundPercent(refundPercent);
        occupancyService.refreshSpotOccupancy(reservation.getSpotId());
        Reservation saved = reservationRepository.save(reservation);
        historyRecorder.saveHistory(saved, "NO_SHOW", "SYSTEM", "arrival deadline missed, refundPercent=" + refundPercent);
        return saved;
    }

    private Reservation cancelReservationWithRefund(Reservation reservation, String userEmail, int percent) {
        BigDecimal refund = rules.calculateRefund(reservation.getTotalAmount(), percent);
        String cancelOperationId = rules.operationId(reservation.getId(), "cancelled");

        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.CANCELLED,
                percent,
                cancelOperationId
        );

        reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
        reservation.setRefundPercent(percent);
        reservation.setRefundAmount(refund);
        occupancyService.refreshSpotOccupancy(reservation.getSpotId());

        Reservation saved = reservationRepository.save(reservation);
        historyRecorder.saveHistory(saved, "CANCEL", userEmail, "refundPercent=" + percent + ",refundAmount=" + refund);
        return saved;
    }

    private void completeExpiredActiveReservation(Reservation reservation) {
        finalizeCompletedReservation(
                reservation,
                "AUTO_COMPLETE",
                "SYSTEM",
                "reservation end time passed"
        );
    }

    private Reservation finalizeCompletedReservation(Reservation reservation,
                                                     String action,
                                                     String requestedBy,
                                                     String requestDetails) {
        reservation.setStatus(Reservation.ReservationStatus.COMPLETED);
        occupancyService.refreshSpotOccupancy(reservation.getSpotId());
        Reservation saved = reservationRepository.save(reservation);
        historyRecorder.saveHistory(saved, action, requestedBy, requestDetails);
        return saved;
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(clock);
    }
}
