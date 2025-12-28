package resenkov.work.parkinguserservice.controller;

import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import resenkov.work.parkinguserservice.entity.Account;
import resenkov.work.parkinguserservice.service.AccountService;
import resenkov.work.parkinguserservice.util.JwtUtils;

import java.math.BigDecimal;

@RestController
@RequestMapping("/account")
public class AccountController {

    private final AccountService service;
    private final JwtUtils jwtUtils;

    public AccountController(AccountService service, JwtUtils jwtUtils) {
        this.service = service;
        this.jwtUtils = jwtUtils;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/deposit")
    public ResponseEntity<Account> deposit(@RequestHeader("Authorization") String authorization,
                                           @RequestBody BalanceRequest request) {
        if (!authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authorization.substring(7);
        Long accountId = jwtUtils.extractAccountId(token);
        if (accountId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.addBalance(accountId, request.getAmount()));
    }

    @Data
    public static class BalanceRequest {
        private BigDecimal amount;
    }
}
