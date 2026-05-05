package ru.dstu.work.parkingaccountservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.dstu.work.parkingaccountservice.dto.BillingOperationRequest;
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

    @Test
    void shouldHoldAndCaptureAndBeIdempotent() {
        String email = "user1@test.com";
        long accountId = createAccount(email, BigDecimal.valueOf(1000));

        Account toppedUp = accountService.topUp(email, "topup-1", BigDecimal.valueOf(100));
        assertThat(toppedUp.getId()).isEqualTo(accountId);

        Account holdAccount = accountService.applyReservationBilling(new BillingOperationRequest(
                "hold-1", 10L, null, email, ReservationStatus.HOLD, BigDecimal.valueOf(300), null
        ));

        assertThat(holdAccount.getBalance()).isEqualByComparingTo("800.00");
        assertThat(holdAccount.getHeldAmount()).isEqualByComparingTo("300.00");

        Account idempotentHold = accountService.applyReservationBilling(new BillingOperationRequest(
                "hold-1", 10L, null, email, ReservationStatus.HOLD, BigDecimal.valueOf(300), null
        ));
        assertThat(idempotentHold.getBalance()).isEqualByComparingTo("800.00");

        Account activeAccount = accountService.applyReservationBilling(new BillingOperationRequest(
                "capture-1", 10L, null, email, ReservationStatus.ACTIVE, BigDecimal.valueOf(300), null
        ));
        assertThat(activeAccount.getHeldAmount()).isEqualByComparingTo("0.00");
        assertThat(activeAccount.getBalance()).isEqualByComparingTo("800.00");
    }

    @Test
    void shouldApplyCancellationRefundPolicy() {
        String email = "user2@test.com";
        createAccount(email, BigDecimal.valueOf(1000));

        accountService.applyReservationBilling(new BillingOperationRequest(
                "hold-2", 20L, null, email, ReservationStatus.HOLD, BigDecimal.valueOf(500), null
        ));

        Account cancelledAccount = accountService.applyReservationBilling(new BillingOperationRequest(
                "cancel-2", 20L, null, email, ReservationStatus.CANCELLED, BigDecimal.valueOf(500), 80
        ));

        assertThat(cancelledAccount.getBalance()).isEqualByComparingTo("900.00");
        assertThat(cancelledAccount.getHeldAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldKeepZeroRefundForNoShow() {
        String email = "user3@test.com";
        createAccount(email, BigDecimal.valueOf(1000));

        accountService.applyReservationBilling(new BillingOperationRequest(
                "hold-3", 30L, null, email, ReservationStatus.HOLD, BigDecimal.valueOf(400), null
        ));

        Account noShowAccount = accountService.applyReservationBilling(new BillingOperationRequest(
                "no-show-3", 30L, null, email, ReservationStatus.NO_SHOW, BigDecimal.valueOf(400), null
        ));

        assertThat(noShowAccount.getBalance()).isEqualByComparingTo("600.00");
        assertThat(noShowAccount.getHeldAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldCreateWalletIfAbsentIdempotently() {
        Account created = accountService.createWalletIfAbsent(101L, "new-user@test.com");
        Account sameWallet = accountService.createWalletIfAbsent(101L, "new-user@test.com");

        assertThat(sameWallet.getId()).isEqualTo(created.getId());
        assertThat(sameWallet.getUserId()).isEqualTo(101L);
        assertThat(sameWallet.getUserEmail()).isEqualTo("new-user@test.com");
    }

    @Test
    void shouldResolveBillingByUserIdWhenProvided() {
        Account wallet = accountService.createWalletIfAbsent(202L, "billing-user@test.com");
        accountService.topUp("billing-user@test.com", "topup-202", BigDecimal.valueOf(100));

        Account billed = accountService.applyReservationBilling(new BillingOperationRequest(
                "hold-202",
                2020L,
                202L,
                "billing-user@test.com",
                ReservationStatus.HOLD,
                BigDecimal.valueOf(50),
                null
        ));

        assertThat(billed.getId()).isEqualTo(wallet.getId());
        assertThat(billed.getHeldAmount()).isEqualByComparingTo("50.00");
    }

    private long createAccount(String email, BigDecimal balance) {
        jdbcTemplate.update(
                "INSERT INTO account(user_email, balance, held_amount, status) VALUES (?, ?, ?, ?)",
                email,
                balance,
                BigDecimal.ZERO,
                "OPEN"
        );
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM account", Long.class);
    }
}
