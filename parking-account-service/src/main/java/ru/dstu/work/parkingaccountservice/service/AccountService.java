package ru.dstu.work.parkingaccountservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dstu.work.parkingaccountservice.dto.BillingOperationRequest;
import ru.dstu.work.parkingaccountservice.entity.Account;
import ru.dstu.work.parkingaccountservice.entity.AccountOperation;
import ru.dstu.work.parkingaccountservice.entity.AccountStatus;
import ru.dstu.work.parkingaccountservice.entity.OperationType;
import ru.dstu.work.parkingaccountservice.entity.ReservationLedger;
import ru.dstu.work.parkingaccountservice.entity.ReservationStatus;
import ru.dstu.work.parkingaccountservice.exception.BadRequestException;
import ru.dstu.work.parkingaccountservice.repository.AccountOperationRepository;
import ru.dstu.work.parkingaccountservice.repository.AccountRepository;
import ru.dstu.work.parkingaccountservice.repository.ReservationLedgerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
@Log4j2
public class AccountService {

    private static final int DEFAULT_NO_SHOW_REFUND_PERCENT = 0;

    private final AccountRepository accountRepository;
    private final AccountOperationRepository operationRepository;
    private final ReservationLedgerRepository ledgerRepository;

    public AccountService(AccountRepository accountRepository,
                          AccountOperationRepository operationRepository,
                          ReservationLedgerRepository ledgerRepository) {
        this.accountRepository = accountRepository;
        this.operationRepository = operationRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public Account createWalletIfAbsent(Long userId, String userEmail) {
        String normalizedEmail = normalizeEmail(userEmail);
        log.info("Создание/проверка счёта: userId={}, email={}", userId, normalizedEmail);

        Account account = upsertWallet(userId, normalizedEmail);

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
        String normalizedEmail = normalizeEmail(userEmail);
        log.info(
                "Пополнение счёта: email={}, operationId={}, amount={}",
                normalizedEmail,
                operationId,
                amount
        );
        Account account = getAccountByUserEmailForUpdate(normalizedEmail);
        if (operationRepository.findByOperationId(operationId).isPresent()) {
            log.info(
                    "Идемпотентность top-up: операция уже выполнена, operationId={}",
                    operationId
            );
            return account;
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        saveOperation(
                operationId,
                normalizedEmail,
                account.getUserId(),
                account.getId(),
                null,
                OperationType.TOP_UP,
                amount,
                "пополнение счёта"
        );

        log.info(
                "Пополнение выполнено: accountId={}, новый баланс={}",
                account.getId(),
                account.getBalance()
        );
        return account;
    }

    @Transactional
    public Account creditFromConfirmedPayment(String userEmail,
                                              String operationId,
                                              BigDecimal amount,
                                              Long paymentId) {
        validateAmount(amount);
        String normalizedEmail = normalizeEmail(userEmail);
        log.info(
                "Crediting confirmed payment: email={}, operationId={}, amount={}, paymentId={}",
                normalizedEmail,
                operationId,
                amount,
                paymentId
        );

        Account account = getAccountByUserEmailForUpdate(normalizedEmail);
        if (operationRepository.findByOperationId(operationId).isPresent()) {
            log.info("Confirmed payment already credited, operationId={}", operationId);
            return account;
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        saveOperation(
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
        String normalizedEmail = normalizeEmail(request.userEmail());
        log.info(
                "Обработка биллингового события: operationId={}, reservationId={}, status={}, userId={}, email={}",
                request.operationId(),
                request.reservationId(),
                request.status(),
                request.userId(),
                normalizedEmail
        );

        Account account = getAccountForBilling(request.userId(), normalizedEmail);
        if (operationRepository.findByOperationId(request.operationId()).isPresent()) {
            log.info(
                    "Идемпотентность billing: операция уже выполнена, operationId={}",
                    request.operationId()
            );
            return account;
        }

        return switch (request.status()) {
            case HOLD -> holdFunds(account, request, normalizedEmail, request.userId());
            case ACTIVE -> captureOnUsage(account, request, normalizedEmail, request.userId());
            case CANCELLED -> cancelWithRefundPolicy(account, request, normalizedEmail, request.userId());
            case EXPIRED -> expireWithFullRefund(account, request, normalizedEmail, request.userId());
            case NO_SHOW -> closeAsNoShow(account, request, normalizedEmail, request.userId());
            case COMPLETED, CONFIRMED -> account;
        };
    }

    public List<AccountOperation> getOperationHistory(String userEmail) {
        log.info("Чтение истории операций: email={}", userEmail);

        return operationRepository.findByUserEmailOrderByCreatedAtDesc(
                normalizeEmail(userEmail)
        );
    }

    public Account getAccount(String userEmail) {
        log.info("Чтение счёта: email={}", userEmail);

        return getAccountByUserEmail(normalizeEmail(userEmail));
    }

    private Account holdFunds(Account account, BillingOperationRequest request, String userEmail, Long userId) {
        validateAmount(request.totalAmount());
        if (account.getBalance().compareTo(request.totalAmount()) < 0) {
            throw new BadRequestException(
                    "Недостаточно средств для HOLD-операции"
            );
        }

        log.info(
                "Выполняем HOLD: accountId={}, reservationId={}, amount={}",
                account.getId(),
                request.reservationId(),
                request.totalAmount()
        );

        account.setBalance(account.getBalance().subtract(request.totalAmount()));
        account.setHeldAmount(account.getHeldAmount().add(request.totalAmount()));
        accountRepository.save(account);

        ReservationLedger ledger = new ReservationLedger();
        ledger.setReservationId(request.reservationId());
        ledger.setUserEmail(userEmail);
        ledger.setUserId(userId);
        ledger.setTotalAmount(request.totalAmount());
        ledger.setHeldAmount(request.totalAmount());
        ledger.setLastStatus(ReservationStatus.HOLD);
        ledgerRepository.save(ledger);

        saveOperation(
                request.operationId(),
                userEmail,
                userId,
                account.getId(),
                request.reservationId(),
                OperationType.HOLD,
                request.totalAmount(),
                "заморозка средств на 5 минут"
        );

        log.info(
                "HOLD выполнен: accountId={}, reservationId={}, balance={}, heldAmount={}",
                account.getId(),
                request.reservationId(),
                account.getBalance(),
                account.getHeldAmount()
        );

        return account;
    }

    private Account captureOnUsage(Account account, BillingOperationRequest request, String userEmail, Long userId) {
        ReservationLedger ledger = getLedger(request.reservationId());

        log.info(
                "Выполняем CAPTURE: accountId={}, reservationId={}",
                account.getId(),
                request.reservationId()
        );

        BigDecimal toCapture = ledger.getHeldAmount();
        if (toCapture.compareTo(BigDecimal.ZERO) > 0) {
            account.setHeldAmount(account.getHeldAmount().subtract(toCapture));
            ledger.setHeldAmount(BigDecimal.ZERO);
            ledger.setCapturedAmount(ledger.getCapturedAmount().add(toCapture));
            saveOperation(
                    request.operationId(),
                    userEmail,
                    userId,
                    account.getId(),
                    request.reservationId(),
                    OperationType.CAPTURE,
                    toCapture,
                    "списание средств при статусе " + request.status()
            );
        }
        ledger.setLastStatus(request.status());
        accountRepository.save(account);
        ledgerRepository.save(ledger);

        log.info(
                "CAPTURE завершён: accountId={}, reservationId={}, capturedAmount={}, heldAmount={}",
                account.getId(),
                request.reservationId(),
                ledger.getCapturedAmount(),
                account.getHeldAmount()
        );

        return account;
    }

    private Account cancelWithRefundPolicy(Account account,
                                           BillingOperationRequest request,
                                           String userEmail,
                                           Long userId) {

        ReservationLedger ledger = getLedger(request.reservationId());

        int refundPercent = request.refundPercent() == null
                ? 0
                : request.refundPercent();

        if (refundPercent < 0 || refundPercent > 100) {
            throw new BadRequestException(
                    "refundPercent должен быть в диапазоне 0..100"
            );
        }

        log.info(
                "Обработка CANCELLED: accountId={}, reservationId={}, refundPercent={}",
                account.getId(),
                request.reservationId(),
                refundPercent
        );

        BigDecimal held = ledger.getHeldAmount();

        BigDecimal refund = percentOf(held, refundPercent);

        BigDecimal penalty = held.subtract(refund);

        if (refund.compareTo(BigDecimal.ZERO) > 0) {

            account.setBalance(account.getBalance().add(refund));

            ledger.setRefundedAmount(
                    ledger.getRefundedAmount().add(refund)
            );

            saveOperation(
                    refundOperationId(request.operationId(), penalty),
                    userEmail,
                    userId,
                    account.getId(),
                    request.reservationId(),
                    OperationType.REFUND,
                    refund,
                    "возврат при отмене " + refundPercent + "%"
            );
        }

        if (penalty.compareTo(BigDecimal.ZERO) > 0) {

            ledger.setPenaltyAmount(
                    ledger.getPenaltyAmount().add(penalty)
            );

            saveOperation(
                    request.operationId(),
                    userEmail,
                    userId,
                    account.getId(),
                    request.reservationId(),
                    OperationType.PENALTY,
                    penalty,
                    "штраф при отмене " + (100 - refundPercent) + "%"
            );
        }

        account.setHeldAmount(
                account.getHeldAmount().subtract(held)
        );

        ledger.setHeldAmount(BigDecimal.ZERO);
        ledger.setLastStatus(ReservationStatus.CANCELLED);

        accountRepository.save(account);
        ledgerRepository.save(ledger);

        log.info(
                "CANCELLED обработан: accountId={}, reservationId={}, refund={}, penalty={}, balance={}",
                account.getId(),
                request.reservationId(),
                refund,
                penalty,
                account.getBalance()
        );

        return account;
    }

    private Account expireWithFullRefund(Account account,
                                         BillingOperationRequest request,
                                         String userEmail,
                                         Long userId) {

        ReservationLedger ledger = getLedger(request.reservationId());

        log.info(
                "Обработка EXPIRED: accountId={}, reservationId={}",
                account.getId(),
                request.reservationId()
        );

        BigDecimal held = ledger.getHeldAmount();

        account.setBalance(account.getBalance().add(held));

        account.setHeldAmount(
                account.getHeldAmount().subtract(held)
        );

        ledger.setHeldAmount(BigDecimal.ZERO);

        ledger.setRefundedAmount(
                ledger.getRefundedAmount().add(held)
        );

        ledger.setLastStatus(ReservationStatus.EXPIRED);

        accountRepository.save(account);
        ledgerRepository.save(ledger);

        saveOperation(
                request.operationId(),
                userEmail,
                userId,
                account.getId(),
                request.reservationId(),
                OperationType.REFUND,
                held,
                "полный возврат при EXPIRED"
        );

        log.info(
                "EXPIRED обработан: accountId={}, reservationId={}, refund={}, balance={}",
                account.getId(),
                request.reservationId(),
                held,
                account.getBalance()
        );

        return account;
    }

    private Account closeAsNoShow(Account account,
                                  BillingOperationRequest request,
                                  String userEmail,
                                  Long userId) {

        ReservationLedger ledger = getLedger(request.reservationId());

        int refundPercent = request.refundPercent() == null
                ? DEFAULT_NO_SHOW_REFUND_PERCENT
                : request.refundPercent();

        if (refundPercent < 0 || refundPercent > 100) {
            throw new BadRequestException(
                    "refundPercent должен быть в диапазоне 0..100"
            );
        }

        log.info(
                "Обработка NO_SHOW: accountId={}, reservationId={}, refundPercent={}",
                account.getId(),
                request.reservationId(),
                refundPercent
        );

        BigDecimal held = ledger.getHeldAmount();

        BigDecimal refund = percentOf(held, refundPercent);

        BigDecimal penalty = held.subtract(refund);

        if (refund.compareTo(BigDecimal.ZERO) > 0) {

            account.setBalance(account.getBalance().add(refund));

            ledger.setRefundedAmount(
                    ledger.getRefundedAmount().add(refund)
            );

            saveOperation(
                    refundOperationId(request.operationId(), penalty),
                    userEmail,
                    userId,
                    account.getId(),
                    request.reservationId(),
                    OperationType.REFUND,
                    refund,
                    "возврат при no-show " + refundPercent + "%"
            );
        }

        account.setHeldAmount(
                account.getHeldAmount().subtract(held)
        );

        ledger.setHeldAmount(BigDecimal.ZERO);

        if (penalty.compareTo(BigDecimal.ZERO) > 0) {

            ledger.setPenaltyAmount(
                    ledger.getPenaltyAmount().add(penalty)
            );

            saveOperation(
                    request.operationId(),
                    userEmail,
                    userId,
                    account.getId(),
                    request.reservationId(),
                    OperationType.PENALTY,
                    penalty,
                    "штраф при no-show " + (100 - refundPercent) + "%"
            );
        }

        ledger.setLastStatus(ReservationStatus.NO_SHOW);

        accountRepository.save(account);
        ledgerRepository.save(ledger);

        log.info(
                "NO_SHOW обработан: accountId={}, reservationId={}, refund={}, penalty={}, heldAmount={}",
                account.getId(),
                request.reservationId(),
                refund,
                penalty,
                account.getHeldAmount()
        );

        return account;
    }

    private ReservationLedger getLedger(Long reservationId) {
        return ledgerRepository.findByReservationId(reservationId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Леджер по брони не найден"
                        )
                );
    }

    private Account getAccountForBilling(Long userId, String userEmail) {
        return upsertWallet(userId, userEmail);
    }

    private Account upsertWallet(Long userId, String userEmail) {
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

    private Account getAccountByUserEmail(String userEmail) {
        return accountRepository.findByUserEmail(userEmail)
                .orElseGet(() -> createDefaultAccount(userEmail));
    }

    private Account getAccountByUserEmailForUpdate(String userEmail) {
        return accountRepository.findByUserEmailForUpdate(userEmail)
                .orElseGet(() -> createDefaultAccount(userEmail));
    }

    private Account updateUserIdIfNeeded(Account account, Long userId) {

        if (userId == null) {
            return account;
        }

        if (account.getUserId() == null) {

            account.setUserId(userId);

            log.info(
                    "Привязка userId к счёту: accountId={}, userId={}",
                    account.getId(),
                    userId
            );

            return accountRepository.save(account);
        }

        if (!account.getUserId().equals(userId)) {
            throw new BadRequestException(
                    "Счёт уже привязан к другому userId"
            );
        }

        return account;
    }

    private Account updateEmailIfNeeded(Account account, String userEmail) {

        if (account.getUserEmail() == null) {

            account.setUserEmail(userEmail);

            log.info(
                    "Привязка email к счёту: accountId={}, email={}",
                    account.getId(),
                    userEmail
            );

            return accountRepository.save(account);
        }

        if (!account.getUserEmail().equals(userEmail)) {
            throw new BadRequestException(
                    "Счёт уже привязан к другому email"
            );
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

        log.info(
                "Создан новый счёт: accountId={}, userId={}, email={}",
                saved.getId(),
                saved.getUserId(),
                saved.getUserEmail()
        );

        return saved;
    }

    private BigDecimal percentOf(BigDecimal total, int percent) {
        return total.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private String refundOperationId(String baseOperationId, BigDecimal penalty) {
        return penalty.compareTo(BigDecimal.ZERO) > 0 ? baseOperationId + "-REFUND" : baseOperationId;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(
                    "Сумма должна быть больше 0"
            );
        }
    }


    private String normalizeEmail(String userEmail) {

        if (userEmail == null || userEmail.isBlank()) {
            throw new BadRequestException(
                    "userEmail обязателен"
            );
        }

        return userEmail.trim().toLowerCase(Locale.ROOT);
    }

    private void saveOperation(String operationId,
                               String userEmail,
                               Long userId,
                               Long accountId,
                               Long reservationId,
                               OperationType type,
                               BigDecimal amount,
                               String details) {

        if (operationRepository.findByOperationId(operationId).isPresent()) {

            log.info(
                    "Идемпотентность операции: операция уже существует, operationId={}",
                    operationId
            );

            return;
        }

        AccountOperation operation = new AccountOperation();

        operation.setOperationId(operationId);
        operation.setUserEmail(userEmail);
        operation.setUserId(userId);
        operation.setAccountId(accountId);
        operation.setReservationId(reservationId);
        operation.setType(type);
        operation.setAmount(amount);
        operation.setDetails(details);

        operationRepository.save(operation);

        log.info(
                "Операция сохранена: operationId={}, type={}, accountId={}, reservationId={}, amount={}",
                operationId,
                type,
                accountId,
                reservationId,
                amount
        );
    }
}
