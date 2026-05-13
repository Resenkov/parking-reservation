package resenkov.work.parkingreservationservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkingreservationservice.client.AccountServiceClient;
import resenkov.work.parkingreservationservice.dto.ParkingLayoutResponse;
import resenkov.work.parkingreservationservice.dto.ParkingLayoutSpotResponse;
import resenkov.work.parkingreservationservice.dto.ParkingSpotAvailabilityResponse;
import resenkov.work.parkingreservationservice.dto.ReservationCatalogResponse;
import resenkov.work.parkingreservationservice.dto.ReservationLevelOptionResponse;
import resenkov.work.parkingreservationservice.dto.ReservationZoneOptionResponse;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.ParkingZone;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.entity.ReservationPolicySettings;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
public class ReservationService {

    private static final long LIFECYCLE_CHECK_DELAY_MS = 10_000L;
    private static final int FULL_REFUND_PERCENT = 100;
    private static final List<Reservation.ReservationStatus> BUSY_RESERVATION_STATUSES = List.of(
            Reservation.ReservationStatus.PENDING_HOLD,
            Reservation.ReservationStatus.HOLD,
            Reservation.ReservationStatus.CONFIRMED,
            Reservation.ReservationStatus.ACTIVE
    );

    private final ParkingSpotRepository spotRepo;
    private final ParkingZoneRepository zoneRepo;
    private final ReservationRepository resRepo;
    private final ReservationStateHistoryRepository historyRepository;
    private final AccountServiceClient accountServiceClient;
    private final ReservationPolicyService reservationPolicyService;
    private final Clock clock;

    public ReservationService(ParkingSpotRepository spotRepo,
                              ParkingZoneRepository zoneRepo,
                              ReservationRepository resRepo,
                              ReservationStateHistoryRepository historyRepository,
                              AccountServiceClient accountServiceClient,
                              ReservationPolicyService reservationPolicyService,
                              Clock clock) {
        this.spotRepo = spotRepo;
        this.zoneRepo = zoneRepo;
        this.resRepo = resRepo;
        this.historyRepository = historyRepository;
        this.accountServiceClient = accountServiceClient;
        this.reservationPolicyService = reservationPolicyService;
        this.clock = clock;
    }

    @Transactional
    public Reservation createReservation(Long userId, String userEmail, String spotCode, LocalDateTime from, LocalDateTime to) {
        LocalDateTime now = currentTime();
        ReservationPolicySettings settings = reservationPolicyService.getSettings();
        validateBookingWindow(now, from, to, settings);

        ParkingSpot spot = spotRepo.findByCode(spotCode)
                .orElseThrow(() -> new EntityNotFoundException("Parking spot not found: " + spotCode));

        List<Reservation> overlaps = resRepo.findOverlappingReservations(
                spot.getId(),
                from,
                to,
                BUSY_RESERVATION_STATUSES
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
        reservation.setArrivalDeadline(calculateArrivalDeadline(to, settings));
        reservation.setTotalAmount(calculateAmount(spot.getPrice(), from, to, settings));
        reservation.setRefundAmount(BigDecimal.ZERO);
        reservation.setRefundPercent(0);
        reservation.setStatus(Reservation.ReservationStatus.PENDING_HOLD);

        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "BOOK_REQUEST", userEmail, "spotCode=" + spotCode + ",from=" + from + ",to=" + to);

        String holdOperationId = operationId(saved.getId(), "hold");
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
            log.error("Failed to create hold for reservation {}", failed.getId(), ex);
            return failed;
        }

