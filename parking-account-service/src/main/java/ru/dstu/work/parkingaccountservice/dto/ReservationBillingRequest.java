package ru.dstu.work.parkingaccountservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.dstu.work.parkingaccountservice.entity.ReservationStatus;

import java.math.BigDecimal;

public record ReservationBillingRequest(
        @NotBlank String operationId,
        @NotNull Long reservationId,
        @NotBlank String userEmail,
        @NotNull ReservationStatus status,
        @NotNull @DecimalMin(value = "0.00") BigDecimal totalAmount,
        Integer refundPercent
) {
}
