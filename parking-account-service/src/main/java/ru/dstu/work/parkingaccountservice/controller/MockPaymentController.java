package ru.dstu.work.parkingaccountservice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dstu.work.parkingaccountservice.entity.Payment;
import ru.dstu.work.parkingaccountservice.entity.PaymentProviderType;
import ru.dstu.work.parkingaccountservice.entity.PaymentStatus;
import ru.dstu.work.parkingaccountservice.service.PaymentService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@RestController
@RequestMapping("/mock-payments")
public class MockPaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public MockPaymentController(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/{paymentId}/page", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paymentPage(@PathVariable Long paymentId) {
        Payment payment = paymentService.getPayment(paymentId);
        String html = """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>Mock payment</title>
                    <style>
                        body { margin: 0; font-family: Arial, sans-serif; background: #f5f7fb; color: #162033; }
                        .wrap { min-height: 100vh; display: grid; place-items: center; padding: 24px; }
                        .panel { width: min(440px, 100%%); background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 12px 36px rgba(15, 23, 42, 0.12); }
                        h1 { margin: 0 0 12px; font-size: 24px; }
                        p { margin: 0 0 12px; line-height: 1.5; }
                        dl { display: grid; grid-template-columns: 1fr auto; gap: 8px 12px; margin: 20px 0; }
                        dt { color: #667085; }
                        dd { margin: 0; font-weight: 600; }
                        .actions { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 20px; }
                        form { margin: 0; }
                        button { border: 0; border-radius: 10px; padding: 14px 16px; font-size: 15px; cursor: pointer; width: 100%%; }
                        .pay { background: #0f766e; color: #fff; }
                        .cancel { background: #e5e7eb; color: #111827; }
                    </style>
                </head>
                <body>
                    <div class="wrap">
                        <div class="panel">
                            <h1>Тестовая оплата</h1>
                            <p>Этот экран имитирует внешний платежный провайдер.</p>
                            <dl>
                                <dt>Платеж</dt>
                                <dd>#%d</dd>
                                <dt>Сумма</dt>
                                <dd>%s RUB</dd>
                                <dt>Статус</dt>
                                <dd>%s</dd>
                            </dl>
                            <div class="actions">
                                <form method="post" action="/api/mock-payments/%d/success">
                                    <button type="submit" class="pay">Оплатить</button>
                                </form>
                                <form method="post" action="/api/mock-payments/%d/cancel">
                                    <button type="submit" class="cancel">Отменить</button>
                                </form>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                payment.getId(),
                formatAmount(payment.getAmount()),
                payment.getStatus(),
                payment.getId(),
                payment.getId()
        );
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @PostMapping("/{paymentId}/success")
    public ResponseEntity<Void> markSuccess(@PathVariable Long paymentId) {
        Payment payment = paymentService.getPayment(paymentId);
        Payment processed = paymentService.handleWebhook(
                PaymentProviderType.MOCK,
                buildMockPayload(payment, PaymentStatus.SUCCEEDED),
                Map.of()
        );
        return redirect(processed.getStatus() == PaymentStatus.SUCCEEDED
                ? paymentService.buildSuccessUrl(paymentId)
                : paymentService.buildFailureUrl(paymentId));
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<Void> markCancelled(@PathVariable Long paymentId) {
        Payment payment = paymentService.getPayment(paymentId);
        Payment processed = paymentService.handleWebhook(
                PaymentProviderType.MOCK,
                buildMockPayload(payment, PaymentStatus.CANCELLED),
                Map.of()
        );
        return redirect(processed.getStatus() == PaymentStatus.SUCCEEDED
                ? paymentService.buildSuccessUrl(paymentId)
                : paymentService.buildFailureUrl(paymentId));
    }

    private String buildMockPayload(Payment payment, PaymentStatus status) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "providerPaymentId", payment.getProviderPaymentId(),
                    "status", status,
                    "amount", payment.getAmount()
            ));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to build mock payment payload", ex);
        }
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(303)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
