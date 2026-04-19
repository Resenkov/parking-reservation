package ru.dstu.work.parkingaccountservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.dstu.work.parkingaccountservice.entity.AccountReservation;

import java.util.Optional;

public interface AccountReservationRepository extends JpaRepository<AccountReservation, Long> {
    Optional<AccountReservation> findByReservationId(Long reservationId);
}
