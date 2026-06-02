package resenkov.work.parkingreservationservice.service;

import org.springframework.stereotype.Service;
import resenkov.work.parkingreservationservice.entity.Reservation;
import resenkov.work.parkingreservationservice.entity.ReservationStateHistory;
import resenkov.work.parkingreservationservice.repository.ReservationStateHistoryRepository;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ReservationHistoryRecorder {

    private final ReservationStateHistoryRepository historyRepository;
    private final Clock clock;

    public ReservationHistoryRecorder(ReservationStateHistoryRepository historyRepository, Clock clock) {
        this.historyRepository = historyRepository;
        this.clock = clock;
    }

    public void saveHistory(Reservation reservation, String action, String requestedBy, String requestDetails) {
        ReservationStateHistory history = new ReservationStateHistory();
        history.setReservationId(reservation.getId());
        history.setStatus(reservation.getStatus());
        history.setAction(action);
        history.setRequestedBy(requestedBy);
        history.setRequestDetails(requestDetails);
        LocalDateTime now = LocalDateTime.now(clock);
        history.setRequestDate(now);
        history.setLastUpdatedAt(now);
        historyRepository.save(history);
    }
}
