package ru.dstu.work.parkingaccountservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.dstu.work.parkingaccountservice.dto.BillingOperationRequest;
import ru.dstu.work.parkingaccountservice.entity.Account;
import ru.dstu.work.parkingaccountservice.entity.OperationType;
import ru.dstu.work.parkingaccountservice.entity.ReservationLedger;
import ru.dstu.work.parkingaccountservice.entity.ReservationStatus;
import ru.dstu.work.parkingaccountservice.exception.BadRequestException;
import ru.dstu.work.parkingaccountservice.repository.AccountRepository;
import ru.dstu.work.parkingaccountservice.repository.ReservationLedgerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Log4j2
public class ReservationBillingProcessor {

    private static final int DEFAULT_NO_SHOW_REFUND_PERCENT = 0;

    private final AccountRepository accountRepository;
    private final ReservationLedgerRepository ledgerRepository;
    private final AccountOperationService accountOperationService;

    public ReservationBillingProcessor(AccountRepository accountRepository,
                                       ReservationLedgerRepository ledgerRepository,
                                       AccountOperationService accountOperationService) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.accountOperationService = accountOperationService;
    }

    public Account apply(Account account, BillingOperationRequest request, String userEmail, Long userId) {
        return switch (request.status()) {
            case HOLD -> holdFunds(account, request, userEmail, userId);
            case ACTIVE -> captureOnUsage(account, request, userEmail, userId);
            case CANCELLED -> cancelWithRefundPolicy(account, request, userEmail, userId);
            case EXPIRED -> expireWithFullRefund(account, request, userEmail, userId);
            case NO_SHOW -> closeAsNoShow(account, request, userEmail, userId);
            case COMPLETED, CONFIRMED -> account;
        };
    }

    private Account holdFunds(Account account, BillingOperationRequest request, String userEmail, Long userId) {
        validateAmount(request.totalAmount());
        if (account.getBalance().compareTo(request.totalAmount()) < 0) {
            throw new BadRequestException("Недостаточно средств для HOLD-операции");
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

        accountOperationService.saveOperation(
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

        log.info("Выполняем CAPTURE: accountId={}, reservationId={}", account.getId(), request.reservationId());

        BigDecimal toCapture = ledger.getHeldAmount();
        if (toCapture.compareTo(BigDecimal.ZERO) > 0) {
            account.setHeldAmount(account.getHeldAmount().subtract(toCapture));
            ledger.setHeldAmount(BigDecimal.ZERO);
            ledger.setCapturedAmount(ledger.getCapturedAmount().add(toCapture));
            accountOperationService.saveOperation(
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
        int refundPercent = request.refundPercent() == null ? 0 : request.refundPercent();
        validateRefundPercent(refundPercent);

        log.info(
                "Обработка CANCELLED: accountId={}, reservationId={}, refundPercent={}",
                account.getId(),
                request.reservationId(),
                refundPercent
        );

        BigDecimal held = ledger.getHeldAmount();
        BigDecimal refund = percentOf(held, refundPercent);
        BigDecimal penalty = held.subtract(refund);

        applyRefund(account, ledger, request, userEmail, userId, refund, penalty, "возврат при отмене " + refundPercent + "%");
        applyPenalty(account, ledger, request, userEmail, userId, penalty, "штраф при отмене " + (100 - refundPercent) + "%");

        account.setHeldAmount(account.getHeldAmount().subtract(held));
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

        log.info("Обработка EXPIRED: accountId={}, reservationId={}", account.getId(), request.reservationId());

        BigDecimal held = ledger.getHeldAmount();
        account.setBalance(account.getBalance().add(held));
        account.setHeldAmount(account.getHeldAmount().subtract(held));
        ledger.setHeldAmount(BigDecimal.ZERO);
        ledger.setRefundedAmount(ledger.getRefundedAmount().add(held));
        ledger.setLastStatus(ReservationStatus.EXPIRED);

        accountRepository.save(account);
        ledgerRepository.save(ledger);

        accountOperationService.saveOperation(
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
        validateRefundPercent(refundPercent);

        log.info(
                "Обработка NO_SHOW: accountId={}, reservationId={}, refundPercent={}",
                account.getId(),
                request.reservationId(),
                refundPercent
        );

        BigDecimal held = ledger.getHeldAmount();
        BigDecimal refund = percentOf(held, refundPercent);
        BigDecimal penalty = held.subtract(refund);

        applyRefund(account, ledger, request, userEmail, userId, refund, penalty, "возврат при no-show " + refundPercent + "%");
        account.setHeldAmount(account.getHeldAmount().subtract(held));
        ledger.setHeldAmount(BigDecimal.ZERO);
        applyPenalty(account, ledger, request, userEmail, userId, penalty, "штраф при no-show " + (100 - refundPercent) + "%");

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

    private void applyRefund(Account account,
                             ReservationLedger ledger,
                             BillingOperationRequest request,
                             String userEmail,
                             Long userId,
                             BigDecimal refund,
                             BigDecimal penalty,
                             String details) {
        if (refund.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        account.setBalance(account.getBalance().add(refund));
        ledger.setRefundedAmount(ledger.getRefundedAmount().add(refund));
        accountOperationService.saveOperation(
                refundOperationId(request.operationId(), penalty),
                userEmail,
                userId,
                account.getId(),
                request.reservationId(),
                OperationType.REFUND,
                refund,
                details
        );
    }

    private void applyPenalty(Account account,
                              ReservationLedger ledger,
                              BillingOperationRequest request,
                              String userEmail,
                              Long userId,
                              BigDecimal penalty,
                              String details) {
        if (penalty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        ledger.setPenaltyAmount(ledger.getPenaltyAmount().add(penalty));
        accountOperationService.saveOperation(
                request.operationId(),
                userEmail,
                userId,
                account.getId(),
                request.reservationId(),
                OperationType.PENALTY,
                penalty,
                details
        );
    }

    private ReservationLedger getLedger(Long reservationId) {
        return ledgerRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Леджер по брони не найден"));
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
            throw new BadRequestException("Сумма должна быть больше 0");
        }
    }

    private void validateRefundPercent(int refundPercent) {
        if (refundPercent < 0 || refundPercent > 100) {
            throw new BadRequestException("refundPercent должен быть в диапазоне 0..100");
        }
    }
}
