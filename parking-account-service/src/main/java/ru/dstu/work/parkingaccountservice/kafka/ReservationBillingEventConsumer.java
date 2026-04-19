package ru.dstu.work.parkingaccountservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.dstu.work.parkingaccountservice.dto.ReservationBillingEvent;
import ru.dstu.work.parkingaccountservice.dto.ReservationBillingRequest;
import ru.dstu.work.parkingaccountservice.service.AccountService;

@Slf4j
@Component
public class ReservationBillingEventConsumer {

    private final AccountService accountService;

    public ReservationBillingEventConsumer(AccountService accountService) {
        this.accountService = accountService;
    }

    @KafkaListener(topics = "reservation-events", groupId = "parking-account-service")
    public void handleReservationBillingEvent(ReservationBillingEvent event) {
        log.info("Received reservation billing event: reservationId={}, operationId={}, status={}",
                event.reservationId(), event.operationId(), event.status());

        accountService.syncReservation(event);
        accountService.applyReservationBilling(new ReservationBillingRequest(
                event.operationId(),
                event.reservationId(),
                event.userEmail(),
                event.status(),
                event.totalAmount(),
                event.refundPercent()
        ));
    }
}
