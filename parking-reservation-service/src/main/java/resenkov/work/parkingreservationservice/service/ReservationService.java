package resenkov.work.parkingreservationservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkingreservationservice.client.AccountServiceClient;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.entity.ReservationStateHistory;
import resenkov.work.parkingreservationservice.repository.ParkingSpotRepository;
import resenkov.work.parkingreservationservice.repository.ReservationRepository;
import resenkov.work.parkingreservationservice.repository.ReservationStateHistoryRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Log4j2
public class ReservationService {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);
    private static final Duration ARRIVAL_WINDOW = Duration.ofMinutes(15);
    private static final Duration SLOT_STEP = Duration.ofMinutes(15);
    private static final Duration MAX_BOOKING_DURATION = Duration.ofHours(12);
    private static final Duration MAX_BOOKING_AHEAD = Duration.ofDays(7);

    private final ParkingSpotRepository spotRepo;
    private final ReservationRepository resRepo;
    private final ReservationStateHistoryRepository historyRepository;
    private final AccountServiceClient accountServiceClient;

    public ReservationService(ParkingSpotRepository spotRepo,
                              ReservationRepository resRepo,
                              ReservationStateHistoryRepository historyRepository,
                              AccountServiceClient accountServiceClient) {
        this.spotRepo = spotRepo;
        this.resRepo = resRepo;
        this.historyRepository = historyRepository;
        this.accountServiceClient = accountServiceClient;
    }

    @Transactional
    public Reservation createReservation(Long userId, String userEmail, String spotCode, LocalDateTime from, LocalDateTime to) {
        log.info(
                "Старт создания брони: userId={}, email={}, spotCode={}, from={}, to={}",
                userId,
                userEmail,
                spotCode,
                from,
                to
        );
        LocalDateTime now = LocalDateTime.now();
        validateBookingWindow(now, from, to);

        ParkingSpot spot = spotRepo.findByCode(spotCode)
                .orElseThrow(() -> new EntityNotFoundException("Место для бронирования не найдено: " + spotCode));

        List<Reservation> overlaps = resRepo.findOverlappingReservations(
                spot.getId(), from, to,
                List.of(
                        Reservation.ReservationStatus.PENDING_HOLD,
                        Reservation.ReservationStatus.HOLD,
                        Reservation.ReservationStatus.CONFIRMED,
                        Reservation.ReservationStatus.ACTIVE
                )
        );
        if (!overlaps.isEmpty()) {
            log.warn("Место уже занято в выбранный интервал: spotCode={}, overlaps={}", spotCode, overlaps.size());
            throw new IllegalStateException("Место уже зарезервировано на выбранное время");
        }

        Reservation reservation = new Reservation();
        reservation.setSpotId(spot.getId());
        reservation.setSpotCode(spot.getCode());
        reservation.setUserId(userId);
        reservation.setUserEmail(userEmail);
        reservation.setStartTime(from);
        reservation.setEndTime(to);
        reservation.setHoldExpiresAt(now.plus(HOLD_DURATION));
        reservation.setArrivalDeadline(from.plus(ARRIVAL_WINDOW));
        reservation.setTotalAmount(calculateAmount(spot.getPrice(), from, to));
        reservation.setRefundAmount(BigDecimal.ZERO);
        reservation.setRefundPercent(0);
        reservation.setStatus(Reservation.ReservationStatus.PENDING_HOLD);

        Reservation saved = resRepo.save(reservation);
        log.info(
                "Бронь сохранена в статусе PENDING_HOLD: reservationId={}, amount={}",
                saved.getId(),
                saved.getTotalAmount()
        );
        saveHistory(saved, "BOOK_REQUEST", userEmail, "spotCode=" + spotCode + ",from=" + from + ",to=" + to);

        String holdOperationId = operationId(saved.getId(), "hold");
        log.info("Отправляем запрос на HOLD в account-service: reservationId={}, operationId={}", saved.getId(), holdOperationId);
        try {
            accountServiceClient.applyReservationEvent(
                    saved,
                    Reservation.ReservationStatus.HOLD,
                    null,
                    holdOperationId
            );
        } catch (RuntimeException ex) {
            saved.setStatus(Reservation.ReservationStatus.HOLD_FAILED);
            Reservation failed = resRepo.save(saved);
            saveHistory(failed, "HOLD_FAILED", userEmail, ex.getMessage());
            log.error("Не удалось выполнить HOLD в account-service: reservationId={}", failed.getId(), ex);
            return failed;
        }

        saved.setStatus(Reservation.ReservationStatus.HOLD);
        saved.setHoldExpiresAt(LocalDateTime.now().plus(HOLD_DURATION));
        Reservation held = resRepo.save(saved);
        saveHistory(held, "HOLD_OK", userEmail, "hold успешно выполнен");
        log.info("HOLD успешно выполнен: reservationId={}, holdExpiresAt={}", held.getId(), held.getHoldExpiresAt());
        return held;
    }

    public List<Reservation> findByUserEmail(String email) {
        log.info("Запрос списка броней пользователя: email={}", email);
        return resRepo.findByUserEmail(email);
    }

    @Transactional
    public Reservation confirmReservation(Long reservationId, String userEmail) {
        log.info("Подтверждение брони: reservationId={}, email={}", reservationId, userEmail);
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        if (reservation.getStatus() != Reservation.ReservationStatus.HOLD) {
            throw new IllegalStateException("Подтверждать можно только бронь в статусе HOLD");
        }
        if (LocalDateTime.now().isAfter(reservation.getHoldExpiresAt())) {
            expireReservation(reservation);
            throw new IllegalStateException("Время HOLD истекло");
        }

        reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "CONFIRM", userEmail, "подтверждение пользователем после успешного hold");
        log.info("Бронь подтверждена: reservationId={}", saved.getId());
        return saved;
    }

    @Transactional
    public Reservation activateReservation(Long reservationId, String userEmail) {
        log.info("Активация брони: reservationId={}, email={}", reservationId, userEmail);
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        LocalDateTime now = LocalDateTime.now();

        if (reservation.getStatus() != Reservation.ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Активировать можно только подтверждённую бронь");
        }
        if (now.isAfter(reservation.getArrivalDeadline())) {
            markNoShow(reservation);
            throw new IllegalStateException("Окно прибытия пропущено");
        }

        String captureOperationId = operationId(reservation.getId(), "capture");
        log.info("Отправляем запрос CAPTURE в account-service: reservationId={}, operationId={}", reservation.getId(), captureOperationId);
        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.ACTIVE,
                null,
                captureOperationId
        );

        reservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        markSpotOccupied(reservation.getSpotId());
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "ACTIVATE", userEmail, "факт прибытия зафиксирован");
        log.info("Бронь активирована: reservationId={}", saved.getId());
        return saved;
    }

    @Transactional
    public Reservation completeReservation(Long reservationId, String userEmail) {
        log.info("Завершение брони: reservationId={}, email={}", reservationId, userEmail);
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Завершить можно только ACTIVE-бронирование");
        }

        reservation.setStatus(Reservation.ReservationStatus.COMPLETED);
        releaseSpot(reservation.getSpotId());
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "COMPLETE", userEmail, "сессия завершена");
        log.info("Бронь завершена: reservationId={}", saved.getId());
        return saved;
    }

    @Transactional
    public Reservation cancelReservation(Long reservationId, String userEmail) {
        log.info("Отмена брони: reservationId={}, email={}", reservationId, userEmail);
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        LocalDateTime now = LocalDateTime.now();

        if (reservation.getStatus() == Reservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Активную бронь нельзя отменить");
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.COMPLETED ||
                reservation.getStatus() == Reservation.ReservationStatus.CANCELLED ||
                reservation.getStatus() == Reservation.ReservationStatus.EXPIRED ||
                reservation.getStatus() == Reservation.ReservationStatus.NO_SHOW) {
            throw new IllegalStateException("Бронь уже финализирована");
        }

        int percent = calculateCancellationRefundPercent(reservation, now);
        BigDecimal refund = calculateRefund(reservation.getTotalAmount(), percent);

        String cancelOperationId = operationId(reservation.getId(), "cancelled");
        log.info(
                "Отправляем событие CANCELLED в account-service: reservationId={}, refundPercent={}, operationId={}",
                reservation.getId(),
                percent,
                cancelOperationId
        );
        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.CANCELLED,
                percent,
                cancelOperationId
        );

        reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
        reservation.setRefundPercent(percent);
        reservation.setRefundAmount(refund);
        releaseSpot(reservation.getSpotId());

        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "CANCEL", userEmail, "refundPercent=" + percent + ",refundAmount=" + refund);
        log.info(
                "Бронь отменена: reservationId={}, refundPercent={}, refundAmount={}",
                saved.getId(),
                percent,
                refund
        );
        return saved;
    }

    @Transactional
    @Scheduled(fixedDelay = 30_000)
    public void expireHolds() {
        List<Reservation> expired = resRepo.findByStatusAndHoldExpiresAtBefore(
                Reservation.ReservationStatus.HOLD,
                LocalDateTime.now()
        );
        if (!expired.isEmpty()) {
            log.info("Запущено закрытие просроченных HOLD: count={}", expired.size());
        }
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
        if (!noShows.isEmpty()) {
            log.info("Запущено закрытие NO_SHOW: count={}", noShows.size());
        }
        for (Reservation reservation : noShows) {
            markNoShow(reservation);
        }
    }

    private Reservation getOwnedReservation(Long reservationId, String userEmail) {
        Reservation reservation = resRepo.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Бронь не найдена"));
        if (!reservation.getUserEmail().equals(userEmail)) {
            throw new SecurityException("Бронь принадлежит другому пользователю");
        }
        return reservation;
    }

    private void validateBookingWindow(LocalDateTime now, LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Время начала и окончания обязательно");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("Время окончания должно быть позже времени начала");
        }
        if (from.isBefore(now)) {
            throw new IllegalArgumentException("Время начала не может быть в прошлом");
        }

        Duration duration = Duration.between(from, to);
        if (duration.compareTo(MAX_BOOKING_DURATION) > 0) {
            throw new IllegalArgumentException("Максимальная длительность брони — 12 часов");
        }

        if (from.isAfter(now.plus(MAX_BOOKING_AHEAD))) {
            throw new IllegalArgumentException("Бронирование вперёд доступно максимум на 7 дней");
        }

        if (from.getMinute() % 15 != 0 || to.getMinute() % 15 != 0 || duration.toMinutes() % 15 != 0) {
            throw new IllegalArgumentException("Шаг бронирования должен быть кратен 15 минутам");
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
        String operationId = operationId(reservation.getId(), "expired");
        log.info("Отправляем событие EXPIRED в account-service: reservationId={}, operationId={}", reservation.getId(), operationId);
        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.EXPIRED,
                0,
                operationId
        );

        reservation.setStatus(Reservation.ReservationStatus.EXPIRED);
        reservation.setRefundAmount(BigDecimal.ZERO);
        reservation.setRefundPercent(0);
        releaseSpot(reservation.getSpotId());
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "EXPIRE_HOLD", "SYSTEM", "hold истёк через 5 минут");
        log.info("HOLD истёк: reservationId={}", saved.getId());
    }

    private void markNoShow(Reservation reservation) {
        String operationId = operationId(reservation.getId(), "no-show");
        log.info("Отправляем событие NO_SHOW в account-service: reservationId={}, operationId={}", reservation.getId(), operationId);
        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.NO_SHOW,
                0,
                operationId
        );

        reservation.setStatus(Reservation.ReservationStatus.NO_SHOW);
        reservation.setRefundAmount(BigDecimal.ZERO);
        reservation.setRefundPercent(0);
        releaseSpot(reservation.getSpotId());
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "NO_SHOW", "SYSTEM", "окно прибытия пропущено");
        log.info("Бронь переведена в NO_SHOW: reservationId={}", saved.getId());
    }

    private String operationId(Long reservationId, String action) {
        return "reservation-" + reservationId + "-" + action;
    }

    private void saveHistory(Reservation reservation, String action, String requestedBy, String requestDetails) {
        ReservationStateHistory history = new ReservationStateHistory();
        history.setReservationId(reservation.getId());
        history.setStatus(reservation.getStatus());
        history.setAction(action);
        history.setRequestedBy(requestedBy);
        history.setRequestDetails(requestDetails);
        LocalDateTime now = LocalDateTime.now();
        history.setRequestDate(now);
        history.setLastUpdatedAt(now);
        historyRepository.save(history);
    }

    private void markSpotOccupied(Long spotId) {
        ParkingSpot spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Парковочное место не найдено"));
        spot.setOccupied(true);
        spotRepo.save(spot);
        log.info("Парковочное место занято: spotId={}", spotId);
    }

    private void releaseSpot(Long spotId) {
        ParkingSpot spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Парковочное место не найдено"));
        spot.setOccupied(false);
        spotRepo.save(spot);
        log.info("Парковочное место освобождено: spotId={}", spotId);
    }
}
