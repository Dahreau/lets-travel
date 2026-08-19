package com.travel_plan.payment_service.provider;

import com.travel_plan.payment_service.vault.VaultClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PaymentProviderConfig {

    @Bean
    public StripeCredentials stripeCredentials(VaultClient vaultClient) {
        String secretKey = vaultClient.fetchSharedSecret("payment-service/stripe", "secret_key");
        return new StripeCredentials(secretKey);
    }

    @Bean
    public PayPalCredentials payPalCredentials(VaultClient vaultClient) {
        String clientId = vaultClient.fetchSharedSecret("payment-service/paypal", "client_id");
        String clientSecret = vaultClient.fetchSharedSecret("payment-service/paypal", "client_secret");
        return new PayPalCredentials(clientId, clientSecret);
    }

    @Bean
    public RestClient paymentRestClient() {
        return RestClient.create();
    }
}
