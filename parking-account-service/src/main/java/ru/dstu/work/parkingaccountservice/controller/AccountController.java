package ru.dstu.work.parkingaccountservice.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.Valid;
import lombok.extern.log4j.Log4j2;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.dstu.work.parkingaccountservice.dto.BillingOperationRequest;
import ru.dstu.work.parkingaccountservice.dto.CreateWalletRequest;
import ru.dstu.work.parkingaccountservice.dto.TopUpRequest;
import ru.dstu.work.parkingaccountservice.entity.AccountOperation;
import ru.dstu.work.parkingaccountservice.entity.Account;
import ru.dstu.work.parkingaccountservice.service.AccountService;

import java.util.List;

@RestController
@RequestMapping("/accounts")
@Validated
@Log4j2
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/wallets")
    public Account createWalletIfAbsent(@Valid @RequestBody CreateWalletRequest request) {
        log.info("Получен запрос создания/проверки счёта: userId={}, email={}", request.userId(), request.email());
        return accountService.createWalletIfAbsent(request.userId(), request.email());
    }

    @PostMapping("/{email}/top-up")
    public Account topUp(@PathVariable @Email(message = "Email должен быть корректным") String email,
                         @Valid @RequestBody TopUpRequest request) {
        log.info(
                "Получен запрос пополнения счёта: email={}, operationId={}, amount={}",
                email,
                request.operationId(),
                request.amount()
        );
        return accountService.topUp(email, request.operationId(), request.amount());
    }

    @PostMapping({"/reservation-event", "/billing-operations"})
    public Account applyReservationEvent(@Valid @RequestBody BillingOperationRequest request) {
        log.info(
                "Получен запрос биллинговой операции: operationId={}, reservationId={}, status={}, userEmail={}",
                request.operationId(),
                request.reservationId(),
                request.status(),
                request.userEmail()
        );
        return accountService.applyReservationBilling(request);
    }

    @GetMapping("/{email}")
    public Account getWallet(@PathVariable @Email(message = "Email должен быть корректным") String email) {
        log.info("Получен запрос на чтение счёта: email={}", email);
        return accountService.getAccount(email);
    }

    @GetMapping("/{email}/operations")
    public List<AccountOperation> history(@PathVariable @Email(message = "Email должен быть корректным") String email) {
        log.info("Получен запрос истории операций: email={}", email);
        return accountService.getOperationHistory(email);
    }
}
