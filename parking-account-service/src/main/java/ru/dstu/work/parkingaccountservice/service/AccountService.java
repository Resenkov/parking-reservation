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
        log.info("РЎРѕР·РґР°РЅРёРµ/РїСЂРѕРІРµСЂРєР° СЃС‡С‘С‚Р°: userId={}, email={}", userId, normalizedEmail);
        Account account = upsertWallet(userId, normalizedEmail);
        log.info("РЎС‡С‘С‚ РіРѕС‚РѕРІ Рє СЂР°Р±РѕС‚Рµ: accountId={}, userId={}, email={}", account.getId(), account.getUserId(), account.getUserEmail());
        return account;
    }

    @Transactional
    public Account topUp(String userEmail, String operationId, BigDecimal amount) {
        validateAmount(amount);
        String normalizedEmail = normalizeEmail(userEmail);
        log.info("РџРѕРїРѕР»РЅРµРЅРёРµ СЃС‡С‘С‚Р°: email={}, operationId={}, amount={}", normalizedEmail, operationId, amount);

        Account account = getAccountByUserEmailForUpdate(normalizedEmail);
        if (operationRepository.findByOperationId(operationId).isPresent()) {
            log.info("РРґРµРјРїРѕС‚РµРЅС‚РЅРѕСЃС‚СЊ top-up: РѕРїРµСЂР°С†РёСЏ СѓР¶Рµ РІС‹РїРѕР»РЅРµРЅР°, operationId={}", operationId);
            return account;
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        saveOperation(operationId, normalizedEmail, account.getUserId(), account.getId(), null, OperationType.TOP_UP, amount, "РїРѕРїРѕР»РЅРµРЅРёРµ СЃС‡С‘С‚Р°");
        log.info("РџРѕРїРѕР»РЅРµРЅРёРµ РІС‹РїРѕР»РЅРµРЅРѕ: accountId={}, РЅРѕРІС‹Р№ Р±Р°Р»Р°РЅСЃ={}", account.getId(), account.getBalance());
        return account;
    }

    @Transactional
    public Account applyReservationBilling(BillingOperationRequest request) {
        String normalizedEmail = normalizeEmail(request.userEmail());
        log.info(
                "РћР±СЂР°Р±РѕС‚РєР° Р±РёР»Р»РёРЅРіРѕРІРѕРіРѕ СЃРѕР±С‹С‚РёСЏ: operationId={}, reservationId={}, status={}, userId={}, email={}",
                request.operationId(),
                request.reservationId(),
                request.status(),
                request.userId(),
                normalizedEmail
        );
        Account account = getAccountForBilling(request.userId(), normalizedEmail);
        if (operationRepository.findByOperationId(request.operationId()).isPresent()) {
            log.info("РРґРµРјРїРѕС‚РµРЅС‚РЅРѕСЃС‚СЊ billing: РѕРїРµСЂР°С†РёСЏ СѓР¶Рµ РІС‹РїРѕР»РЅРµРЅР°, operationId={}", request.operationId());
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
        log.info("Р§С‚РµРЅРёРµ РёСЃС‚РѕСЂРёРё РѕРїРµСЂР°С†РёР№: email={}", userEmail);
        return operationRepository.findByUserEmailOrderByCreatedAtDesc(normalizeEmail(userEmail));
    }

    public Account getAccount(String userEmail) {
        log.info("Р§С‚РµРЅРёРµ СЃС‡С‘С‚Р°: email={}", userEmail);
        return getAccountByUserEmail(normalizeEmail(userEmail));
    }

    private Account holdFunds(Account account, BillingOperationRequest request, String userEmail, Long userId) {
        validateAmount(request.totalAmount());
        if (account.getBalance().compareTo(request.totalAmount()) < 0) {
            throw new BadRequestException("РќРµРґРѕСЃС‚Р°С‚РѕС‡РЅРѕ СЃСЂРµРґСЃС‚РІ РґР»СЏ HOLD-РѕРїРµСЂР°С†РёРё");
        }

        log.info(
                "Р’С‹РїРѕР»РЅСЏРµРј HOLD: accountId={}, reservationId={}, amount={}",
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
                "Р·Р°РјРѕСЂРѕР·РєР° СЃСЂРµРґСЃС‚РІ РЅР° 5 РјРёРЅСѓС‚"
        );
        log.info(
                "HOLD РІС‹РїРѕР»РЅРµРЅ: accountId={}, reservationId={}, balance={}, heldAmount={}",
                account.getId(),
                request.reservationId(),
                account.getBalance(),
                account.getHeldAmount()
        );
        return account;
    }

    private Account captureOnUsage(Account account, BillingOperationRequest request, String userEmail, Long userId) {
        ReservationLedger ledger = getLedger(request.reservationId());
        log.info("Р’С‹РїРѕР»РЅСЏРµРј CAPTURE: accountId={}, reservationId={}", account.getId(), request.reservationId());

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
                    "СЃРїРёСЃР°РЅРёРµ СЃСЂРµРґСЃС‚РІ РїСЂРё СЃС‚Р°С‚СѓСЃРµ " + request.status()
            );
        }
        ledger.setLastStatus(request.status());
        accountRepository.save(account);
        ledgerRepository.save(ledger);
        log.info(
                "CAPTURE Р·Р°РІРµСЂС€С‘РЅ: accountId={}, reservationId={}, capturedAmount={}, heldAmount={}",
                account.getId(),
                request.reservationId(),
                ledger.getCapturedAmount(),
                account.getHeldAmount()
        );
        return account;
    }

    private Account cancelWithRefundPolicy(Account account, BillingOperationRequest request, String userEmail, Long userId) {
        ReservationLedger ledger = getLedger(request.reservationId());

        int refundPercent = request.refundPercent() == null ? 0 : request.refundPercent();
        if (refundPercent < 0 || refundPercent > 100) {
            throw new BadRequestException("refundPercent РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РІ РґРёР°РїР°Р·РѕРЅРµ 0..100");
        }

        log.info(
                "РћР±СЂР°Р±РѕС‚РєР° CANCELLED: accountId={}, reservationId={}, refundPercent={}",
                account.getId(),
                request.reservationId(),
                refundPercent
        );

        BigDecimal held = ledger.getHeldAmount();
        BigDecimal refund = percentOf(held, refundPercent);
        BigDecimal penalty = held.subtract(refund);

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            account.setBalance(account.getBalance().add(refund));
            ledger.setRefundedAmount(ledger.getRefundedAmount().add(refund));
            saveOperation(
                    refundOperationId(request.operationId(), penalty),
                    userEmail,
                    userId,
                    account.getId(),
                    request.reservationId(),
                    OperationType.REFUND,
                    refund,
                    "РІРѕР·РІСЂР°С‚ РїСЂРё РѕС‚РјРµРЅРµ " + refundPercent + "%"
            );
        }

        if (penalty.compareTo(BigDecimal.ZERO) > 0) {
            ledger.setPenaltyAmount(ledger.getPenaltyAmount().add(penalty));
            saveOperation(
                    request.operationId(),
                    userEmail,
                    userId,
                    account.getId(),
                    request.reservationId(),
                    OperationType.PENALTY,
                    penalty,
                    "С€С‚СЂР°С„ РїСЂРё РѕС‚РјРµРЅРµ " + (100 - refundPercent) + "%"
            );
        }

        account.setHeldAmount(account.getHeldAmount().subtract(held));
        ledger.setHeldAmount(BigDecimal.ZERO);
        ledger.setLastStatus(ReservationStatus.CANCELLED);
        accountRepository.save(account);
        ledgerRepository.save(ledger);
        log.info(
                "CANCELLED РѕР±СЂР°Р±РѕС‚Р°РЅ: accountId={}, reservationId={}, refund={}, penalty={}, balance={}",
                account.getId(),
                request.reservationId(),
                refund,
                penalty,
                account.getBalance()
        );
        return account;
    }

    private Account expireWithFullRefund(Account account, BillingOperationRequest request, String userEmail, Long userId) {
        ReservationLedger ledger = getLedger(request.reservationId());
        log.info("РћР±СЂР°Р±РѕС‚РєР° EXPIRED: accountId={}, reservationId={}", account.getId(), request.reservationId());

        BigDecimal held = ledger.getHeldAmount();
        account.setBalance(account.getBalance().add(held));
        account.setHeldAmount(account.getHeldAmount().subtract(held));

        ledger.setHeldAmount(BigDecimal.ZERO);
        ledger.setRefundedAmount(ledger.getRefundedAmount().add(held));
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
                "РїРѕР»РЅС‹Р№ РІРѕР·РІСЂР°С‚ РїСЂРё EXPIRED"
        );
        log.info(
                "EXPIRED РѕР±СЂР°Р±РѕС‚Р°РЅ: accountId={}, reservationId={}, refund={}, balance={}",
                account.getId(),
                request.reservationId(),
                held,
                account.getBalance()
        );
        return account;
    }

    private Account closeAsNoShow(Account account, BillingOperationRequest request, String userEmail, Long userId) {
        ReservationLedger ledger = getLedger(request.reservationId());
        int refundPercent = request.refundPercent() == null ? DEFAULT_NO_SHOW_REFUND_PERCENT : request.refundPercent();
        if (refundPercent < 0 || refundPercent > 100) {
            throw new BadRequestException("refundPercent must be in range 0..100");
        }
        log.info(
                "Processing NO_SHOW: accountId={}, reservationId={}, refundPercent={}",
                account.getId(),
                request.reservationId(),
                refundPercent
        );

        BigDecimal held = ledger.getHeldAmount();
        BigDecimal refund = percentOf(held, refundPercent);
        BigDecimal penalty = held.subtract(refund);

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            account.setBalance(account.getBalance().add(refund));
            ledger.setRefundedAmount(ledger.getRefundedAmount().add(refund));
            saveOperation(
                    refundOperationId(request.operationId(), penalty),
                    userEmail,
                    userId,
                    account.getId(),
                    request.reservationId(),
                    OperationType.REFUND,
                    refund,
                    "no-show refund " + refundPercent + "%"
            );
        }

        account.setHeldAmount(account.getHeldAmount().subtract(held));
        ledger.setHeldAmount(BigDecimal.ZERO);
        if (penalty.compareTo(BigDecimal.ZERO) > 0) {
            ledger.setPenaltyAmount(ledger.getPenaltyAmount().add(penalty));
            saveOperation(
                    request.operationId(),
                    userEmail,
                    userId,
                    account.getId(),
                    request.reservationId(),
                    OperationType.PENALTY,
                    penalty,
                    "no-show penalty " + (100 - refundPercent) + "%"
            );
        }
        ledger.setLastStatus(ReservationStatus.NO_SHOW);

        accountRepository.save(account);
        ledgerRepository.save(ledger);
        log.info(
                "NO_SHOW processed: accountId={}, reservationId={}, refund={}, penalty={}, heldAmount={}",
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
                .orElseThrow(() -> new EntityNotFoundException("Р›РµРґР¶РµСЂ РїРѕ Р±СЂРѕРЅРё РЅРµ РЅР°Р№РґРµРЅ"));
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
            log.info("РџСЂРёРІСЏР·РєР° userId Рє СЃС‡С‘С‚Сѓ: accountId={}, userId={}", account.getId(), userId);
            return accountRepository.save(account);
        }
        if (!account.getUserId().equals(userId)) {
            throw new BadRequestException("РЎС‡С‘С‚ СѓР¶Рµ РїСЂРёРІСЏР·Р°РЅ Рє РґСЂСѓРіРѕРјСѓ userId");
        }
        return account;
    }

    private Account updateEmailIfNeeded(Account account, String userEmail) {
        if (account.getUserEmail() == null) {
            account.setUserEmail(userEmail);
            log.info("РџСЂРёРІСЏР·РєР° email Рє СЃС‡С‘С‚Сѓ: accountId={}, email={}", account.getId(), userEmail);
            return accountRepository.save(account);
        }
        if (!account.getUserEmail().equals(userEmail)) {
            throw new BadRequestException("РЎС‡С‘С‚ СѓР¶Рµ РїСЂРёРІСЏР·Р°РЅ Рє РґСЂСѓРіРѕРјСѓ email");
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
        log.info("РЎРѕР·РґР°РЅ РЅРѕРІС‹Р№ СЃС‡С‘С‚: accountId={}, userId={}, email={}", saved.getId(), saved.getUserId(), saved.getUserEmail());
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
            throw new BadRequestException("РЎСѓРјРјР° РґРѕР»Р¶РЅР° Р±С‹С‚СЊ Р±РѕР»СЊС€Рµ 0");
        }
    }

    private String normalizeEmail(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new BadRequestException("userEmail РѕР±СЏР·Р°С‚РµР»РµРЅ");
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
            log.info("РРґРµРјРїРѕС‚РµРЅС‚РЅРѕСЃС‚СЊ РѕРїРµСЂР°С†РёРё: РѕРїРµСЂР°С†РёСЏ СѓР¶Рµ СЃСѓС‰РµСЃС‚РІСѓРµС‚, operationId={}", operationId);
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
                "РћРїРµСЂР°С†РёСЏ СЃРѕС…СЂР°РЅРµРЅР°: operationId={}, type={}, accountId={}, reservationId={}, amount={}",
                operationId,
                type,
                accountId,
                reservationId,
                amount
        );
    }
}
