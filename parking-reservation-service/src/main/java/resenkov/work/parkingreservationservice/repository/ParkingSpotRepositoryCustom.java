package resenkov.work.parkingreservationservice.repository;

import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.Reservation;

import java.time.LocalDateTime;
import java.util.List;

public interface ParkingSpotRepositoryCustom {
    List<ParkingSpot> search(String zone, String level, Boolean occupied);

    List<ParkingSpot> findAvailableSpots(String zone,
                                         String level,
                                         LocalDateTime fromTime,
                                         LocalDateTime toTime,
                                         List<Reservation.ReservationStatus> statuses);
}
