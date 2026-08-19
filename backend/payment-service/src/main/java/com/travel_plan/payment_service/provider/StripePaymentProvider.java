package com.travel_plan.payment_service.provider;

import com.travel_plan.payment_service.domain.PaymentStatus;
import com.travel_plan.payment_service.domain.ProviderType;
import java.math.RoundingMode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class StripePaymentProvider implements PaymentProvider {

    private final RestClient restClient;
    private final StripeCredentials credentials;
    private final String apiBase;

    public StripePaymentProvider(
            RestClient paymentRestClient, StripeCredentials stripeCredentials,
            @Value("${app.stripe.api-base}") String apiBase) {
        this.restClient = paymentRestClient;
        this.credentials = stripeCredentials;
        this.apiBase = apiBase;
    }

    @Override
    public ProviderType type() {
        return ProviderType.STRIPE;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        long amountInCents =
                request.amount().setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("amount", String.valueOf(amountInCents));
        body.add("currency", request.currency().toLowerCase());
        body.add("payment_method", request.providerToken());
        body.add("confirm", "true");
        body.add("off_session", "true");

        Map<String, Object> response = restClient
                .post()
                .uri(apiBase + "/v1/payment_intents")
                .headers(headers -> {
                    headers.setBasicAuth(credentials.secretKey(), "");
                    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                })
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("id") == null) {
            throw new IllegalStateException("Stripe did not return a payment intent id");
        }

        String status = String.valueOf(response.get("status"));
        PaymentStatus mappedStatus = "succeeded".equals(status) ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;
        return new ChargeResult(String.valueOf(response.get("id")), mappedStatus);
    }

    @Override
    public void refund(String providerReference) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("payment_intent", providerReference);

        Map<String, Object> response = restClient
                .post()
                .uri(apiBase + "/v1/refunds")
                .headers(headers -> {
                    headers.setBasicAuth(credentials.secretKey(), "");
                    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                })
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("id") == null) {
            throw new IllegalStateException("Stripe did not return a refund id for payment_intent " + providerReference);
        }
        if ("failed".equals(String.valueOf(response.get("status")))) {
            throw new IllegalStateException("Stripe refund failed for payment_intent " + providerReference);
        }
    }
}
