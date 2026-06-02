package resenkov.work.parkingreservationservice.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.repository.ParkingSpotRepository;
import resenkov.work.parkingreservationservice.repository.ReservationRepository;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ParkingOccupancyService {

    private final ParkingSpotRepository spotRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public ParkingOccupancyService(ParkingSpotRepository spotRepository,
                                   ReservationRepository reservationRepository,
                                   Clock clock) {
        this.spotRepository = spotRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    public void markSpotOccupied(Long spotId) {
        ParkingSpot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Parking spot not found"));
        spot.setOccupied(true);
        spotRepository.save(spot);
    }

    public void refreshSpotOccupancy(Long spotId) {
        ParkingSpot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Parking spot not found"));
        boolean occupied = reservationRepository.existsCurrentReservation(
                spotId,
                Reservation.ReservationStatus.ACTIVE,
                LocalDateTime.now(clock)
        );
        spot.setOccupied(occupied);
        spotRepository.save(spot);
    }
}
