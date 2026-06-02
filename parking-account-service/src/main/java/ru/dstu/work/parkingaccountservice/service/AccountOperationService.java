package ru.dstu.work.parkingaccountservice.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.dstu.work.parkingaccountservice.entity.AccountOperation;
import ru.dstu.work.parkingaccountservice.entity.OperationType;
import ru.dstu.work.parkingaccountservice.repository.AccountOperationRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@Log4j2
public class AccountOperationService {

    private final AccountOperationRepository operationRepository;

    public AccountOperationService(AccountOperationRepository operationRepository) {
        this.operationRepository = operationRepository;
    }

    public boolean exists(String operationId) {
        return operationRepository.findByOperationId(operationId).isPresent();
    }

    public List<AccountOperation> findHistory(String userEmail) {
        return operationRepository.findByUserEmailOrderByCreatedAtDesc(userEmail);
    }

    public void saveOperation(String operationId,
                              String userEmail,
                              Long userId,
                              Long accountId,
                              Long reservationId,
                              OperationType type,
                              BigDecimal amount,
                              String details) {

        if (exists(operationId)) {
            log.info("Идемпотентность операции: операция уже существует, operationId={}", operationId);
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
