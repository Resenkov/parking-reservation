package ru.dstu.work.parkingaccountservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InitTopUpPaymentRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        @Digits(integer = 12, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount
) {
}
