package resenkov.work.parkinguserservice.client;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Log4j2
public class AccountServiceClient {

    private final RestClient restClient;

    public AccountServiceClient(RestClient.Builder restClientBuilder,
                                @Value("${account.service.base-url:http://localhost:8081}") String accountServiceBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(accountServiceBaseUrl).build();
    }

    public void createWalletIfAbsent(Long userId, String email) {
        log.info("Подготовка HTTP-запроса в account-service на создание счёта: userId={}, email={}", userId, email);
        try {
            restClient.post()
                    .uri("/accounts/wallets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreateWalletRequest(userId, email))
                    .retrieve()
                    .toBodilessEntity();
            log.info("HTTP-запрос в account-service выполнен успешно: userId={}, email={}", userId, email);
        } catch (RestClientException ex) {
            log.error("Ошибка вызова account-service при создании счёта: userId={}, email={}", userId, email, ex);
            throw new IllegalStateException("Не удалось создать счёт в account-service", ex);
        }
    }

    private record CreateWalletRequest(Long userId, String email) {
    }
}
