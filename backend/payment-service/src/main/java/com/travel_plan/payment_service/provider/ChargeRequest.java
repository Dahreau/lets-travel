package com.travel_plan.payment_service.provider;

import java.math.BigDecimal;

public record ChargeRequest(BigDecimal amount, String currency, String providerToken) {
}
