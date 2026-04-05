package ru.dstu.work.parkingaccountservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dstu.work.parkingaccountservice.entity.ReservationLedger;

import java.util.Optional;

public interface ReservationLedgerRepository extends JpaRepository<ReservationLedger, Long> {
    Optional<ReservationLedger> findByReservationId(Long reservationId);
}
