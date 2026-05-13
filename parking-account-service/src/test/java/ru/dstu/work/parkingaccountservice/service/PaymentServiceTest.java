package ru.dstu.work.parkingaccountservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.dstu.work.parkingaccountservice.entity.OperationType;
import ru.dstu.work.parkingaccountservice.entity.Payment;
import ru.dstu.work.parkingaccountservice.entity.PaymentProviderType;
import ru.dstu.work.parkingaccountservice.entity.PaymentStatus;
import ru.dstu.work.parkingaccountservice.repository.AccountOperationRepository;
import ru.dstu.work.parkingaccountservice.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountOperationRepository accountOperationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldInitPaymentAsPending() {
        Payment payment = paymentService.initTopUp(
                1L,
                "payment-init@test.com",
                BigDecimal.valueOf(500),
                null,
                null
        );

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getProvider()).isEqualTo(PaymentProviderType.MOCK);
        assertThat(payment.getProviderPaymentId()).isEqualTo("mock-payment-" + payment.getId());
        assertThat(payment.getCheckoutUrl()).isEqualTo("/api/mock-payments/" + payment.getId() + "/page");
        assertThat(payment.getSuccessUrl()).isEqualTo("http://127.0.0.1:4173/payment/success");
        assertThat(payment.getFailureUrl()).isEqualTo("http://127.0.0.1:4173/payment/failure");
        assertThat(payment.getCreditedAt()).isNull();
    }

    @Test
    void shouldUseClientOriginForReturnUrls() {
        Payment payment = paymentService.initTopUp(
                10L,
                "payment-origin@test.com",
                BigDecimal.valueOf(150),
                "http://localhost:5173",
                null
        );

        assertThat(payment.getSuccessUrl()).isEqualTo("http://localhost:5173/payment/success");
        assertThat(payment.getFailureUrl()).isEqualTo("http://localhost:5173/payment/failure");
        assertThat(paymentService.buildSuccessUrl(payment.getId()))
                .isEqualTo("http://localhost:5173/payment/success?paymentId=" + payment.getId() + "&status=SUCCEEDED");
        assertThat(paymentService.buildFailureUrl(payment.getId()))
                .isEqualTo("http://localhost:5173/payment/failure?paymentId=" + payment.getId());
    }

    @Test
    void shouldCreditWalletOnlyAfterSuccessfulWebhookAndIgnoreRepeat() throws JsonProcessingException {
        String email = "payment-success@test.com";
        accountService.createWalletIfAbsent(2L, email);
        Payment payment = paymentService.initTopUp(2L, email, BigDecimal.valueOf(700), null, null);

        Payment succeeded = paymentService.handleWebhook(
                PaymentProviderType.MOCK,
                webhookPayload(payment, PaymentStatus.SUCCEEDED),
                Map.of()
        );
        Payment repeated = paymentService.handleWebhook(
                PaymentProviderType.MOCK,
                webhookPayload(payment, PaymentStatus.SUCCEEDED),
                Map.of()
        );

        assertThat(succeeded.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(succeeded.getCreditedAt()).isNotNull();
        assertThat(repeated.getCreditedAt()).isNotNull();
        assertThat(accountRepository.findByUserEmail(email))
                .get()
                .extracting(account -> account.getBalance())
                .isEqualTo(new BigDecimal("700.00"));
        assertThat(accountOperationRepository.findByOperationId("payment-" + payment.getId() + "-topup")).isPresent();
        assertThat(accountOperationRepository.findAll().stream()
                .filter(operation -> operation.getType() == OperationType.TOP_UP)
                .filter(operation -> ("payment-" + payment.getId() + "-topup").equals(operation.getOperationId()))
                .count())
                .isEqualTo(1);
    }

    @Test
    void shouldNotCreditCancelledPayment() throws JsonProcessingException {
        String email = "payment-cancel@test.com";
        accountService.createWalletIfAbsent(3L, email);
        Payment payment = paymentService.initTopUp(3L, email, BigDecimal.valueOf(450), null, null);

        Payment cancelled = paymentService.handleWebhook(
                PaymentProviderType.MOCK,
                webhookPayload(payment, PaymentStatus.CANCELLED),
                Map.of()
        );

        assertThat(cancelled.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(cancelled.getCreditedAt()).isNull();
        assertThat(accountRepository.findByUserEmail(email))
                .get()
                .extracting(account -> account.getBalance())
                .isEqualTo(new BigDecimal("0.00"));
        assertThat(accountOperationRepository.findByOperationId("payment-" + payment.getId() + "-topup")).isEmpty();
    }

    private String webhookPayload(Payment payment, PaymentStatus status) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "providerPaymentId", payment.getProviderPaymentId(),
                "status", status,
                "amount", payment.getAmount()
        ));
    }
}
