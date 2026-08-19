package com.travel_plan.payment_service.vault;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VaultLoginResponse(@JsonProperty("auth") VaultAuth auth) {

    public record VaultAuth(@JsonProperty("client_token") String clientToken) {
    }
}
