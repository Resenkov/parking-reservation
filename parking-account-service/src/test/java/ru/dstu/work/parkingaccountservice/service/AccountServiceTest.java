package ru.dstu.work.parkingaccountservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.dstu.work.parkingaccountservice.dto.ReservationBillingRequest;
import ru.dstu.work.parkingaccountservice.entity.Account;
import ru.dstu.work.parkingaccountservice.entity.ReservationStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareUsersTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (id BIGINT AUTO_INCREMENT PRIMARY KEY, email VARCHAR(255), account_id BIGINT)");
    }

    @Test
    void shouldHoldAndCaptureAndBeIdempotent() {
        String email = "user1@test.com";
        long accountId = createUserWithAccount(email, BigDecimal.valueOf(1000));

        Account toppedUp = accountService.topUp(email, "topup-1", BigDecimal.valueOf(100));
        assertThat(toppedUp.getId()).isEqualTo(accountId);

        Account holdAccount = accountService.applyReservationBilling(new ReservationBillingRequest(
                "hold-1", 10L, email, ReservationStatus.HOLD, BigDecimal.valueOf(300), null
        ));

        assertThat(holdAccount.getBalance()).isEqualByComparingTo("800.00");
        assertThat(holdAccount.getHeldAmount()).isEqualByComparingTo("300.00");

        Account idempotentHold = accountService.applyReservationBilling(new ReservationBillingRequest(
                "hold-1", 10L, email, ReservationStatus.HOLD, BigDecimal.valueOf(300), null
        ));
        assertThat(idempotentHold.getBalance()).isEqualByComparingTo("800.00");

        Account activeAccount = accountService.applyReservationBilling(new ReservationBillingRequest(
                "capture-1", 10L, email, ReservationStatus.ACTIVE, BigDecimal.valueOf(300), null
        ));
        assertThat(activeAccount.getHeldAmount()).isEqualByComparingTo("0.00");
        assertThat(activeAccount.getBalance()).isEqualByComparingTo("800.00");
    }

    @Test
    void shouldApplyCancellationRefundPolicy() {
        String email = "user2@test.com";
        createUserWithAccount(email, BigDecimal.valueOf(1000));

        accountService.applyReservationBilling(new ReservationBillingRequest(
                "hold-2", 20L, email, ReservationStatus.HOLD, BigDecimal.valueOf(500), null
        ));

        Account cancelledAccount = accountService.applyReservationBilling(new ReservationBillingRequest(
                "cancel-2", 20L, email, ReservationStatus.CANCELLED, BigDecimal.valueOf(500), 80
        ));

        assertThat(cancelledAccount.getBalance()).isEqualByComparingTo("900.00");
        assertThat(cancelledAccount.getHeldAmount()).isEqualByComparingTo("0.00");
    }

    private long createUserWithAccount(String email, BigDecimal balance) {
        jdbcTemplate.update("INSERT INTO account(balance, held_amount, status) VALUES (?, ?, ?)", balance, BigDecimal.ZERO, "OPEN");
        Long accountId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM account", Long.class);
        jdbcTemplate.update("INSERT INTO users(email, account_id) VALUES (?, ?)", email, accountId);
        return accountId;
    }
}
