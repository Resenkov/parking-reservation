package ru.dstu.work.parkingaccountservice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dstu.work.parkingaccountservice.dto.BillingOperationRequest;
import ru.dstu.work.parkingaccountservice.dto.CreateWalletRequest;
import ru.dstu.work.parkingaccountservice.dto.TopUpRequest;
import ru.dstu.work.parkingaccountservice.entity.Account;
import ru.dstu.work.parkingaccountservice.entity.AccountOperation;
import ru.dstu.work.parkingaccountservice.exception.BadRequestException;
import ru.dstu.work.parkingaccountservice.service.AccountService;

import java.util.List;

@RestController
@RequestMapping("/accounts")
@Validated
@Log4j2
public class AccountController {

    private final AccountService accountService;
    private final boolean directTopUpEnabled;

    public AccountController(AccountService accountService,
                             @Value("${account.top-up.dev-enabled:false}") boolean directTopUpEnabled) {
        this.accountService = accountService;
        this.directTopUpEnabled = directTopUpEnabled;
    }

    @PostMapping("/wallets")
    public Account createWalletIfAbsent(@Valid @RequestBody CreateWalletRequest request) {
        log.info("Received create/check wallet request: userId={}, email={}", request.userId(), request.email());
        return accountService.createWalletIfAbsent(request.userId(), request.email());
    }

    @PostMapping("/{email}/top-up")
    public Account topUp(@PathVariable @Email(message = "Email must be valid") String email,
                         @Valid @RequestBody TopUpRequest request) {
        if (!directTopUpEnabled) {
            throw new BadRequestException("Прямое пополнение отключено. Используйте платежный сценарий.");
        }
        log.info(
                "Received direct top-up request: email={}, operationId={}, amount={}",
                email,
                request.operationId(),
                request.amount()
        );
        return accountService.topUp(email, request.operationId(), request.amount());
    }

    @PostMapping({"/reservation-event", "/billing-operations"})
    public Account applyReservationEvent(@Valid @RequestBody BillingOperationRequest request) {
        log.info(
                "Received billing request: operationId={}, reservationId={}, status={}, userEmail={}",
                request.operationId(),
                request.reservationId(),
                request.status(),
                request.userEmail()
        );
        return accountService.applyReservationBilling(request);
    }

    @GetMapping("/{email}")
    public Account getWallet(@PathVariable @Email(message = "Email must be valid") String email) {
        log.info("Received wallet read request: email={}", email);
        return accountService.getAccount(email);
    }

    @GetMapping("/{email}/operations")
    public List<AccountOperation> history(@PathVariable @Email(message = "Email must be valid") String email) {
        log.info("Received wallet operations request: email={}", email);
        return accountService.getOperationHistory(email);
    }
}
