package resenkov.work.parkingreservationservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import resenkov.work.parkingreservationservice.client.AccountServiceClient;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.entity.ReservationPolicySettings;
import resenkov.work.parkingreservationservice.repository.ParkingSpotRepository;
import resenkov.work.parkingreservationservice.repository.ReservationRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Log4j2
public class ReservationCreationService {

    private final ParkingSpotRepository spotRepository;
    private final ReservationRepository reservationRepository;
    private final AccountServiceClient accountServiceClient;
    private final ReservationPolicyService reservationPolicyService;
    private final ReservationRules rules;
    private final ReservationHistoryRecorder historyRecorder;
    private final Clock clock;

    public ReservationCreationService(ParkingSpotRepository spotRepository,
                                      ReservationRepository reservationRepository,
                                      AccountServiceClient accountServiceClient,
                                      ReservationPolicyService reservationPolicyService,
                                      ReservationRules rules,
                                      ReservationHistoryRecorder historyRecorder,
                                      Clock clock) {
        this.spotRepository = spotRepository;
        this.reservationRepository = reservationRepository;
        this.accountServiceClient = accountServiceClient;
        this.reservationPolicyService = reservationPolicyService;
        this.rules = rules;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    public Reservation createReservation(Long userId, String userEmail, String spotCode, LocalDateTime from, LocalDateTime to) {
        LocalDateTime now = currentTime();
        ReservationPolicySettings settings = reservationPolicyService.getSettings();
        rules.validateBookingWindow(now, from, to, settings);

        ParkingSpot spot = spotRepository.findByCode(spotCode)
                .orElseThrow(() -> new EntityNotFoundException("Parking spot not found: " + spotCode));

        List<Reservation> overlaps = reservationRepository.findOverlappingReservations(
                spot.getId(),
                from,
                to,
                rules.busyReservationStatuses()
        );
        if (!overlaps.isEmpty()) {
            throw new IllegalStateException("Parking spot is already reserved for the selected interval");
        }

        Reservation reservation = new Reservation();
        reservation.setSpotId(spot.getId());
        reservation.setSpotCode(spot.getCode());
        reservation.setUserId(userId);
        reservation.setUserEmail(userEmail);
        reservation.setStartTime(from);
        reservation.setEndTime(to);
        reservation.setHoldExpiresAt(now.plusMinutes(settings.getHoldDurationMinutes()));
        reservation.setArrivalDeadline(rules.calculateArrivalDeadline(to, settings));
        reservation.setTotalAmount(rules.calculateAmount(spot.getPrice(), from, to, settings));
        reservation.setRefundAmount(BigDecimal.ZERO);
        reservation.setRefundPercent(0);
        reservation.setStatus(Reservation.ReservationStatus.PENDING_HOLD);

        Reservation saved = reservationRepository.save(reservation);
        historyRecorder.saveHistory(saved, "BOOK_REQUEST", userEmail, "spotCode=" + spotCode + ",from=" + from + ",to=" + to);

        String holdOperationId = rules.operationId(saved.getId(), "hold");
        try {
            accountServiceClient.applyReservationEvent(
                    saved,
                    Reservation.ReservationStatus.HOLD,
                    null,
                    holdOperationId
            );
        } catch (RuntimeException ex) {
            saved.setStatus(Reservation.ReservationStatus.HOLD_FAILED);
            Reservation failed = reservationRepository.save(saved);
            historyRecorder.saveHistory(failed, "HOLD_FAILED", userEmail, ex.getMessage());
            log.error("Failed to create hold for reservation {}", failed.getId(), ex);
            return failed;
        }

        saved.setStatus(Reservation.ReservationStatus.HOLD);
        saved.setHoldExpiresAt(currentTime().plusMinutes(settings.getHoldDurationMinutes()));
        Reservation held = reservationRepository.save(saved);
        historyRecorder.saveHistory(held, "HOLD_OK", userEmail, "hold completed");
        return held;
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(clock);
    }
}
