package ru.dstu.work.parkingaccountservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.dstu.work.parkingaccountservice.entity.Payment;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerPaymentId = :providerPaymentId")
    Optional<Payment> findByProviderPaymentIdForUpdate(@Param("providerPaymentId") String providerPaymentId);
}
