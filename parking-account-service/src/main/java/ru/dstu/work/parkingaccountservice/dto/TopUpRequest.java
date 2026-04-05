package ru.dstu.work.parkingaccountservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TopUpRequest(
        @NotBlank String operationId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {
}
