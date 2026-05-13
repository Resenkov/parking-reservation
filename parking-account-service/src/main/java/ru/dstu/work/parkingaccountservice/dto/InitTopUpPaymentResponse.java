package ru.dstu.work.parkingaccountservice.dto;

import ru.dstu.work.parkingaccountservice.entity.PaymentProviderType;
import ru.dstu.work.parkingaccountservice.entity.PaymentStatus;

import java.math.BigDecimal;

public record InitTopUpPaymentResponse(
        Long paymentId,
        BigDecimal amount,
        PaymentStatus status,
        PaymentProviderType provider,
        String checkoutUrl
) {
}
