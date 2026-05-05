package resenkov.work.parkingreservationservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkingreservationservice.client.AccountServiceClient;
import resenkov.work.parkingreservationservice.dto.ParkingSpotAvailabilityResponse;
import resenkov.work.parkingreservationservice.dto.ReservationCatalogResponse;
import resenkov.work.parkingreservationservice.dto.ReservationLevelOptionResponse;
import resenkov.work.parkingreservationservice.dto.ReservationZoneOptionResponse;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.ParkingZone;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.entity.ReservationStateHistory;
import resenkov.work.parkingreservationservice.repository.ParkingSpotRepository;
import resenkov.work.parkingreservationservice.repository.ParkingZoneRepository;
import resenkov.work.parkingreservationservice.repository.ReservationRepository;
import resenkov.work.parkingreservationservice.repository.ReservationStateHistoryRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
public class ReservationService {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);
    private static final Duration ARRIVAL_WINDOW = Duration.ofMinutes(15);
    private static final Duration SLOT_STEP = Duration.ofMinutes(15);
    private static final Duration MAX_BOOKING_DURATION = Duration.ofHours(12);
    private static final Duration MAX_BOOKING_AHEAD = Duration.ofDays(7);
    private static final long LIFECYCLE_CHECK_DELAY_MS = 10_000L;
    private static final int FULL_REFUND_PERCENT = 100;
    private static final int MEDIUM_REFUND_PERCENT = 80;
    private static final int LATE_REFUND_PERCENT = 60;
    private static final int POST_START_REFUND_PERCENT = 30;
    private static final int NO_SHOW_REFUND_PERCENT = 0;

    private final ParkingSpotRepository spotRepo;
    private final ParkingZoneRepository zoneRepo;
    private final ReservationRepository resRepo;
    private final ReservationStateHistoryRepository historyRepository;
    private final AccountServiceClient accountServiceClient;
    private final Clock clock;

    public ReservationService(ParkingSpotRepository spotRepo,
                              ParkingZoneRepository zoneRepo,
                              ReservationRepository resRepo,
                              ReservationStateHistoryRepository historyRepository,
                              AccountServiceClient accountServiceClient,
                              Clock clock) {
        this.spotRepo = spotRepo;
        this.zoneRepo = zoneRepo;
        this.resRepo = resRepo;
        this.historyRepository = historyRepository;
        this.accountServiceClient = accountServiceClient;
        this.clock = clock;
    }

    @Transactional
    public Reservation createReservation(Long userId, String userEmail, String spotCode, LocalDateTime from, LocalDateTime to) {
        log.info(
                "РЎС‚Р°СЂС‚ СЃРѕР·РґР°РЅРёСЏ Р±СЂРѕРЅРё: userId={}, email={}, spotCode={}, from={}, to={}",
                userId,
                userEmail,
                spotCode,
                from,
                to
        );
        LocalDateTime now = currentTime();
        validateBookingWindow(now, from, to);

        ParkingSpot spot = spotRepo.findByCode(spotCode)
                .orElseThrow(() -> new EntityNotFoundException("РњРµСЃС‚Рѕ РґР»СЏ Р±СЂРѕРЅРёСЂРѕРІР°РЅРёСЏ РЅРµ РЅР°Р№РґРµРЅРѕ: " + spotCode));

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
            log.warn("РњРµСЃС‚Рѕ СѓР¶Рµ Р·Р°РЅСЏС‚Рѕ РІ РІС‹Р±СЂР°РЅРЅС‹Р№ РёРЅС‚РµСЂРІР°Р»: spotCode={}, overlaps={}", spotCode, overlaps.size());
            throw new IllegalStateException("РњРµСЃС‚Рѕ СѓР¶Рµ Р·Р°СЂРµР·РµСЂРІРёСЂРѕРІР°РЅРѕ РЅР° РІС‹Р±СЂР°РЅРЅРѕРµ РІСЂРµРјСЏ");
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
                "Р‘СЂРѕРЅСЊ СЃРѕС…СЂР°РЅРµРЅР° РІ СЃС‚Р°С‚СѓСЃРµ PENDING_HOLD: reservationId={}, amount={}",
                saved.getId(),
                saved.getTotalAmount()
        );
        saveHistory(saved, "BOOK_REQUEST", userEmail, "spotCode=" + spotCode + ",from=" + from + ",to=" + to);

        String holdOperationId = operationId(saved.getId(), "hold");
        log.info("РћС‚РїСЂР°РІР»СЏРµРј Р·Р°РїСЂРѕСЃ РЅР° HOLD РІ account-service: reservationId={}, operationId={}", saved.getId(), holdOperationId);
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
            log.error("РќРµ СѓРґР°Р»РѕСЃСЊ РІС‹РїРѕР»РЅРёС‚СЊ HOLD РІ account-service: reservationId={}", failed.getId(), ex);
            return failed;
        }

        saved.setStatus(Reservation.ReservationStatus.HOLD);
        saved.setHoldExpiresAt(currentTime().plus(HOLD_DURATION));
        Reservation held = resRepo.save(saved);
        saveHistory(held, "HOLD_OK", userEmail, "hold СѓСЃРїРµС€РЅРѕ РІС‹РїРѕР»РЅРµРЅ");
        log.info("HOLD СѓСЃРїРµС€РЅРѕ РІС‹РїРѕР»РЅРµРЅ: reservationId={}, holdExpiresAt={}", held.getId(), held.getHoldExpiresAt());
        return held;
    }

    public List<Reservation> findByUserEmail(String email) {
        log.info("Р—Р°РїСЂРѕСЃ СЃРїРёСЃРєР° Р±СЂРѕРЅРµР№ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ: email={}", email);
        return resRepo.findByUserEmail(email);
    }

    @Transactional(readOnly = true)
    public ReservationCatalogResponse getReservationCatalog() {
        List<ParkingSpot> spots = spotRepo.findAllByOrderByCodeAsc();

        Map<String, Long> spotCountByLevel = spots.stream()
                .map(ParkingSpot::getLevel)
                .map(this::normalizeFilter)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> spotCountByZone = spots.stream()
                .map(ParkingSpot::getZone)
                .map(this::normalizeFilter)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        Map<String, ParkingZone> zoneByCode = zoneRepo.findAllByOrderByCodeAsc().stream()
                .filter(zone -> normalizeFilter(zone.getCode()) != null)
                .collect(Collectors.toMap(
                        zone -> normalizeFilter(zone.getCode()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<ReservationLevelOptionResponse> levels = spotCountByLevel.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String::compareToIgnoreCase))
                .map(entry -> ReservationLevelOptionResponse.builder()
                        .code(entry.getKey())
                        .spotCount(entry.getValue())
                        .build())
                .toList();

        List<ReservationZoneOptionResponse> zones = spotCountByZone.entrySet().stream()
                .map(entry -> {
                    ParkingZone zone = zoneByCode.get(entry.getKey());
                    return ReservationZoneOptionResponse.builder()
                            .code(entry.getKey())
                            .name(zone == null ? null : normalizeNullable(zone.getName()))
                            .level(zone == null ? findLevelForZone(spots, entry.getKey()) : normalizeFilter(zone.getLevel()))
                            .spotCount(entry.getValue())
                            .build();
                })
                .sorted(Comparator
                        .comparing(ReservationZoneOptionResponse::getLevel, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(ReservationZoneOptionResponse::getCode, String::compareToIgnoreCase))
                .toList();

        return ReservationCatalogResponse.builder()
                .levels(levels)
                .zones(zones)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ParkingSpotAvailabilityResponse> findAvailableSpots(LocalDateTime from,
                                                                    LocalDateTime to,
                                                                    String zone,
                                                                    String level) {
        validateSpotSearchWindow(currentTime(), from, to);

        List<ParkingSpot> spots = spotRepo.findAvailableSpots(
                normalizeFilter(zone),
                normalizeFilter(level),
                from,
                to,
                List.of(
                        Reservation.ReservationStatus.PENDING_HOLD,
                        Reservation.ReservationStatus.HOLD,
                        Reservation.ReservationStatus.CONFIRMED,
                        Reservation.ReservationStatus.ACTIVE
                )
        );

        return spots.stream()
                .map(spot -> ParkingSpotAvailabilityResponse.builder()
                        .id(spot.getId())
                        .code(spot.getCode())
                        .zone(spot.getZone())
                        .level(spot.getLevel())
                        .price(spot.getPrice())
                        .occupied(spot.isOccupied())
                        .build())
                .toList();
    }

    @Transactional
    public Reservation confirmReservation(Long reservationId, String userEmail) {
        log.info("РџРѕРґС‚РІРµСЂР¶РґРµРЅРёРµ Р±СЂРѕРЅРё: reservationId={}, email={}", reservationId, userEmail);
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        if (reservation.getStatus() != Reservation.ReservationStatus.HOLD) {
            throw new IllegalStateException("РџРѕРґС‚РІРµСЂР¶РґР°С‚СЊ РјРѕР¶РЅРѕ С‚РѕР»СЊРєРѕ Р±СЂРѕРЅСЊ РІ СЃС‚Р°С‚СѓСЃРµ HOLD");
        }
        if (hasReachedOrPassed(currentTime(), reservation.getHoldExpiresAt())) {
            return expireReservation(reservation);
        }

        reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "CONFIRM", userEmail, "РїРѕРґС‚РІРµСЂР¶РґРµРЅРёРµ РїРѕР»СЊР·РѕРІР°С‚РµР»РµРј РїРѕСЃР»Рµ СѓСЃРїРµС€РЅРѕРіРѕ hold");
        log.info("Р‘СЂРѕРЅСЊ РїРѕРґС‚РІРµСЂР¶РґРµРЅР°: reservationId={}", saved.getId());
        return saved;
    }

    @Transactional
    public Reservation activateReservation(Long reservationId, String userEmail) {
        log.info("РђРєС‚РёРІР°С†РёСЏ Р±СЂРѕРЅРё: reservationId={}, email={}", reservationId, userEmail);
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        LocalDateTime now = currentTime();

        if (reservation.getStatus() != Reservation.ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("РђРєС‚РёРІРёСЂРѕРІР°С‚СЊ РјРѕР¶РЅРѕ С‚РѕР»СЊРєРѕ РїРѕРґС‚РІРµСЂР¶РґС‘РЅРЅСѓСЋ Р±СЂРѕРЅСЊ");
        }
        if (now.isBefore(reservation.getStartTime())) {
            throw new IllegalStateException("Check-in is available only after reservation start time");
        }
        if (hasReachedOrPassed(now, reservation.getArrivalDeadline())) {
            return markNoShow(reservation);
        }

        String captureOperationId = operationId(reservation.getId(), "capture");
        log.info("РћС‚РїСЂР°РІР»СЏРµРј Р·Р°РїСЂРѕСЃ CAPTURE РІ account-service: reservationId={}, operationId={}", reservation.getId(), captureOperationId);
        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.ACTIVE,
                null,
                captureOperationId
        );

        reservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        markSpotOccupied(reservation.getSpotId());
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "ACTIVATE", userEmail, "С„Р°РєС‚ РїСЂРёР±С‹С‚РёСЏ Р·Р°С„РёРєСЃРёСЂРѕРІР°РЅ");
        log.info("Р‘СЂРѕРЅСЊ Р°РєС‚РёРІРёСЂРѕРІР°РЅР°: reservationId={}", saved.getId());
        return saved;
    }

    @Transactional
    public Reservation completeReservation(Long reservationId, String userEmail) {
        log.info("Р—Р°РІРµСЂС€РµРЅРёРµ Р±СЂРѕРЅРё: reservationId={}, email={}", reservationId, userEmail);
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Р—Р°РІРµСЂС€РёС‚СЊ РјРѕР¶РЅРѕ С‚РѕР»СЊРєРѕ ACTIVE-Р±СЂРѕРЅРёСЂРѕРІР°РЅРёРµ");
        }

        Reservation saved = finalizeCompletedReservation(
                reservation,
                "COMPLETE",
                userEmail,
                "СЃРµСЃСЃРёСЏ Р·Р°РІРµСЂС€РµРЅР°"
        );
        log.info("Р‘СЂРѕРЅСЊ Р·Р°РІРµСЂС€РµРЅР°: reservationId={}", saved.getId());
        return saved;
    }

    @Transactional
    public Reservation cancelReservation(Long reservationId, String userEmail) {
        log.info("РћС‚РјРµРЅР° Р±СЂРѕРЅРё: reservationId={}, email={}", reservationId, userEmail);
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        LocalDateTime now = currentTime();

        if (reservation.getStatus() == Reservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("РђРєС‚РёРІРЅСѓСЋ Р±СЂРѕРЅСЊ РЅРµР»СЊР·СЏ РѕС‚РјРµРЅРёС‚СЊ");
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.HOLD_FAILED ||
                reservation.getStatus() == Reservation.ReservationStatus.PENDING_HOLD) {
            Reservation.ReservationStatus previousStatus = reservation.getStatus();
            reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
            reservation.setRefundPercent(0);
            reservation.setRefundAmount(BigDecimal.ZERO);
            refreshSpotOccupancy(reservation.getSpotId());

            Reservation saved = resRepo.save(reservation);
            saveHistory(saved, "CANCEL", userEmail, "hold was not completed, billing was not called");
            log.info("Reservation cancelled without billing: reservationId={}, previousStatus={}", saved.getId(), previousStatus);
            return saved;
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.COMPLETED ||
                reservation.getStatus() == Reservation.ReservationStatus.CANCELLED ||
                reservation.getStatus() == Reservation.ReservationStatus.EXPIRED ||
                reservation.getStatus() == Reservation.ReservationStatus.NO_SHOW) {
            throw new IllegalStateException("Р‘СЂРѕРЅСЊ СѓР¶Рµ С„РёРЅР°Р»РёР·РёСЂРѕРІР°РЅР°");
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.HOLD &&
                hasReachedOrPassed(now, reservation.getHoldExpiresAt())) {
            return expireReservation(reservation);
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.CONFIRMED &&
                hasReachedOrPassed(now, reservation.getArrivalDeadline())) {
            return markNoShow(reservation);
        }

        int percent = calculateCancellationRefundPercent(reservation, now);
        BigDecimal refund = calculateRefund(reservation.getTotalAmount(), percent);

        String cancelOperationId = operationId(reservation.getId(), "cancelled");
        log.info(
                "РћС‚РїСЂР°РІР»СЏРµРј СЃРѕР±С‹С‚РёРµ CANCELLED РІ account-service: reservationId={}, refundPercent={}, operationId={}",
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
        refreshSpotOccupancy(reservation.getSpotId());

        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "CANCEL", userEmail, "refundPercent=" + percent + ",refundAmount=" + refund);
        log.info(
                "Р‘СЂРѕРЅСЊ РѕС‚РјРµРЅРµРЅР°: reservationId={}, refundPercent={}, refundAmount={}",
                saved.getId(),
                percent,
                refund
        );
        return saved;
    }

    @Transactional
    @Scheduled(fixedDelay = LIFECYCLE_CHECK_DELAY_MS)
    public void expireHolds() {
        List<Reservation> expired = resRepo.findByStatusAndHoldExpiresAtLessThanEqual(
                Reservation.ReservationStatus.HOLD,
                currentTime()
        );
        if (!expired.isEmpty()) {
            log.info("Р—Р°РїСѓС‰РµРЅРѕ Р·Р°РєСЂС‹С‚РёРµ РїСЂРѕСЃСЂРѕС‡РµРЅРЅС‹С… HOLD: count={}", expired.size());
        }
        for (Reservation reservation : expired) {
            expireReservation(reservation);
        }
    }

    @Transactional
    @Scheduled(fixedDelay = LIFECYCLE_CHECK_DELAY_MS)
    public void closeNoShows() {
        List<Reservation> noShows = resRepo.findByStatusAndArrivalDeadlineLessThanEqual(
                Reservation.ReservationStatus.CONFIRMED,
                currentTime()
        );
        if (!noShows.isEmpty()) {
            log.info("Р—Р°РїСѓС‰РµРЅРѕ Р·Р°РєСЂС‹С‚РёРµ NO_SHOW: count={}", noShows.size());
        }
        for (Reservation reservation : noShows) {
            markNoShow(reservation);
        }
    }

    @Transactional
    @Scheduled(fixedDelay = LIFECYCLE_CHECK_DELAY_MS)
    public void completeFinishedReservations() {
        List<Reservation> completed = resRepo.findByStatusAndEndTimeLessThanEqual(
                Reservation.ReservationStatus.ACTIVE,
                currentTime()
        );
        if (!completed.isEmpty()) {
            log.info("Running auto-complete for ACTIVE reservations: count={}", completed.size());
        }
        for (Reservation reservation : completed) {
            completeExpiredActiveReservation(reservation);
        }
    }

    private Reservation getOwnedReservation(Long reservationId, String userEmail) {
        Reservation reservation = resRepo.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Р‘СЂРѕРЅСЊ РЅРµ РЅР°Р№РґРµРЅР°"));
        if (!reservation.getUserEmail().equals(userEmail)) {
            throw new SecurityException("Р‘СЂРѕРЅСЊ РїСЂРёРЅР°РґР»РµР¶РёС‚ РґСЂСѓРіРѕРјСѓ РїРѕР»СЊР·РѕРІР°С‚РµР»СЋ");
        }
        return reservation;
    }

    private void validateBookingWindow(LocalDateTime now, LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Р’СЂРµРјСЏ РЅР°С‡Р°Р»Р° Рё РѕРєРѕРЅС‡Р°РЅРёСЏ РѕР±СЏР·Р°С‚РµР»СЊРЅРѕ");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("Р’СЂРµРјСЏ РѕРєРѕРЅС‡Р°РЅРёСЏ РґРѕР»Р¶РЅРѕ Р±С‹С‚СЊ РїРѕР·Р¶Рµ РІСЂРµРјРµРЅРё РЅР°С‡Р°Р»Р°");
        }
        if (from.isBefore(now)) {
            throw new IllegalArgumentException("Р’СЂРµРјСЏ РЅР°С‡Р°Р»Р° РЅРµ РјРѕР¶РµС‚ Р±С‹С‚СЊ РІ РїСЂРѕС€Р»РѕРј");
        }

        Duration duration = Duration.between(from, to);
        if (duration.compareTo(MAX_BOOKING_DURATION) > 0) {
            throw new IllegalArgumentException("РњР°РєСЃРёРјР°Р»СЊРЅР°СЏ РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊ Р±СЂРѕРЅРё вЂ” 12 С‡Р°СЃРѕРІ");
        }

        if (from.isAfter(now.plus(MAX_BOOKING_AHEAD))) {
            throw new IllegalArgumentException("Р‘СЂРѕРЅРёСЂРѕРІР°РЅРёРµ РІРїРµСЂС‘Рґ РґРѕСЃС‚СѓРїРЅРѕ РјР°РєСЃРёРјСѓРј РЅР° 7 РґРЅРµР№");
        }

        if (from.getMinute() % 15 != 0 || to.getMinute() % 15 != 0 || duration.toMinutes() % 15 != 0) {
            throw new IllegalArgumentException("РЁР°Рі Р±СЂРѕРЅРёСЂРѕРІР°РЅРёСЏ РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РєСЂР°С‚РµРЅ 15 РјРёРЅСѓС‚Р°Рј");
        }
    }

    private void validateSpotSearchWindow(LocalDateTime now, LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Р’СЂРµРјСЏ РЅР°С‡Р°Р»Р° Рё РѕРєРѕРЅС‡Р°РЅРёСЏ РїРѕРёСЃРєР° РѕР±СЏР·Р°С‚РµР»СЊРЅРѕ");
        }
        validateBookingWindow(now, from, to);
    }

    private BigDecimal calculateAmount(BigDecimal pricePerStep, LocalDateTime from, LocalDateTime to) {
        long steps = Duration.between(from, to).toMinutes() / SLOT_STEP.toMinutes();
        return pricePerStep.multiply(BigDecimal.valueOf(steps)).setScale(2, RoundingMode.HALF_UP);
    }

    private int calculateCancellationRefundPercent(Reservation reservation, LocalDateTime now) {
        LocalDateTime start = reservation.getStartTime();

        if (now.isBefore(start.minusMinutes(60))) {
            return FULL_REFUND_PERCENT;
        }
        if (!now.isBefore(start)) {
            return POST_START_REFUND_PERCENT;
        }
        if (now.isAfter(start.minusMinutes(15))) {
            return LATE_REFUND_PERCENT;
        }
        return MEDIUM_REFUND_PERCENT;
    }

    private BigDecimal calculateRefund(BigDecimal total, int percent) {
        return total.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private Reservation expireReservation(Reservation reservation) {
        String operationId = operationId(reservation.getId(), "expired");
        log.info("РћС‚РїСЂР°РІР»СЏРµРј СЃРѕР±С‹С‚РёРµ EXPIRED РІ account-service: reservationId={}, operationId={}", reservation.getId(), operationId);
        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.EXPIRED,
                0,
                operationId
        );

        reservation.setStatus(Reservation.ReservationStatus.EXPIRED);
        reservation.setRefundAmount(BigDecimal.ZERO);
        reservation.setRefundPercent(0);
        refreshSpotOccupancy(reservation.getSpotId());
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "EXPIRE_HOLD", "SYSTEM", "hold РёСЃС‚С‘Рє С‡РµСЂРµР· 5 РјРёРЅСѓС‚");
        log.info("HOLD РёСЃС‚С‘Рє: reservationId={}", saved.getId());
        return saved;
    }

    private Reservation markNoShow(Reservation reservation) {
        int refundPercent = NO_SHOW_REFUND_PERCENT;
        BigDecimal refundAmount = calculateRefund(reservation.getTotalAmount(), refundPercent);
        String operationId = operationId(reservation.getId(), "no-show");
        log.info("РћС‚РїСЂР°РІР»СЏРµРј СЃРѕР±С‹С‚РёРµ NO_SHOW РІ account-service: reservationId={}, operationId={}", reservation.getId(), operationId);
        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.NO_SHOW,
                refundPercent,
                operationId
        );

        reservation.setStatus(Reservation.ReservationStatus.NO_SHOW);
        reservation.setRefundAmount(refundAmount);
        reservation.setRefundPercent(refundPercent);
        refreshSpotOccupancy(reservation.getSpotId());
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "NO_SHOW", "SYSTEM", "arrival window missed, refundPercent=" + refundPercent);
        log.info("Reservation moved to NO_SHOW: reservationId={}, refundPercent={}, refundAmount={}", saved.getId(), refundPercent, refundAmount);
        return saved;
    }

    private String operationId(Long reservationId, String action) {
        return "reservation-" + reservationId + "-" + action;
    }

    private boolean hasReachedOrPassed(LocalDateTime now, LocalDateTime deadline) {
        return deadline != null && !now.isBefore(deadline);
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String findLevelForZone(List<ParkingSpot> spots, String zoneCode) {
        return spots.stream()
                .filter(spot -> spot.getZone() != null && zoneCode.equalsIgnoreCase(spot.getZone()))
                .map(ParkingSpot::getLevel)
                .map(this::normalizeFilter)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void saveHistory(Reservation reservation, String action, String requestedBy, String requestDetails) {
        ReservationStateHistory history = new ReservationStateHistory();
        history.setReservationId(reservation.getId());
        history.setStatus(reservation.getStatus());
        history.setAction(action);
        history.setRequestedBy(requestedBy);
        history.setRequestDetails(requestDetails);
        LocalDateTime now = currentTime();
        history.setRequestDate(now);
        history.setLastUpdatedAt(now);
        historyRepository.save(history);
    }

    private void markSpotOccupied(Long spotId) {
        ParkingSpot spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("РџР°СЂРєРѕРІРѕС‡РЅРѕРµ РјРµСЃС‚Рѕ РЅРµ РЅР°Р№РґРµРЅРѕ"));
        spot.setOccupied(true);
        spotRepo.save(spot);
        log.info("РџР°СЂРєРѕРІРѕС‡РЅРѕРµ РјРµСЃС‚Рѕ Р·Р°РЅСЏС‚Рѕ: spotId={}", spotId);
    }

    private void refreshSpotOccupancy(Long spotId) {
        ParkingSpot spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("РџР°СЂРєРѕРІРѕС‡РЅРѕРµ РјРµСЃС‚Рѕ РЅРµ РЅР°Р№РґРµРЅРѕ"));
        boolean occupied = resRepo.existsCurrentReservation(
                spotId,
                Reservation.ReservationStatus.ACTIVE,
                currentTime()
        );
        spot.setOccupied(occupied);
        spotRepo.save(spot);
        log.info("Parking spot occupancy refreshed: spotId={}, occupied={}", spotId, occupied);
    }

    private void completeExpiredActiveReservation(Reservation reservation) {
        Reservation saved = finalizeCompletedReservation(
                reservation,
                "AUTO_COMPLETE",
                "SYSTEM",
                "reservation end time passed"
        );
        log.info("ACTIVE reservation auto-completed: reservationId={}", saved.getId());
    }

    private Reservation finalizeCompletedReservation(Reservation reservation,
                                                     String action,
                                                     String requestedBy,
                                                     String requestDetails) {
        reservation.setStatus(Reservation.ReservationStatus.COMPLETED);
        refreshSpotOccupancy(reservation.getSpotId());
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, action, requestedBy, requestDetails);
        return saved;
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(clock);
    }
}
