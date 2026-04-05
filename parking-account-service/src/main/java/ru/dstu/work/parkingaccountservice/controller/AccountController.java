package ru.dstu.work.parkingaccountservice.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.dstu.work.parkingaccountservice.dto.ReservationBillingRequest;
import ru.dstu.work.parkingaccountservice.dto.TopUpRequest;
import ru.dstu.work.parkingaccountservice.entity.AccountOperation;
import ru.dstu.work.parkingaccountservice.entity.Account;
import ru.dstu.work.parkingaccountservice.service.AccountService;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{email}/top-up")
    public Account topUp(@PathVariable String email, @Valid @RequestBody TopUpRequest request) {
        return accountService.topUp(email, request.operationId(), request.amount());
    }

    @PostMapping("/reservation-event")
    public Account applyReservationEvent(@Valid @RequestBody ReservationBillingRequest request) {
        return accountService.applyReservationBilling(request);
    }

    @GetMapping("/{email}")
    public Account getWallet(@PathVariable String email) {
        return accountService.getAccount(email);
    }

    @GetMapping("/{email}/operations")
    public List<AccountOperation> history(@PathVariable String email) {
        return accountService.getOperationHistory(email);
    }
}
