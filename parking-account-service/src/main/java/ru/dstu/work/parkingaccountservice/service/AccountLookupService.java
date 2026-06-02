package ru.dstu.work.parkingaccountservice.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.dstu.work.parkingaccountservice.entity.Account;
import ru.dstu.work.parkingaccountservice.entity.AccountStatus;
import ru.dstu.work.parkingaccountservice.exception.BadRequestException;
import ru.dstu.work.parkingaccountservice.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@Log4j2
public class AccountLookupService {

    private final AccountRepository accountRepository;

    public AccountLookupService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account getAccountForBilling(Long userId, String userEmail) {
        return upsertWallet(userId, userEmail);
    }

    public Account upsertWallet(Long userId, String userEmail) {
        if (userId != null) {
            Account byUserId = accountRepository.findByUserIdForUpdate(userId).orElse(null);
            if (byUserId != null) {
                return updateEmailIfNeeded(byUserId, userEmail);
            }
        }

        Account byEmail = accountRepository.findByUserEmailForUpdate(userEmail).orElse(null);
        if (byEmail != null) {
            return updateUserIdIfNeeded(byEmail, userId);
        }

        return createDefaultAccount(userId, userEmail);
    }

    public Account getAccountByUserEmail(String userEmail) {
        return accountRepository.findByUserEmail(userEmail)
                .orElseGet(() -> createDefaultAccount(userEmail));
    }

    public Account getAccountByUserEmailForUpdate(String userEmail) {
        return accountRepository.findByUserEmailForUpdate(userEmail)
                .orElseGet(() -> createDefaultAccount(userEmail));
    }

    public String normalizeEmail(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new BadRequestException("userEmail обязателен");
        }
        return userEmail.trim().toLowerCase(Locale.ROOT);
    }

    private Account updateUserIdIfNeeded(Account account, Long userId) {
        if (userId == null) {
            return account;
        }

        if (account.getUserId() == null) {
            account.setUserId(userId);
            log.info("Привязка userId к счёту: accountId={}, userId={}", account.getId(), userId);
            return accountRepository.save(account);
        }

        if (!account.getUserId().equals(userId)) {
            throw new BadRequestException("Счёт уже привязан к другому userId");
        }

        return account;
    }

    private Account updateEmailIfNeeded(Account account, String userEmail) {
        if (account.getUserEmail() == null) {
            account.setUserEmail(userEmail);
            log.info("Привязка email к счёту: accountId={}, email={}", account.getId(), userEmail);
            return accountRepository.save(account);
        }

        if (!account.getUserEmail().equals(userEmail)) {
            throw new BadRequestException("Счёт уже привязан к другому email");
        }

        return account;
    }

    private Account createDefaultAccount(String userEmail) {
        return createDefaultAccount(null, userEmail);
    }

    private Account createDefaultAccount(Long userId, String userEmail) {
        Account account = new Account();
        account.setUserId(userId);
        account.setUserEmail(userEmail);
        account.setBalance(BigDecimal.ZERO);
        account.setHeldAmount(BigDecimal.ZERO);
        account.setStatus(AccountStatus.OPEN);

        Account saved = accountRepository.save(account);
        log.info("Создан новый счёт: accountId={}, userId={}, email={}", saved.getId(), saved.getUserId(), saved.getUserEmail());
        return saved;
    }
}
