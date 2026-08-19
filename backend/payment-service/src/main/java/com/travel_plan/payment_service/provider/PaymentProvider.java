package com.travel_plan.payment_service.provider;

import com.travel_plan.payment_service.domain.ProviderType;

public interface PaymentProvider {

    ProviderType type();

    ChargeResult charge(ChargeRequest request);

    void refund(String providerReference);
}
