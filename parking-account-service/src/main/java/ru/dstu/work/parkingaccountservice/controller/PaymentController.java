package ru.dstu.work.parkingaccountservice.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dstu.work.parkingaccountservice.dto.InitTopUpPaymentRequest;
import ru.dstu.work.parkingaccountservice.dto.InitTopUpPaymentResponse;
import ru.dstu.work.parkingaccountservice.dto.PaymentResponse;
import ru.dstu.work.parkingaccountservice.entity.Payment;
import ru.dstu.work.parkingaccountservice.service.PaymentService;
import ru.dstu.work.parkingaccountservice.util.JwtUtils;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final JwtUtils jwtUtils;

    public PaymentController(PaymentService paymentService, JwtUtils jwtUtils) {
        this.paymentService = paymentService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/top-up/init")
    public InitTopUpPaymentResponse initTopUp(@RequestHeader("Authorization") String authorizationHeader,
                                              @RequestHeader(value = "Origin", required = false) String origin,
                                              @RequestHeader(value = "Referer", required = false) String referer,
                                              @Valid @RequestBody InitTopUpPaymentRequest request) {
        Payment payment = paymentService.initTopUp(
                jwtUtils.extractUserIdFromAuthorizationHeader(authorizationHeader),
                jwtUtils.extractUsernameFromAuthorizationHeader(authorizationHeader),
                request.amount(),
                origin,
                referer
        );
        return new InitTopUpPaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getCheckoutUrl()
        );
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(@RequestHeader("Authorization") String authorizationHeader,
                                      @PathVariable Long paymentId) {
        Payment payment = paymentService.getPaymentForUser(
                paymentId,
                jwtUtils.extractUsernameFromAuthorizationHeader(authorizationHeader)
        );
        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getCheckoutUrl(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getConfirmedAt(),
                payment.getCreditedAt()
        );
    }
}
