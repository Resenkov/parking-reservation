package resenkov.work.parkinguserservice.service;


import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkinguserservice.entity.Account;
import resenkov.work.parkinguserservice.entity.AccountStatus;
import resenkov.work.parkinguserservice.repository.AccountRepository;

import java.math.BigDecimal;

@Service
@Transactional
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public Account findById(Long id) {
        return repository.findById(id).orElseThrow(
                ()->new EntityNotFoundException("Account not found"));
    }

    public Account createDefaultAccount() {
        Account account = new Account();
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.OPEN);
        return repository.save(account);
    }

    public Account addBalance(Long id, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        Account account = findById(id);
        account.setBalance(account.getBalance().add(amount));
        return repository.save(account);
    }
}