        saved.setStatus(Reservation.ReservationStatus.HOLD);
        saved.setHoldExpiresAt(currentTime().plusMinutes(settings.getHoldDurationMinutes()));
        Reservation held = resRepo.save(saved);
        saveHistory(held, "HOLD_OK", userEmail, "hold completed");
        return held;
    }

    public List<Reservation> findByUserEmail(String email) {
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
        ReservationPolicySettings settings = reservationPolicyService.getSettings();
        validateSpotSearchWindow(currentTime(), from, to, settings);

        List<ParkingSpot> spots = spotRepo.findAvailableSpots(
                normalizeFilter(zone),
                normalizeFilter(level),
                from,
                to,
                BUSY_RESERVATION_STATUSES
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

    @Transactional(readOnly = true)
    public ParkingLayoutResponse getParkingLayout(LocalDateTime from,
                                                  LocalDateTime to,
                                                  String zone,
                                                  String level) {
        ReservationPolicySettings settings = reservationPolicyService.getSettings();
        LocalDateTime now = currentTime();
        boolean hasInterval = from != null || to != null;

        if (hasInterval && (from == null || to == null)) {
            throw new IllegalArgumentException("Both from and to are required");
        }
        if (hasInterval) {
            validateSpotSearchWindow(now, from, to, settings);
        }

        String normalizedZone = normalizeFilter(zone);
        String normalizedLevel = normalizeFilter(level);
        List<ParkingSpot> allSpots = normalizedZone == null && normalizedLevel == null
                ? spotRepo.findAllByOrderByCodeAsc()
                : spotRepo.search(normalizedZone, normalizedLevel, null);

        Map<Long, ParkingZone> zonesById = getZonesById(allSpots);
        Set<Long> availableSpotIds = hasInterval
                ? spotRepo.findAvailableSpots(normalizedZone, normalizedLevel, from, to, BUSY_RESERVATION_STATUSES).stream()
                .map(ParkingSpot::getId)
                .collect(Collectors.toCollection(HashSet::new))
                : Set.of();

        List<ParkingLayoutSpotResponse> spots = allSpots.stream()
                .map(spot -> {
                    ParkingZone parkingZone = spot.getZoneId() == null ? null : zonesById.get(spot.getZoneId());
                    return ParkingLayoutSpotResponse.builder()
                            .id(spot.getId())
                            .code(spot.getCode())
                            .zone(normalizeNullable(spot.getZone()))
                            .zoneName(parkingZone == null ? null : normalizeNullable(parkingZone.getName()))
                            .level(normalizeNullable(spot.getLevel()))
                            .price(spot.getPrice())
                            .occupied(spot.isOccupied())
                            .available(hasInterval ? availableSpotIds.contains(spot.getId()) : !spot.isOccupied())
                            .build();
                })
                .toList();

        return ParkingLayoutResponse.builder()
                .generatedAt(now)
                .from(from)
                .to(to)
                .spots(spots)
                .build();
    }

    @Transactional
    public Reservation confirmReservation(Long reservationId, String userEmail) {
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        if (reservation.getStatus() != Reservation.ReservationStatus.HOLD) {
            throw new IllegalStateException("Only HOLD reservations can be confirmed");
        }
        if (hasReachedOrPassed(currentTime(), reservation.getHoldExpiresAt())) {
            return expireReservation(reservation);
        }

        reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "CONFIRM", userEmail, "reservation confirmed");
        return saved;
    }

    @Transactional
    public Reservation activateReservation(Long reservationId, String userEmail) {
        Reservation reservation = getOwnedReservation(reservationId, userEmail);
        LocalDateTime now = currentTime();

        if (reservation.getStatus() != Reservation.ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED reservations can be activated");
        }
        if (now.isBefore(reservation.getStartTime())) {
            throw new IllegalStateException("Check-in is available only after reservation start time");
        }
        if (hasReachedOrPassed(now, reservation.getArrivalDeadline())) {
            return markNoShow(reservation);
        }

        String captureOperationId = operationId(reservation.getId(), "capture");
        accountServiceClient.applyReservationEvent(
                reservation,
                Reservation.ReservationStatus.ACTIVE,
                null,
                captureOperationId
        );

        reservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        markSpotOccupied(reservation.getSpotId());
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, "ACTIVATE", userEmail, "reservation activated");
        return saved;
    }

    @Transactional
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

    @Transactional
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
            refreshSpotOccupancy(reservation.getSpotId());

            Reservation saved = resRepo.save(reservation);
            saveHistory(saved, "CANCEL", userEmail, "billing was not started, previousStatus=" + previousStatus);
            return saved;
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.COMPLETED
                || reservation.getStatus() == Reservation.ReservationStatus.CANCELLED
                || reservation.getStatus() == Reservation.ReservationStatus.EXPIRED
                || reservation.getStatus() == Reservation.ReservationStatus.NO_SHOW) {
            throw new IllegalStateException("Reservation is already finalized");
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.HOLD
                && hasReachedOrPassed(now, reservation.getHoldExpiresAt())) {
            return expireReservation(reservation);
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.HOLD) {
            return cancelReservationWithRefund(reservation, userEmail, FULL_REFUND_PERCENT);
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.CONFIRMED
                && hasReachedOrPassed(now, reservation.getArrivalDeadline())) {
            return markNoShow(reservation);
        }

        return cancelReservationWithRefund(
                reservation,
                userEmail,
                reservationPolicyService.getSettings().getStandardCancellationRefundPercent()
        );
    }

    @Transactional
    @Scheduled(fixedDelay = LIFECYCLE_CHECK_DELAY_MS)
    public void expireHolds() {
        List<Reservation> expired = resRepo.findByStatusAndHoldExpiresAtLessThanEqual(
                Reservation.ReservationStatus.HOLD,
                currentTime()
        );
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
        for (Reservation reservation : completed) {
            completeExpiredActiveReservation(reservation);
        }
    }

    private Reservation getOwnedReservation(Long reservationId, String userEmail) {
        Reservation reservation = resRepo.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found"));
        if (!reservation.getUserEmail().equals(userEmail)) {
            throw new SecurityException("Reservation belongs to another user");
        }
        return reservation;
    }

    private void validateBookingWindow(LocalDateTime now,
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

    private void validateSpotSearchWindow(LocalDateTime now,
                                          LocalDateTime from,
                                          LocalDateTime to,
                                          ReservationPolicySettings settings) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Search interval is required");
        }
        validateBookingWindow(now, from, to, settings);
    }

    private BigDecimal calculateAmount(BigDecimal pricePerStep,
                                       LocalDateTime from,
                                       LocalDateTime to,
                                       ReservationPolicySettings settings) {
        long steps = Duration.between(from, to).toMinutes() / settings.getBookingStepMinutes();
        return pricePerStep.multiply(BigDecimal.valueOf(steps)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRefund(BigDecimal total, int percent) {
        return total.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private Reservation expireReservation(Reservation reservation) {
        String operationId = operationId(reservation.getId(), "expired");
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
        saveHistory(saved, "EXPIRE_HOLD", "SYSTEM", "hold expired");
        return saved;
    }

    private Reservation markNoShow(Reservation reservation) {
        int refundPercent = reservationPolicyService.getSettings().getNoShowRefundPercent();
        BigDecimal refundAmount = calculateRefund(reservation.getTotalAmount(), refundPercent);
        String operationId = operationId(reservation.getId(), "no-show");
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
        saveHistory(saved, "NO_SHOW", "SYSTEM", "arrival deadline missed, refundPercent=" + refundPercent);
        return saved;
    }

    private Reservation cancelReservationWithRefund(Reservation reservation, String userEmail, int percent) {
        BigDecimal refund = calculateRefund(reservation.getTotalAmount(), percent);
        String cancelOperationId = operationId(reservation.getId(), "cancelled");

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
                .orElseThrow(() -> new EntityNotFoundException("Parking spot not found"));
        spot.setOccupied(true);
        spotRepo.save(spot);
    }

    private void refreshSpotOccupancy(Long spotId) {
        ParkingSpot spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Parking spot not found"));
        boolean occupied = resRepo.existsCurrentReservation(
                spotId,
                Reservation.ReservationStatus.ACTIVE,
                currentTime()
        );
        spot.setOccupied(occupied);
        spotRepo.save(spot);
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
        refreshSpotOccupancy(reservation.getSpotId());
        Reservation saved = resRepo.save(reservation);
        saveHistory(saved, action, requestedBy, requestDetails);
        return saved;
    }

    private LocalDateTime calculateArrivalDeadline(LocalDateTime endTime, ReservationPolicySettings settings) {
        return endTime.minusMinutes(settings.getArrivalDeadlineMinutesBeforeEnd());
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

    private Map<Long, ParkingZone> getZonesById(List<ParkingSpot> spots) {
        Set<Long> zoneIds = spots.stream()
                .map(ParkingSpot::getZoneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (zoneIds.isEmpty()) {
            return Map.of();
        }
        return zoneRepo.findByIdIn(zoneIds).stream()
                .collect(Collectors.toMap(ParkingZone::getId, Function.identity()));
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(clock);
    }
}
