package resenkov.work.parkingreservationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import resenkov.work.parkingreservationservice.dto.ReservationCreatedEvent;
import resenkov.work.parkingreservationservice.entity.ParkingSpot;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.kafka.ReservationEventProducer;
import resenkov.work.parkingreservationservice.repository.ParkingSpotRepository;
import resenkov.work.parkingreservationservice.repository.ReservationRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService {

    private final ParkingSpotRepository spotRepo;
    private final ReservationRepository resRepo;
    private final ReservationEventProducer producer;

    public ReservationService(ParkingSpotRepository spotRepo,
                              ReservationRepository resRepo,
                              ReservationEventProducer producer) {
        this.spotRepo = spotRepo;
        this.resRepo = resRepo;
        this.producer = producer;
    }

    @Transactional
    public Reservation createReservation(String userEmail, String spotCode, LocalDateTime from, LocalDateTime to) {
        ParkingSpot spot = spotRepo.findByCode(spotCode)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));

        if (spot.isOccupied()) {
            throw new IllegalStateException("Spot already occupied");
        }

        spot.setOccupied(true);
        spotRepo.save(spot);

        Reservation r = new Reservation();
        r.setSpotId(spot.getId());
        r.setSpotCode(spot.getCode());
        r.setUserEmail(userEmail);
        r.setStartTime(from);
        r.setEndTime(to);
        r.setStatus(Reservation.ReservationStatus.ACTIVE);

        Reservation saved = resRepo.save(r);

        // Publish Kafka event
        ReservationCreatedEvent event = new ReservationCreatedEvent(saved.getId(), saved.getSpotCode(), saved.getUserEmail(), saved.getEndTime());
        producer.publishReservationCreated(event);

        return saved;
    }

    public List<Reservation> findByUserEmail(String email) {
        return resRepo.findByUserEmail(email);
    }

    @Transactional
    public void cancelReservation(Long reservationId, String userEmail) {
        Reservation r = resRepo.findById(reservationId).orElseThrow(() -> new EntityNotFoundException("Reservation not found"));
        if (!r.getUserEmail().equals(userEmail)) {
            throw new SecurityException("Not your reservation");
        }
        r.setStatus(Reservation.ReservationStatus.CANCELLED);
        resRepo.save(r);

        ParkingSpot spot = spotRepo.findById(r.getSpotId()).orElseThrow();
        spot.setOccupied(false);
        spotRepo.save(spot);
    }
}
