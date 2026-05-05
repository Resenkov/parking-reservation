package resenkov.work.parkingreservationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import resenkov.work.parkingreservationservice.client.AccountServiceClient;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.repository.ParkingSpotRepository;
import resenkov.work.parkingreservationservice.repository.ParkingZoneRepository;
import resenkov.work.parkingreservationservice.repository.ReservationRepository;
import resenkov.work.parkingreservationservice.repository.ReservationStateHistoryRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
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

    @Test
    void cancelReservationAfterStartRefundsThirtyPercentAndKeepsOccupiedWhenAnotherActiveExists() {
        Reservation reservation = reservation(
                1L,
                7L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 4, 23, 15),
                LocalDateTime.of(2026, 5, 5, 0, 15),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 4, 23, 30)
        );
        ParkingSpot spot = spot(7L, true);
        LocalDateTime now = LocalDateTime.of(2026, 5, 4, 23, 20);

        ReservationService service = serviceAt(now);
        when(resRepo.findById(1L)).thenReturn(Optional.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(7L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(7L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(true);

        Reservation cancelled = service.cancelReservation(1L, "user@test.com");

        assertThat(cancelled.getStatus()).isEqualTo(Reservation.ReservationStatus.CANCELLED);
        assertThat(cancelled.getRefundPercent()).isEqualTo(30);
        assertThat(cancelled.getRefundAmount()).isEqualByComparingTo("120.00");
        assertThat(spot.isOccupied()).isTrue();
        verify(accountServiceClient).applyReservationEvent(
                same(reservation),
                eq(Reservation.ReservationStatus.CANCELLED),
                eq(30),
                eq("reservation-1-cancelled")
        );
    }

    @Test
    void cancelReservationExactlyFifteenMinutesBeforeStartRefundsEightyPercent() {
        Reservation reservation = reservation(
                2L,
                8L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 4, 23, 15),
                LocalDateTime.of(2026, 5, 5, 0, 15),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 4, 23, 30)
        );
        ParkingSpot spot = spot(8L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 4, 23, 0);

        ReservationService service = serviceAt(now);
        when(resRepo.findById(2L)).thenReturn(Optional.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(8L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(8L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        Reservation cancelled = service.cancelReservation(2L, "user@test.com");

        assertThat(cancelled.getRefundPercent()).isEqualTo(80);
        assertThat(cancelled.getRefundAmount()).isEqualByComparingTo("320.00");
    }

    @Test
    void cancelReservationExactlySixtyMinutesBeforeStartRefundsEightyPercent() {
        Reservation reservation = reservation(
                21L,
                81L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 5, 13, 0),
                LocalDateTime.of(2026, 5, 5, 14, 0),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 5, 13, 15)
        );
        ParkingSpot spot = spot(81L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 5, 12, 0);

        ReservationService service = serviceAt(now);
        when(resRepo.findById(21L)).thenReturn(Optional.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(81L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(81L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        Reservation cancelled = service.cancelReservation(21L, "user@test.com");

        assertThat(cancelled.getRefundPercent()).isEqualTo(80);
        assertThat(cancelled.getRefundAmount()).isEqualByComparingTo("320.00");
    }

    @Test
    void cancelReservationTwelveMinutesBeforeStartRefundsSixtyPercent() {
        Reservation reservation = reservation(
                22L,
                82L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 5, 13, 0),
                LocalDateTime.of(2026, 5, 5, 14, 0),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 5, 13, 15)
        );
        ParkingSpot spot = spot(82L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 5, 12, 48);

        ReservationService service = serviceAt(now);
        when(resRepo.findById(22L)).thenReturn(Optional.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(82L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(82L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        Reservation cancelled = service.cancelReservation(22L, "user@test.com");

        assertThat(cancelled.getRefundPercent()).isEqualTo(60);
        assertThat(cancelled.getRefundAmount()).isEqualByComparingTo("240.00");
        verify(accountServiceClient).applyReservationEvent(
                same(reservation),
                eq(Reservation.ReservationStatus.CANCELLED),
                eq(60),
                eq("reservation-22-cancelled")
        );
    }

    @Test
    void closeNoShowsReturnsZeroPercent() {
        Reservation reservation = reservation(
                3L,
                9L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 4, 23, 15),
                LocalDateTime.of(2026, 5, 5, 0, 15),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 4, 23, 30)
        );
        ParkingSpot spot = spot(9L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 4, 23, 31);

        ReservationService service = serviceAt(now);
        when(resRepo.findByStatusAndArrivalDeadlineLessThanEqual(Reservation.ReservationStatus.CONFIRMED, now))
                .thenReturn(List.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(9L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(9L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        service.closeNoShows();

        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.NO_SHOW);
        assertThat(reservation.getRefundPercent()).isZero();
        assertThat(reservation.getRefundAmount()).isEqualByComparingTo("0.00");
        verify(accountServiceClient).applyReservationEvent(
                same(reservation),
                eq(Reservation.ReservationStatus.NO_SHOW),
                eq(0),
                eq("reservation-3-no-show")
        );
    }

    @Test
    void completeFinishedReservationsAutoCompletesExpiredActiveReservation() {
        Reservation reservation = reservation(
                4L,
                10L,
                "user@test.com",
                Reservation.ReservationStatus.ACTIVE,
                LocalDateTime.of(2026, 5, 4, 21, 0),
                LocalDateTime.of(2026, 5, 4, 22, 0),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 4, 21, 15)
        );
        ParkingSpot spot = spot(10L, true);
        LocalDateTime now = LocalDateTime.of(2026, 5, 4, 22, 30);

        ReservationService service = serviceAt(now);
        when(resRepo.findByStatusAndEndTimeLessThanEqual(Reservation.ReservationStatus.ACTIVE, now))
                .thenReturn(List.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(10L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(10L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        service.completeFinishedReservations();

        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.COMPLETED);
        assertThat(spot.isOccupied()).isFalse();
    }

    @Test
    void cancelReservationAtArrivalDeadlineMarksNoShow() {
        Reservation reservation = reservation(
                5L,
                11L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 5, 0, 15),
                LocalDateTime.of(2026, 5, 5, 1, 15),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 5, 0, 30)
        );
        ParkingSpot spot = spot(11L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 5, 0, 30);

        ReservationService service = serviceAt(now);
        when(resRepo.findById(5L)).thenReturn(Optional.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(11L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(11L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        Reservation result = service.cancelReservation(5L, "user@test.com");

        assertThat(result.getStatus()).isEqualTo(Reservation.ReservationStatus.NO_SHOW);
        assertThat(result.getRefundPercent()).isZero();
        assertThat(result.getRefundAmount()).isEqualByComparingTo("0.00");
        verify(accountServiceClient).applyReservationEvent(
                same(reservation),
                eq(Reservation.ReservationStatus.NO_SHOW),
                eq(0),
                eq("reservation-5-no-show")
        );
    }

    @Test
    void confirmReservationAtHoldDeadlineExpiresReservation() {
        Reservation reservation = reservation(
                6L,
                12L,
                "user@test.com",
                Reservation.ReservationStatus.HOLD,
                LocalDateTime.of(2026, 5, 5, 1, 0),
                LocalDateTime.of(2026, 5, 5, 2, 0),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 5, 1, 15)
        );
        reservation.setHoldExpiresAt(LocalDateTime.of(2026, 5, 5, 0, 25));
        ParkingSpot spot = spot(12L, false);
        LocalDateTime now = LocalDateTime.of(2026, 5, 5, 0, 25);

        ReservationService service = serviceAt(now);
        when(resRepo.findById(6L)).thenReturn(Optional.of(reservation));
        when(resRepo.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spotRepo.findById(12L)).thenReturn(Optional.of(spot));
        when(spotRepo.save(any(ParkingSpot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resRepo.existsCurrentReservation(12L, Reservation.ReservationStatus.ACTIVE, now)).thenReturn(false);

        Reservation result = service.confirmReservation(6L, "user@test.com");

        assertThat(result.getStatus()).isEqualTo(Reservation.ReservationStatus.EXPIRED);
        assertThat(result.getRefundPercent()).isZero();
        assertThat(result.getRefundAmount()).isEqualByComparingTo("0.00");
        verify(accountServiceClient).applyReservationEvent(
                same(reservation),
                eq(Reservation.ReservationStatus.EXPIRED),
                eq(0),
                eq("reservation-6-expired")
        );
    }

    @Test
    void activateReservationBeforeStartTimeIsRejected() {
        Reservation reservation = reservation(
                7L,
                13L,
                "user@test.com",
                Reservation.ReservationStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 5, 0, 45),
                LocalDateTime.of(2026, 5, 5, 1, 45),
                BigDecimal.valueOf(400),
                LocalDateTime.of(2026, 5, 5, 1, 0)
        );
        LocalDateTime now = LocalDateTime.of(2026, 5, 5, 0, 42);

        ReservationService service = serviceAt(now);
        when(resRepo.findById(7L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.activateReservation(7L, "user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reservation start time");

        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CONFIRMED);
        verifyNoInteractions(accountServiceClient);
        verify(resRepo, never()).save(any(Reservation.class));
    }

    private ReservationService serviceAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new ReservationService(spotRepo, zoneRepo, resRepo, historyRepo, accountServiceClient, clock);
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
