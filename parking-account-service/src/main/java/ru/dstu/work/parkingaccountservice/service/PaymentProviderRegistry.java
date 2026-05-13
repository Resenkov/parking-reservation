package ru.dstu.work.parkingaccountservice.service;

import org.springframework.stereotype.Component;
import ru.dstu.work.parkingaccountservice.entity.PaymentProviderType;
import ru.dstu.work.parkingaccountservice.exception.BadRequestException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentProviderRegistry {

    private final Map<PaymentProviderType, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providers = new EnumMap<>(PaymentProviderType.class);
        for (PaymentProvider provider : providers) {
            this.providers.put(provider.getProviderType(), provider);
        }
    }

    public PaymentProvider get(PaymentProviderType providerType) {
        PaymentProvider provider = providers.get(providerType);
        if (provider == null) {
            throw new BadRequestException("Unsupported payment provider: " + providerType);
        }
        return provider;
    }
}
