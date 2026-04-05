package ru.dstu.work.parkingaccountservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dstu.work.parkingaccountservice.entity.AccountOperation;

import java.util.List;
import java.util.Optional;

public interface AccountOperationRepository extends JpaRepository<AccountOperation, Long> {
    Optional<AccountOperation> findByOperationId(String operationId);

    List<AccountOperation> findByUserEmailOrderByCreatedAtDesc(String userEmail);
}
