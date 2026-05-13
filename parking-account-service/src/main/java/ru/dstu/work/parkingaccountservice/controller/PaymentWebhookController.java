package ru.dstu.work.parkingaccountservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dstu.work.parkingaccountservice.entity.PaymentProviderType;
import ru.dstu.work.parkingaccountservice.exception.BadRequestException;
import ru.dstu.work.parkingaccountservice.service.PaymentService;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/payments/webhook")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Void> handleWebhook(@PathVariable String provider,
                                              @RequestBody String payload,
                                              @RequestHeader Map<String, String> headers) {
        paymentService.handleWebhook(parseProvider(provider), payload, normalizeHeaders(headers));
        return ResponseEntity.ok().build();
    }

    private PaymentProviderType parseProvider(String provider) {
        try {
            return PaymentProviderType.valueOf(provider.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unsupported payment provider: " + provider);
        }
    }

    private Map<String, String> normalizeHeaders(Map<String, String> headers) {
        return headers.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
    }
}
