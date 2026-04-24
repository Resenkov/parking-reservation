package ru.dstu.work.parkingaccountservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TopUpRequest(
        @NotBlank(message = "operationId обязателен") String operationId,
        @NotNull(message = "Сумма пополнения обязательна")
        @DecimalMin(value = "0.01", message = "Сумма пополнения должна быть не меньше 0.01")
        BigDecimal amount
) {
}
