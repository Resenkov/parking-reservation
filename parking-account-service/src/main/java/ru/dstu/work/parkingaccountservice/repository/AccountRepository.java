package ru.dstu.work.parkingaccountservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.dstu.work.parkingaccountservice.entity.Account;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            select a.*
            from public.account a
            join public.users u on u.account_id = a.id
            where u.email = :email
            """, nativeQuery = true)
    Optional<Account> findByUserEmailForUpdate(@Param("email") String email);
}
