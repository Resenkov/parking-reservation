package resenkov.work.parkingreservationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import resenkov.work.parkingreservationservice.entity.Reservation;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByUserEmail(String email);
    List<Reservation> findBySpotIdAndStatus(Long spotId, Reservation.ReservationStatus status);
}