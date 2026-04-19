package ru.dstu.work.parkingaccountservice.dto;

import ru.dstu.work.parkingaccountservice.entity.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationBillingEvent(
        String operationId,
        Long reservationId,
        String userEmail,
        String spotCode,
        ReservationStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal totalAmount,
        Integer refundPercent,
        LocalDateTime emittedAt
) {
}
