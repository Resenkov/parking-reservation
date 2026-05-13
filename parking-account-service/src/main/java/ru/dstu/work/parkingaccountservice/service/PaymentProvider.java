package ru.dstu.work.parkingaccountservice.service;

import ru.dstu.work.parkingaccountservice.entity.PaymentProviderType;

import java.util.Map;

public interface PaymentProvider {

    PaymentProviderType getProviderType();

    CreatePaymentResult createPayment(CreatePaymentCommand command);

    PaymentWebhookResult handleWebhook(String payload, Map<String, String> headers);
}
