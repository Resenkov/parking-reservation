package resenkov.work.parkinguserservice.controller;

import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import resenkov.work.parkinguserservice.entity.Account;
import resenkov.work.parkinguserservice.entity.User;
import resenkov.work.parkinguserservice.service.AccountService;
import resenkov.work.parkinguserservice.service.UserService;
import resenkov.work.parkinguserservice.util.JwtUtils;

import java.math.BigDecimal;

@RestController
@RequestMapping("/account")
public class AccountController {

    private final AccountService service;
    private final UserService userService;
    private final JwtUtils jwtUtils;

    public AccountController(AccountService service, UserService userService, JwtUtils jwtUtils) {
        this.service = service;
        this.userService = userService;
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
        String email = jwtUtils.extractUsername(token);
        User user = userService.findByEmail(email);
        Long accountId = user.getAccountId().getId();
        return ResponseEntity.ok(service.addBalance(accountId, request.getAmount()));
    }

    @Data
    public static class BalanceRequest {
        private BigDecimal amount;
    }
}
