package resenkov.work.parkingreservationservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class ReservationCreatedEvent {
    // getters / setters
    private Long reservationId;
    private String spotCode;
    private String userEmail;
    private LocalDateTime validUntil;

    public ReservationCreatedEvent() {}

    public ReservationCreatedEvent(Long reservationId, String spotCode, String userEmail, LocalDateTime validUntil) {
        this.reservationId = reservationId;
        this.spotCode = spotCode;
        this.userEmail = userEmail;
        this.validUntil = validUntil;
    }

}
