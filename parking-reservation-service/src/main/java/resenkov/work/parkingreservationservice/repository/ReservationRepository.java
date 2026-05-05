package resenkov.work.parkingreservationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import resenkov.work.parkingreservationservice.entity.Reservation;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByUserEmail(String email);

    @Query("""
            select r from Reservation r
            where r.spotId = :spotId
              and r.status in :statuses
              and r.startTime < :toTime
              and r.endTime > :fromTime
            """)
    List<Reservation> findOverlappingReservations(
            @Param("spotId") Long spotId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("statuses") List<Reservation.ReservationStatus> statuses
    );

    List<Reservation> findByStatusAndHoldExpiresAtLessThanEqual(Reservation.ReservationStatus status, LocalDateTime time);

    List<Reservation> findByStatusAndArrivalDeadlineLessThanEqual(Reservation.ReservationStatus status, LocalDateTime time);

    List<Reservation> findByStatusAndEndTimeLessThanEqual(Reservation.ReservationStatus status, LocalDateTime time);

    @Query("""
            select (count(r) > 0) from Reservation r
            where r.spotId = :spotId
              and r.status = :status
              and r.startTime <= :moment
              and r.endTime > :moment
            """)
    boolean existsCurrentReservation(
            @Param("spotId") Long spotId,
            @Param("status") Reservation.ReservationStatus status,
            @Param("moment") LocalDateTime moment
    );

    boolean existsBySpotId(Long spotId);
}
