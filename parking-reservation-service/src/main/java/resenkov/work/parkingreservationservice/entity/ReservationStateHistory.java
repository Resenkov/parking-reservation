package resenkov.work.parkingreservationservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_state_history")
@Getter
@Setter
public class ReservationStateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reservationId;

    @Enumerated(EnumType.STRING)
    private Reservation.ReservationStatus status;

    private String action;

    @Column(columnDefinition = "TEXT")
    private String requestDetails;

    private String requestedBy;

    private LocalDateTime requestDate;

    private LocalDateTime lastUpdatedAt;
}
