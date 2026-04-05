package ru.dstu.work.parkingaccountservice.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dstu.work.parkingaccountservice.dto.ReservationBillingRequest;
import ru.dstu.work.parkingaccountservice.entity.*;
import ru.dstu.work.parkingaccountservice.exception.BadRequestException;
import ru.dstu.work.parkingaccountservice.repository.AccountOperationRepository;
import ru.dstu.work.parkingaccountservice.repository.AccountRepository;
import ru.dstu.work.parkingaccountservice.repository.ReservationLedgerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class AccountService {

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
    public Account topUp(String userEmail, String operationId, BigDecimal amount) {
        validateAmount(amount);
        Account account = getAccountByUserEmail(userEmail);
        if (operationRepository.findByOperationId(operationId).isPresent()) {
            return account;
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        saveOperation(operationId, userEmail, account.getId(), null, OperationType.TOP_UP, amount, "wallet top up");
        return account;
    }

    @Transactional
    public Account applyReservationBilling(ReservationBillingRequest request) {
        Account account = getAccountByUserEmail(request.userEmail());
        if (operationRepository.findByOperationId(request.operationId()).isPresent()) {
            return account;
        }

        return switch (request.status()) {
            case HOLD -> holdFunds(account, request);
            case ACTIVE, COMPLETED -> captureOnUsage(account, request);
            case CANCELLED -> cancelWithRefundPolicy(account, request);
            case EXPIRED -> expireWithFullRefund(account, request);
            case NO_SHOW -> closeAsNoShow(account, request);
            case CONFIRMED -> account;
        };
    }

    public List<AccountOperation> getOperationHistory(String userEmail) {
        return operationRepository.findByUserEmailOrderByCreatedAtDesc(userEmail);
    }

    public Account getAccount(String userEmail) {
        return getAccountByUserEmail(userEmail);
    }

    private Account holdFunds(Account account, ReservationBillingRequest request) {
        validateAmount(request.totalAmount());
        if (account.getBalance().compareTo(request.totalAmount()) < 0) {
            throw new BadRequestException("Недостаточно средств для HOLD операции");
        }

        account.setBalance(account.getBalance().subtract(request.totalAmount()));
        account.setHeldAmount(account.getHeldAmount().add(request.totalAmount()));
        accountRepository.save(account);

        ReservationLedger ledger = new ReservationLedger();
        ledger.setReservationId(request.reservationId());
        ledger.setUserEmail(request.userEmail());
        ledger.setTotalAmount(request.totalAmount());
        ledger.setHeldAmount(request.totalAmount());
        ledger.setLastStatus(ReservationStatus.HOLD);
        ledgerRepository.save(ledger);

        saveOperation(request.operationId(), request.userEmail(), account.getId(), request.reservationId(), OperationType.HOLD,
                request.totalAmount(), "reservation hold for 5 minutes");
        return account;
    }

    private Account captureOnUsage(Account account, ReservationBillingRequest request) {
        ReservationLedger ledger = getLedger(request.reservationId());

        BigDecimal toCapture = ledger.getHeldAmount();
        if (toCapture.compareTo(BigDecimal.ZERO) > 0) {
            account.setHeldAmount(account.getHeldAmount().subtract(toCapture));
            ledger.setHeldAmount(BigDecimal.ZERO);
            ledger.setCapturedAmount(ledger.getCapturedAmount().add(toCapture));
            saveOperation(request.operationId(), request.userEmail(), account.getId(), request.reservationId(), OperationType.CAPTURE,
                    toCapture, "captured on status " + request.status());
        }
        ledger.setLastStatus(request.status());
        accountRepository.save(account);
        ledgerRepository.save(ledger);
        return account;
    }

    private Account cancelWithRefundPolicy(Account account, ReservationBillingRequest request) {
        ReservationLedger ledger = getLedger(request.reservationId());

        int refundPercent = request.refundPercent() == null ? 0 : request.refundPercent();
        if (refundPercent < 0 || refundPercent > 100) {
            throw new BadRequestException("refundPercent должен быть в диапазоне 0..100");
        }

        BigDecimal held = ledger.getHeldAmount();
        BigDecimal refund = percentOf(held, refundPercent);
        BigDecimal penalty = held.subtract(refund);

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            account.setBalance(account.getBalance().add(refund));
            ledger.setRefundedAmount(ledger.getRefundedAmount().add(refund));
            saveOperation(request.operationId() + "-REFUND", request.userEmail(), account.getId(), request.reservationId(), OperationType.REFUND,
                    refund, "cancel refund " + refundPercent + "%");
        }

        if (penalty.compareTo(BigDecimal.ZERO) > 0) {
            ledger.setPenaltyAmount(ledger.getPenaltyAmount().add(penalty));
            saveOperation(request.operationId(), request.userEmail(), account.getId(), request.reservationId(), OperationType.PENALTY,
                    penalty, "cancel penalty " + (100 - refundPercent) + "%");
        }

        account.setHeldAmount(account.getHeldAmount().subtract(held));
        ledger.setHeldAmount(BigDecimal.ZERO);
        ledger.setLastStatus(ReservationStatus.CANCELLED);
        accountRepository.save(account);
        ledgerRepository.save(ledger);
        return account;
    }

    private Account expireWithFullRefund(Account account, ReservationBillingRequest request) {
        ReservationLedger ledger = getLedger(request.reservationId());

        BigDecimal held = ledger.getHeldAmount();
        account.setBalance(account.getBalance().add(held));
        account.setHeldAmount(account.getHeldAmount().subtract(held));

        ledger.setHeldAmount(BigDecimal.ZERO);
        ledger.setRefundedAmount(ledger.getRefundedAmount().add(held));
        ledger.setLastStatus(ReservationStatus.EXPIRED);

        accountRepository.save(account);
        ledgerRepository.save(ledger);

        saveOperation(request.operationId(), request.userEmail(), account.getId(), request.reservationId(), OperationType.REFUND,
                held, "full refund on EXPIRED");
        return account;
    }

    private Account closeAsNoShow(Account account, ReservationBillingRequest request) {
        ReservationLedger ledger = getLedger(request.reservationId());

        BigDecimal held = ledger.getHeldAmount();
        account.setHeldAmount(account.getHeldAmount().subtract(held));
        ledger.setHeldAmount(BigDecimal.ZERO);
        ledger.setPenaltyAmount(ledger.getPenaltyAmount().add(held));
        ledger.setLastStatus(ReservationStatus.NO_SHOW);

        accountRepository.save(account);
        ledgerRepository.save(ledger);

        saveOperation(request.operationId(), request.userEmail(), account.getId(), request.reservationId(), OperationType.PENALTY,
                held, "no-show penalty 100%");
        return account;
    }

    private ReservationLedger getLedger(Long reservationId) {
        return ledgerRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Ledger по брони не найден"));
    }

    private Account getAccountByUserEmail(String userEmail) {
        return accountRepository.findByUserEmailForUpdate(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("Аккаунт пользователя не найден"));
    }

    private BigDecimal percentOf(BigDecimal total, int percent) {
        return total.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Сумма должна быть больше 0");
        }
    }

    private void saveOperation(String operationId,
                               String userEmail,
                               Long accountId,
                               Long reservationId,
                               OperationType type,
                               BigDecimal amount,
                               String details) {
        if (operationRepository.findByOperationId(operationId).isPresent()) {
            return;
        }
        AccountOperation operation = new AccountOperation();
        operation.setOperationId(operationId);
        operation.setUserEmail(userEmail);
        operation.setAccountId(accountId);
        operation.setReservationId(reservationId);
        operation.setType(type);
        operation.setAmount(amount);
        operation.setDetails(details);
        operationRepository.save(operation);
    }
}
