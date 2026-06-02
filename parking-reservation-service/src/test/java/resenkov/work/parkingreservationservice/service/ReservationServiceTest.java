package resenkov.work.parkingreservationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import resenkov.work.parkingreservationservice.client.AccountServiceClient;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.entity.ReservationPolicySettings;
import resenkov.work.parkingreservationservice.repository.ParkingSpotRepository;
import resenkov.work.parkingreservationservice.repository.ParkingZoneRepository;
import resenkov.work.parkingreservationservice.repository.ReservationRepository;
import resenkov.work.parkingreservationservice.repository.ReservationStateHistoryRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ParkingSpotRepository spotRepo;

    @Mock
    private ParkingZoneRepository zoneRepo;

    @Mock
    private ReservationRepository resRepo;

    @Mock
    private ReservationStateHistoryRepository historyRepo;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private ReservationPolicyService reservationPolicyService;

    @Test
    void createReservationUsesConfigurableStepAndArrivalDeadlineBeforeEnd() {
        ParkingSpot spot = spot(7L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 6, 12, 0);

        ReservationService service = serviceAt(now, settings(15, 5, 15, 60, 0, 720, 7));
        when(spotRepo.findByCode("A-01")).thenReturn(Optional.of(spot));
        when(resRepo.findOverlappingReservations(eq(7L), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(List.of());
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            if (reservation.getId() == null) {
                reservation.setId(1L);
            }
            return reservation;
        });

        Reservation reservation = service.createReservation(
                10L,
                "user@test.com",
                "A-01",
                LocalDateTime.of(2026, 5, 6, 12, 0),
                LocalDateTime.of(2026, 5, 6, 13, 0)
        );

        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.HOLD);
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo("400.00");
        assertThat(reservation.getHoldExpiresAt()).isEqualTo(LocalDateTime.of(2026, 5, 6, 12, 5));
        assertThat(reservation.getArrivalDeadline()).isEqualTo(LocalDateTime.of(2026, 5, 6, 12, 45));
        verify(accountServiceClient).applyReservationEvent(
                same(reservation),
                eq(Reservation.ReservationStatus.HOLD),
                eq(null),
                eq("reservation-1-hold")
        );
    }

    @Test
    void cancelConfirmedReservationBeforeArrivalDeadlineRefundsConfiguredPercent() {
        Reservation reservation = reservation(
                2L,
                8L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 6, 13, 0),
                LocalDateTime.of(2026, 5, 6, 14, 0),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 6, 13, 45)
        );
        ParkingSpot spot = spot(8L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 6, 12, 48);

        ReservationService service = serviceAt(now, settings(15, 5, 15, 60, 0, 720, 7));
        when(resRepo.findById(2L)).thenReturn(Optional.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(8L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(8L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        Reservation cancelled = service.cancelReservation(2L, "user@test.com");

        assertThat(cancelled.getStatus()).isEqualTo(Reservation.ReservationStatus.CANCELLED);
        assertThat(cancelled.getRefundPercent()).isEqualTo(60);
        assertThat(cancelled.getRefundAmount()).isEqualByComparingTo("240.00");
        verify(accountServiceClient).applyReservationEvent(
                same(reservation),
                eq(Reservation.ReservationStatus.CANCELLED),
                eq(60),
                eq("reservation-2-cancelled")
        );
    }

    @Test
    void cancelConfirmedReservationAtArrivalDeadlineMarksNoShow() {
        Reservation reservation = reservation(
                3L,
                9L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 6, 13, 0),
                LocalDateTime.of(2026, 5, 6, 14, 0),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 6, 13, 45)
        );
        ParkingSpot spot = spot(9L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 6, 13, 45);

        ReservationService service = serviceAt(now, settings(15, 5, 15, 60, 0, 720, 7));
        when(resRepo.findById(3L)).thenReturn(Optional.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(9L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(9L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        Reservation result = service.cancelReservation(3L, "user@test.com");

        assertThat(result.getStatus()).isEqualTo(Reservation.ReservationStatus.NO_SHOW);
        assertThat(result.getRefundPercent()).isZero();
        assertThat(result.getRefundAmount()).isEqualByComparingTo("0.00");
        verify(accountServiceClient).applyReservationEvent(
                same(reservation),
                eq(Reservation.ReservationStatus.NO_SHOW),
                eq(0),
                eq("reservation-3-no-show")
        );
    }

    @Test
    void cancelConfirmedReservationAfterStartButBeforeArrivalDeadlineStillRefundsConfiguredPercent() {
        Reservation reservation = reservation(
                31L,
                91L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 6, 13, 0),
                LocalDateTime.of(2026, 5, 6, 14, 0),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 6, 13, 45)
        );
        ParkingSpot spot = spot(91L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 6, 13, 20);

        ReservationService service = serviceAt(now, settings(15, 5, 15, 60, 0, 720, 7));
        when(resRepo.findById(31L)).thenReturn(Optional.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(91L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(91L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        Reservation cancelled = service.cancelReservation(31L, "user@test.com");

        assertThat(cancelled.getStatus()).isEqualTo(Reservation.ReservationStatus.CANCELLED);
        assertThat(cancelled.getRefundPercent()).isEqualTo(60);
        assertThat(cancelled.getRefundAmount()).isEqualByComparingTo("240.00");
    }

    @Test
    void closeNoShowsUsesConfiguredNoShowRefund() {
        Reservation reservation = reservation(
                4L,
                10L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 6, 13, 0),
                LocalDateTime.of(2026, 5, 6, 14, 0),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 6, 13, 45)
        );
        ParkingSpot spot = spot(10L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 6, 13, 46);

        ReservationService service = serviceAt(now, settings(15, 5, 15, 60, 10, 720, 7));
        when(resRepo.findByStatusAndArrivalDeadlineLessThanEqual(Reservation.ReservationStatus.CONFIRMED, now))
                .thenReturn(List.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(10L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(10L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        service.closeNoShows();

        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.NO_SHOW);
        assertThat(reservation.getRefundPercent()).isEqualTo(10);
        assertThat(reservation.getRefundAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void cancelHoldReservationRefundsFullAmount() {
        Reservation reservation = reservation(
                5L,
                11L,
                "user@test.com",
                Reservation.ReservationStatus.HOLD,
                LocalDateTime.of(2026, 5, 6, 13, 0),
                LocalDateTime.of(2026, 5, 6, 14, 0),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 6, 13, 45)
        );
        reservation.setHoldExpiresAt(LocalDateTime.of(2026, 5, 6, 12, 35));
        ParkingSpot spot = spot(11L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 6, 12, 10);

        ReservationService service = serviceAt(now, settings(15, 5, 15, 60, 0, 720, 7));
        when(resRepo.findById(5L)).thenReturn(Optional.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(11L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(11L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        Reservation cancelled = service.cancelReservation(5L, "user@test.com");

        assertThat(cancelled.getStatus()).isEqualTo(Reservation.ReservationStatus.CANCELLED);
        assertThat(cancelled.getRefundPercent()).isEqualTo(100);
        assertThat(cancelled.getRefundAmount()).isEqualByComparingTo("400.00");
    }

    @Test
    void completeFinishedReservationsAutoCompletesExpiredActiveReservation() {
        Reservation reservation = reservation(
                6L,
                12L,
                "user@test.com",
                Reservation.ReservationStatus.ACTIVE,
                LocalDateTime.of(2026, 5, 6, 11, 0),
                LocalDateTime.of(2026, 5, 6, 12, 0),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 6, 11, 45)
        );
        ParkingSpot spot = spot(12L, true);
        LocalDateTime now = LocalDateTime.of(2026, 5, 6, 12, 30);

        ReservationService service = serviceAt(now, settings(15, 5, 15, 60, 0, 720, 7));
        when(resRepo.findByStatusAndEndTimeLessThanEqual(Reservation.ReservationStatus.ACTIVE, now))
                .thenReturn(List.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(12L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(12L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        service.completeFinishedReservations();

        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.COMPLETED);
        assertThat(spot.isOccupied()).isFalse();
    }

    @Test
    void activateReservationBeforeStartTimeIsRejected() {
        Reservation reservation = reservation(
                7L,
                13L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 6, 13, 0),
                LocalDateTime.of(2026, 5, 6, 14, 0),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 6, 13, 45)
        );
        LocalDateTime now = LocalDateTime.of(2026, 5, 6, 12, 58);

        ReservationService service = serviceAt(now, settings(15, 5, 15, 60, 0, 720, 7));
        when(resRepo.findById(7L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.activateReservation(7L, "user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reservation start time");

        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CONFIRMED);
        verifyNoInteractions(accountServiceClient);
        verify(resRepo, never()).save(any(Reservation.class));
    }

    private ReservationService serviceAt(LocalDateTime now, ReservationPolicySettings settings) {
        Clock clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        lenient().when(reservationPolicyService.getSettings()).thenReturn(settings);
        ReservationRules rules = new ReservationRules();
        ReservationHistoryRecorder historyRecorder = new ReservationHistoryRecorder(historyRepo, clock);
        ParkingOccupancyService occupancyService = new ParkingOccupancyService(spotRepo, resRepo, clock);
        ReservationCreationService creationService = new ReservationCreationService(
                spotRepo,
                resRepo,
                accountServiceClient,
                reservationPolicyService,
                rules,
                historyRecorder,
                clock
        );
        ReservationLifecycleService lifecycleService = new ReservationLifecycleService(
                resRepo,
                accountServiceClient,
                reservationPolicyService,
                rules,
                historyRecorder,
                occupancyService,
                clock
        );
        ReservationCatalogService catalogService = new ReservationCatalogService(
                spotRepo,
                zoneRepo,
                reservationPolicyService,
                rules,
                clock
        );
        return new ReservationService(
                resRepo,
                creationService,
                lifecycleService,
                catalogService
        );
    }

    private ReservationPolicySettings settings(int bookingStepMinutes,
                                               int holdDurationMinutes,
                                               int arrivalDeadlineMinutesBeforeEnd,
                                               int standardCancellationRefundPercent,
                                               int noShowRefundPercent,
                                               int maxBookingDurationMinutes,
                                               int maxBookingAheadDays) {
        ReservationPolicySettings settings = new ReservationPolicySettings();
        settings.setId(1L);
        settings.setBookingStepMinutes(bookingStepMinutes);
        settings.setHoldDurationMinutes(holdDurationMinutes);
        settings.setArrivalDeadlineMinutesBeforeEnd(arrivalDeadlineMinutesBeforeEnd);
        settings.setStandardCancellationRefundPercent(standardCancellationRefundPercent);
        settings.setNoShowRefundPercent(noShowRefundPercent);
        settings.setMaxBookingDurationMinutes(maxBookingDurationMinutes);
        settings.setMaxBookingAheadDays(maxBookingAheadDays);
        return settings;
    }

    private Reservation reservation(Long id,
                                    Long spotId,
                                    String userEmail,
                                    Reservation.ReservationStatus status,
                                    LocalDateTime startTime,
                                    LocalDateTime endTime,
                                    BigDecimal totalAmount,
                                    LocalDateTime arrivalDeadline) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setSpotId(spotId);
        reservation.setSpotCode("S-" + spotId);
        reservation.setUserId(100L + id);
        reservation.setUserEmail(userEmail);
        reservation.setStatus(status);
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);
        reservation.setTotalAmount(totalAmount);
        reservation.setHoldExpiresAt(startTime.minusMinutes(10));
        reservation.setArrivalDeadline(arrivalDeadline);
        reservation.setRefundAmount(BigDecimal.ZERO);
        reservation.setRefundPercent(0);
        return reservation;
    }

    private ParkingSpot spot(Long id, boolean occupied) {
        ParkingSpot spot = new ParkingSpot();
        spot.setId(id);
        spot.setCode("S-" + id);
        spot.setOccupied(occupied);
        spot.setPrice(BigDecimal.valueOf(100));
        return spot;
    }
}
