package com.travel_plan.payment_service.vault;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record VaultKvResponse(@JsonProperty("data") VaultKvData data) {

    public record VaultKvData(@JsonProperty("data") Map<String, String> data) {
    }
}
