package ru.dstu.work.parkingaccountservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.dstu.work.parkingaccountservice.entity.PaymentProviderType;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payments")
public class PaymentProperties {

    private PaymentProviderType provider = PaymentProviderType.MOCK;
    private String successUrl = "http://localhost:5173/payment/success";
    private String failureUrl = "http://localhost:5173/payment/failure";
    private String webhookSecret = "";
}
