package resenkov.work.parkingreservationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import resenkov.work.parkingreservationservice.entity.Reservation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationBillingEvent {
    private String operationId;
    private Long reservationId;
    private String userEmail;
    private String spotCode;
    private Reservation.ReservationStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal totalAmount;
    private Integer refundPercent;
    private LocalDateTime emittedAt;
}
