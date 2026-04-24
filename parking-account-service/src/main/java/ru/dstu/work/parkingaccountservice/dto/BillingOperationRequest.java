package ru.dstu.work.parkingaccountservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.dstu.work.parkingaccountservice.entity.ReservationStatus;

import java.math.BigDecimal;

public record BillingOperationRequest(
        @NotBlank(message = "operationId обязателен") String operationId,
        @NotNull(message = "reservationId обязателен") Long reservationId,
        Long userId,
        @NotBlank(message = "userEmail обязателен")
        @Email(message = "userEmail должен быть корректным email")
        @Size(max = 255, message = "userEmail не должен быть длиннее 255 символов")
        String userEmail,
        @NotNull(message = "status обязателен") ReservationStatus status,
        @NotNull(message = "totalAmount обязателен")
        @DecimalMin(value = "0.00", message = "totalAmount не может быть отрицательным")
        BigDecimal totalAmount,
        @Min(value = 0, message = "refundPercent должен быть в диапазоне 0..100")
        @Max(value = 100, message = "refundPercent должен быть в диапазоне 0..100")
        Integer refundPercent
) {
}
