package resenkov.work.parkingreservationservice.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkingreservationservice.dto.ParkingLayoutResponse;
import resenkov.work.parkingreservationservice.dto.ParkingSpotAvailabilityResponse;
import resenkov.work.parkingreservationservice.dto.ReservationCatalogResponse;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.repository.ReservationRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService {

    private static final long LIFECYCLE_CHECK_DELAY_MS = 10_000L;

    private final ReservationRepository reservationRepository;
    private final ReservationCreationService creationService;
    private final ReservationLifecycleService lifecycleService;
    private final ReservationCatalogService catalogService;

    public ReservationService(ReservationRepository reservationRepository,
                              ReservationCreationService creationService,
                              ReservationLifecycleService lifecycleService,
                              ReservationCatalogService catalogService) {
        this.reservationRepository = reservationRepository;
        this.creationService = creationService;
        this.lifecycleService = lifecycleService;
        this.catalogService = catalogService;
    }

    @Transactional
    public Reservation createReservation(Long userId, String userEmail, String spotCode, LocalDateTime from, LocalDateTime to) {
        return creationService.createReservation(userId, userEmail, spotCode, from, to);
    }

    public List<Reservation> findByUserEmail(String email) {
        return reservationRepository.findByUserEmail(email);
    }

    @Transactional(readOnly = true)
    public ReservationCatalogResponse getReservationCatalog() {
        return catalogService.getReservationCatalog();
    }

    @Transactional(readOnly = true)
    public List<ParkingSpotAvailabilityResponse> findAvailableSpots(LocalDateTime from,
                                                                    LocalDateTime to,
                                                                    String zone,
                                                                    String level) {
        return catalogService.findAvailableSpots(from, to, zone, level);
    }

    @Transactional(readOnly = true)
    public ParkingLayoutResponse getParkingLayout(LocalDateTime from,
                                                  LocalDateTime to,
                                                  String zone,
                                                  String level) {
        return catalogService.getParkingLayout(from, to, zone, level);
    }

    @Transactional
    public Reservation confirmReservation(Long reservationId, String userEmail) {
        return lifecycleService.confirmReservation(reservationId, userEmail);
    }

    @Transactional
    public Reservation activateReservation(Long reservationId, String userEmail) {
        return lifecycleService.activateReservation(reservationId, userEmail);
    }

    @Transactional
    public Reservation completeReservation(Long reservationId, String userEmail) {
        return lifecycleService.completeReservation(reservationId, userEmail);
    }

    @Transactional
    public Reservation cancelReservation(Long reservationId, String userEmail) {
        return lifecycleService.cancelReservation(reservationId, userEmail);
    }

    @Transactional
    @Scheduled(fixedDelay = LIFECYCLE_CHECK_DELAY_MS)
    public void expireHolds() {
        lifecycleService.expireHolds();
    }

    @Transactional
    @Scheduled(fixedDelay = LIFECYCLE_CHECK_DELAY_MS)
    public void closeNoShows() {
        lifecycleService.closeNoShows();
    }

    @Transactional
    @Scheduled(fixedDelay = LIFECYCLE_CHECK_DELAY_MS)
    public void completeFinishedReservations() {
        lifecycleService.completeFinishedReservations();
    }
}
