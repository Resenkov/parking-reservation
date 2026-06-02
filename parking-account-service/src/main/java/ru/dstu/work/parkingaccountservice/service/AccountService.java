package ru.dstu.work.parkingaccountservice.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dstu.work.parkingaccountservice.dto.BillingOperationRequest;
import ru.dstu.work.parkingaccountservice.entity.Account;
import ru.dstu.work.parkingaccountservice.entity.AccountOperation;
import ru.dstu.work.parkingaccountservice.entity.OperationType;
import ru.dstu.work.parkingaccountservice.exception.BadRequestException;
import ru.dstu.work.parkingaccountservice.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@Log4j2
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountLookupService accountLookupService;
    private final AccountOperationService accountOperationService;
    private final ReservationBillingProcessor reservationBillingProcessor;

    public AccountService(AccountRepository accountRepository,
                          AccountLookupService accountLookupService,
                          AccountOperationService accountOperationService,
                          ReservationBillingProcessor reservationBillingProcessor) {
        this.accountRepository = accountRepository;
        this.accountLookupService = accountLookupService;
        this.accountOperationService = accountOperationService;
        this.reservationBillingProcessor = reservationBillingProcessor;
    }

    @Transactional
    public Account createWalletIfAbsent(Long userId, String userEmail) {
        String normalizedEmail = accountLookupService.normalizeEmail(userEmail);
        log.info("Создание/проверка счёта: userId={}, email={}", userId, normalizedEmail);

        Account account = accountLookupService.upsertWallet(userId, normalizedEmail);

        log.info(
                "Счёт готов к работе: accountId={}, userId={}, email={}",
                account.getId(),
                account.getUserId(),
                account.getUserEmail()
        );

        return account;
    }

    @Transactional
    public Account topUp(String userEmail, String operationId, BigDecimal amount) {
        validateAmount(amount);
        String normalizedEmail = accountLookupService.normalizeEmail(userEmail);
        log.info(
                "Пополнение счёта: email={}, operationId={}, amount={}",
                normalizedEmail,
                operationId,
                amount
        );

        Account account = accountLookupService.getAccountByUserEmailForUpdate(normalizedEmail);
        if (accountOperationService.exists(operationId)) {
            log.info("Идемпотентность top-up: операция уже выполнена, operationId={}", operationId);
            return account;
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        accountOperationService.saveOperation(
                operationId,
                normalizedEmail,
                account.getUserId(),
                account.getId(),
                null,
                OperationType.TOP_UP,
                amount,
                "пополнение счёта"
        );

        log.info("Пополнение выполнено: accountId={}, новый баланс={}", account.getId(), account.getBalance());
        return account;
    }

    @Transactional
    public Account creditFromConfirmedPayment(String userEmail,
                                              String operationId,
                                              BigDecimal amount,
                                              Long paymentId) {
        validateAmount(amount);
        String normalizedEmail = accountLookupService.normalizeEmail(userEmail);
        log.info(
                "Crediting confirmed payment: email={}, operationId={}, amount={}, paymentId={}",
                normalizedEmail,
                operationId,
                amount,
                paymentId
        );

        Account account = accountLookupService.getAccountByUserEmailForUpdate(normalizedEmail);
        if (accountOperationService.exists(operationId)) {
            log.info("Confirmed payment already credited, operationId={}", operationId);
            return account;
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        accountOperationService.saveOperation(
                operationId,
                normalizedEmail,
                account.getUserId(),
                account.getId(),
                null,
                OperationType.TOP_UP,
                amount,
                "пополнение через подтвержденный платеж #" + paymentId
        );
        log.info(
                "Confirmed payment credited: accountId={}, paymentId={}, balance={}",
                account.getId(),
                paymentId,
                account.getBalance()
        );
        return account;
    }

    @Transactional
    public Account applyReservationBilling(BillingOperationRequest request) {
        String normalizedEmail = accountLookupService.normalizeEmail(request.userEmail());
        log.info(
                "Обработка биллингового события: operationId={}, reservationId={}, status={}, userId={}, email={}",
                request.operationId(),
                request.reservationId(),
                request.status(),
                request.userId(),
                normalizedEmail
        );

        Account account = accountLookupService.getAccountForBilling(request.userId(), normalizedEmail);
        if (accountOperationService.exists(request.operationId())) {
            log.info("Идемпотентность billing: операция уже выполнена, operationId={}", request.operationId());
            return account;
        }

        return reservationBillingProcessor.apply(account, request, normalizedEmail, request.userId());
    }

    public List<AccountOperation> getOperationHistory(String userEmail) {
        log.info("Чтение истории операций: email={}", userEmail);
        return accountOperationService.findHistory(accountLookupService.normalizeEmail(userEmail));
    }

    public Account getAccount(String userEmail) {
        log.info("Чтение счёта: email={}", userEmail);
        return accountLookupService.getAccountByUserEmail(accountLookupService.normalizeEmail(userEmail));
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Сумма должна быть больше 0");
        }
    }
}
