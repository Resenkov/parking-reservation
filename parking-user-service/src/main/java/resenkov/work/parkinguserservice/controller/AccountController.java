package resenkov.work.parkinguserservice.controller;

import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import resenkov.work.parkinguserservice.entity.Account;
import resenkov.work.parkinguserservice.service.AccountService;

import java.math.BigDecimal;

@RestController
@RequestMapping("/account")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<Account> deposit(@PathVariable Long id, @RequestBody BalanceRequest request) {
        return ResponseEntity.ok(service.addBalance(id, request.getAmount()));
    }

    @Data
    public static class BalanceRequest {
        private BigDecimal amount;
    }
}
