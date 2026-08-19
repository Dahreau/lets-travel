package com.travel_plan.payment_service.provider;

import com.travel_plan.payment_service.domain.ProviderType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderResolver {

    private final Map<ProviderType, PaymentProvider> providers;

    public PaymentProviderResolver(List<PaymentProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toMap(PaymentProvider::type, Function.identity()));
    }

    public PaymentProvider resolve(ProviderType type) {
        PaymentProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No payment provider registered for " + type);
        }
        return provider;
    }
}
