package ru.dstu.work.parkingaccountservice.dto;

import ru.dstu.work.parkingaccountservice.entity.PaymentProviderType;
import ru.dstu.work.parkingaccountservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId,
        BigDecimal amount,
        PaymentStatus status,
        PaymentProviderType provider,
        String checkoutUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime confirmedAt,
        LocalDateTime creditedAt
) {
}
