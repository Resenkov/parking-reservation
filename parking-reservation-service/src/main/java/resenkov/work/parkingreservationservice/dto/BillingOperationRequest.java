package resenkov.work.parkingreservationservice.dto;

import resenkov.work.parkingreservationservice.entity.Reservation;

import java.math.BigDecimal;

public record BillingOperationRequest(
        String operationId,
        Long reservationId,
        Long userId,
        String userEmail,
        Reservation.ReservationStatus status,
        BigDecimal totalAmount,
        Integer refundPercent
) {
}
