package com.travel_plan.payment_service.provider;

import com.travel_plan.payment_service.vault.VaultClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PaymentProviderConfig {

    // fix/audit-gaps (troubleshooting.md #40) : RestClient.create() n'a AUCUN timeout par
    // defaut (connect ni read) - un Stripe/PayPal qui traine ou ne repond jamais bloquait la
    // requete indefiniment, sans meme le filet de securite d'un timeout amont (contrairement
    // aux appels internes, tous timeoutes - voir TravelServiceClientConfig, GatewayHttpClientConfig).
    // 15s de lecture car un fournisseur de paiement externe est naturellement plus lent qu'un
    // appel interne docker (2-3s), mais reste borne.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

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
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
