package resenkov.work.parkingreservationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import resenkov.work.parkingreservationservice.entity.ReservationStateHistory;

import java.util.List;

public interface ReservationStateHistoryRepository extends JpaRepository<ReservationStateHistory, Long> {
    List<ReservationStateHistory> findByReservationIdOrderByRequestDateAsc(Long reservationId);
}
