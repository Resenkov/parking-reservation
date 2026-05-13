package ru.dstu.work.parkingaccountservice.service;

import java.math.BigDecimal;

public record CreatePaymentCommand(
        Long paymentId,
        Long userId,
        String userEmail,
        BigDecimal amount,
        String description,
        String successUrl,
        String failureUrl
) {
}
