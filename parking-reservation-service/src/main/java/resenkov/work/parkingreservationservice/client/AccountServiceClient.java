package resenkov.work.parkingreservationservice.client;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import resenkov.work.parkingreservationservice.dto.BillingOperationRequest;
import resenkov.work.parkingreservationservice.entity.Reservation;

@Component
@Log4j2
public class AccountServiceClient {

    private final RestClient restClient;

    public AccountServiceClient(RestClient.Builder restClientBuilder,
                                @Value("${account.service.base-url:http://localhost:8081}") String accountServiceBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(accountServiceBaseUrl).build();
    }

    public void applyReservationEvent(Reservation reservation,
                                      Reservation.ReservationStatus status,
                                      Integer refundPercent,
                                      String operationId) {
        log.info(
                "Подготовка billing-запроса в account-service: operationId={}, reservationId={}, status={}, refundPercent={}",
                operationId,
                reservation.getId(),
                status,
                refundPercent
        );
        try {
            restClient.post()
                    .uri("/accounts/billing-operations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new BillingOperationRequest(
                            operationId,
                            reservation.getId(),
                            reservation.getUserId(),
                            reservation.getUserEmail(),
                            status,
                            reservation.getTotalAmount(),
                            refundPercent
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info(
                    "Billing-запрос в account-service выполнен успешно: operationId={}, reservationId={}",
                    operationId,
                    reservation.getId()
            );
        } catch (RestClientException ex) {
            log.error(
                    "Ошибка billing-вызова в account-service: operationId={}, reservationId={}",
                    operationId,
                    reservation.getId(),
                    ex
            );
            throw new IllegalStateException("Ошибка биллинга для брони " + reservation.getId(), ex);
        }
    }
}
