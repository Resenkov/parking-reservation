package ru.dstu.work.parkingaccountservice.service;

import ru.dstu.work.parkingaccountservice.entity.PaymentStatus;

import java.math.BigDecimal;

public record PaymentWebhookResult(
        String providerPaymentId,
        PaymentStatus status,
        BigDecimal amount
) {
}
