package ru.dstu.work.parkingaccountservice.service;

import ru.dstu.work.parkingaccountservice.entity.PaymentStatus;

public record CreatePaymentResult(
        String providerPaymentId,
        String checkoutUrl,
        PaymentStatus status
) {
}
