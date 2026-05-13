package ru.dstu.work.parkingaccountservice.service;

import org.springframework.stereotype.Component;
import ru.dstu.work.parkingaccountservice.entity.PaymentProviderType;

import java.util.Map;

@Component
public class RealPaymentProvider implements PaymentProvider {

    @Override
    public PaymentProviderType getProviderType() {
        return PaymentProviderType.REAL;
    }

    @Override
    public CreatePaymentResult createPayment(CreatePaymentCommand command) {
        throw new UnsupportedOperationException("REAL payment provider is not implemented yet");
    }

    @Override
    public PaymentWebhookResult handleWebhook(String payload, Map<String, String> headers) {
        throw new UnsupportedOperationException("REAL payment provider is not implemented yet");
    }
}
