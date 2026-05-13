package ru.dstu.work.parkingaccountservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.dstu.work.parkingaccountservice.entity.PaymentProviderType;
import ru.dstu.work.parkingaccountservice.entity.PaymentStatus;
import ru.dstu.work.parkingaccountservice.exception.BadRequestException;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class MockPaymentProvider implements PaymentProvider {

    private final ObjectMapper objectMapper;

    public MockPaymentProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProviderType getProviderType() {
        return PaymentProviderType.MOCK;
    }

    @Override
    public CreatePaymentResult createPayment(CreatePaymentCommand command) {
        return new CreatePaymentResult(
                "mock-payment-" + command.paymentId(),
                "/api/mock-payments/" + command.paymentId() + "/page",
                PaymentStatus.PENDING
        );
    }

    @Override
    public PaymentWebhookResult handleWebhook(String payload, Map<String, String> headers) {
        try {
            MockWebhookPayload webhookPayload = objectMapper.readValue(payload, MockWebhookPayload.class);
            if (webhookPayload.providerPaymentId() == null || webhookPayload.providerPaymentId().isBlank()) {
                throw new BadRequestException("providerPaymentId is required");
            }
            if (webhookPayload.status() == null) {
                throw new BadRequestException("status is required");
            }
            return new PaymentWebhookResult(
                    webhookPayload.providerPaymentId(),
                    webhookPayload.status(),
                    webhookPayload.amount()
            );
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid mock webhook payload");
        }
    }

    private record MockWebhookPayload(
            String providerPaymentId,
            PaymentStatus status,
            BigDecimal amount
    ) {
    }
}
