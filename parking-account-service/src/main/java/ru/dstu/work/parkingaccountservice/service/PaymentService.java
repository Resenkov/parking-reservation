package ru.dstu.work.parkingaccountservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import ru.dstu.work.parkingaccountservice.config.PaymentProperties;
import ru.dstu.work.parkingaccountservice.entity.Payment;
import ru.dstu.work.parkingaccountservice.entity.PaymentProviderType;
import ru.dstu.work.parkingaccountservice.entity.PaymentStatus;
import ru.dstu.work.parkingaccountservice.exception.BadRequestException;
import ru.dstu.work.parkingaccountservice.repository.PaymentRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Log4j2
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final AccountService accountService;
    private final PaymentProperties paymentProperties;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentProviderRegistry paymentProviderRegistry,
                          AccountService accountService,
                          PaymentProperties paymentProperties) {
        this.paymentRepository = paymentRepository;
        this.paymentProviderRegistry = paymentProviderRegistry;
        this.accountService = accountService;
        this.paymentProperties = paymentProperties;
    }

    @Transactional
    public Payment initTopUp(Long userId,
                             String userEmail,
                             BigDecimal amount,
                             String origin,
                             String referer) {
        BigDecimal normalizedAmount = validateAndNormalizeAmount(amount);
        String normalizedEmail = normalizeEmail(userEmail);
        PaymentProviderType providerType = paymentProperties.getProvider();

        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setUserEmail(normalizedEmail);
        payment.setAmount(normalizedAmount);
        payment.setStatus(PaymentStatus.NEW);
        payment.setProvider(providerType);
        payment.setIdempotencyKey(UUID.randomUUID().toString());
        payment.setSuccessUrl(resolveSuccessUrl(origin, referer));
        payment.setFailureUrl(resolveFailureUrl(origin, referer));
        payment = paymentRepository.save(payment);

        log.info(
                "Payment created: paymentId={}, userId={}, email={}, amount={}, provider={}",
                payment.getId(),
                userId,
                normalizedEmail,
                normalizedAmount,
                providerType
        );

        PaymentProvider provider = paymentProviderRegistry.get(providerType);
        CreatePaymentResult result;
        try {
            result = provider.createPayment(new CreatePaymentCommand(
                    payment.getId(),
                    userId,
                    normalizedEmail,
                    normalizedAmount,
                    "Пополнение парковочного кошелька",
                    buildSuccessUrl(payment),
                    buildFailureUrl(payment)
            ));
        } catch (RuntimeException ex) {
            log.error(
                    "Failed to initialize payment with provider: paymentId={}, provider={}",
                    payment.getId(),
                    providerType,
                    ex
            );
            throw ex;
        }

        payment.setProviderPaymentId(result.providerPaymentId());
        payment.setCheckoutUrl(result.checkoutUrl());
        payment.setStatus(result.status());
        Payment saved = paymentRepository.save(payment);

        log.info(
                "Payment initialized: paymentId={}, providerPaymentId={}, status={}, checkoutUrl={}",
                saved.getId(),
                saved.getProviderPaymentId(),
                saved.getStatus(),
                saved.getCheckoutUrl()
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public Payment getPaymentForUser(Long paymentId, String userEmail) {
        Payment payment = getPayment(paymentId);
        if (!payment.getUserEmail().equals(normalizeEmail(userEmail))) {
            throw new EntityNotFoundException("Платеж не найден");
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Платеж не найден"));
    }

    @Transactional
    public Payment handleWebhook(PaymentProviderType providerType, String payload, Map<String, String> headers) {
        log.info("Payment webhook received: provider={}, payload={}", providerType, payload);
        PaymentProvider provider = paymentProviderRegistry.get(providerType);
        PaymentWebhookResult webhookResult = provider.handleWebhook(payload, headers);

        if (webhookResult.providerPaymentId() == null || webhookResult.providerPaymentId().isBlank()) {
            throw new BadRequestException("providerPaymentId is required");
        }
        if (webhookResult.status() == null) {
            throw new BadRequestException("status is required");
        }

        Payment payment = paymentRepository.findByProviderPaymentIdForUpdate(webhookResult.providerPaymentId())
                .orElseThrow(() -> new EntityNotFoundException("Платеж не найден"));

        if (payment.getProvider() != providerType) {
            throw new BadRequestException("Payment provider mismatch");
        }
        if (webhookResult.amount() != null
                && payment.getAmount().compareTo(validateAndNormalizeAmount(webhookResult.amount())) != 0) {
            throw new BadRequestException("Payment amount mismatch");
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED && payment.getCreditedAt() != null) {
            log.info("Repeated webhook ignored for already credited payment: paymentId={}", payment.getId());
            return payment;
        }

        if (payment.getStatus().isFinal()
                && payment.getStatus() != PaymentStatus.SUCCEEDED
                && payment.getStatus() != webhookResult.status()) {
            log.warn(
                    "Conflicting webhook ignored for final payment: paymentId={}, currentStatus={}, incomingStatus={}",
                    payment.getId(),
                    payment.getStatus(),
                    webhookResult.status()
            );
            return payment;
        }

        return switch (webhookResult.status()) {
            case SUCCEEDED -> markSucceededAndCredit(payment);
            case FAILED, CANCELLED -> markFinalWithoutCredit(payment, webhookResult.status());
            case NEW, PENDING -> markPending(payment, webhookResult.status());
        };
    }

    @Transactional(readOnly = true)
    public String buildSuccessUrl(Long paymentId) {
        return buildSuccessUrl(getPayment(paymentId));
    }

    @Transactional(readOnly = true)
    public String buildFailureUrl(Long paymentId) {
        return buildFailureUrl(getPayment(paymentId));
    }

    private Payment markSucceededAndCredit(Payment payment) {
        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PaymentStatus.SUCCEEDED);
        if (payment.getConfirmedAt() == null) {
            payment.setConfirmedAt(now);
        }

        try {
            accountService.creditFromConfirmedPayment(
                    payment.getUserEmail(),
                    "payment-" + payment.getId() + "-topup",
                    payment.getAmount(),
                    payment.getId()
            );
        } catch (RuntimeException ex) {
            log.error("Failed to credit confirmed payment: paymentId={}", payment.getId(), ex);
            throw ex;
        }

        if (payment.getCreditedAt() == null) {
            payment.setCreditedAt(now);
        }
        Payment saved = paymentRepository.save(payment);
        log.info(
                "Payment credited successfully: paymentId={}, providerPaymentId={}, amount={}",
                saved.getId(),
                saved.getProviderPaymentId(),
                saved.getAmount()
        );
        return saved;
    }

    private Payment markFinalWithoutCredit(Payment payment, PaymentStatus status) {
        if (payment.getStatus() == status) {
            log.info("Repeated final webhook ignored: paymentId={}, status={}", payment.getId(), status);
            return payment;
        }
        payment.setStatus(status);
        if (payment.getConfirmedAt() == null) {
            payment.setConfirmedAt(LocalDateTime.now());
        }
        Payment saved = paymentRepository.save(payment);
        log.info("Payment marked final without credit: paymentId={}, status={}", saved.getId(), status);
        return saved;
    }

    private Payment markPending(Payment payment, PaymentStatus status) {
        if (payment.getStatus().isFinal()) {
            return payment;
        }
        payment.setStatus(status);
        return paymentRepository.save(payment);
    }

    private BigDecimal validateAndNormalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Сумма должна быть больше 0");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeEmail(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new BadRequestException("userEmail is required");
        }
        return userEmail.trim().toLowerCase(Locale.ROOT);
    }

    private String buildSuccessUrl(Payment payment) {
        return appendPaymentId(payment.getSuccessUrl(), payment.getId(), "SUCCEEDED");
    }

    private String buildFailureUrl(Payment payment) {
        return appendPaymentId(payment.getFailureUrl(), payment.getId(), null);
    }

    private String resolveSuccessUrl(String origin, String referer) {
        return resolveClientBaseUrl(origin, referer, paymentProperties.getSuccessUrl()) + "/payment/success";
    }

    private String resolveFailureUrl(String origin, String referer) {
        return resolveClientBaseUrl(origin, referer, paymentProperties.getFailureUrl()) + "/payment/failure";
    }

    private String resolveClientBaseUrl(String origin, String referer, String fallbackUrl) {
        if (origin != null && !origin.isBlank()) {
            return trimTrailingSlash(origin.trim());
        }
        if (referer != null && !referer.isBlank()) {
            try {
                return trimTrailingSlash(
                        UriComponentsBuilder.fromUriString(referer)
                                .replacePath(null)
                                .replaceQuery(null)
                                .fragment(null)
                                .build()
                                .toUriString()
                );
            } catch (IllegalArgumentException ex) {
                log.warn("Failed to parse referer for payment return URL: referer={}", referer);
            }
        }
        try {
            return trimTrailingSlash(
                    UriComponentsBuilder.fromUriString(fallbackUrl)
                            .replacePath(null)
                            .replaceQuery(null)
                            .fragment(null)
                            .build()
                            .toUriString()
            );
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid configured payment return URL");
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String appendPaymentId(String baseUrl, Long paymentId, String status) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("paymentId", paymentId);
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        return builder.build(true).toUriString();
    }
}
