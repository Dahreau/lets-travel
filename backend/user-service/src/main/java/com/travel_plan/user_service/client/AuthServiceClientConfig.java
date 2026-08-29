package com.travel_plan.user_service.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// fix/audit-gaps (troubleshooting.md #41) : reutilise le bean @LoadBalanced RestClient.Builder
// deja cable avec mTLS (bundle internal-services) et timeouts par TravelServiceClientConfig -
// aucune config TLS/timeout dupliquee ici, seule la base URL differe (auth-service au lieu de
// travel-service). Meme registre de decouverte statique (spring.cloud.discovery.client.simple),
// voir application.properties pour les 2 instances AUTH_SERVICE_URI_1/2.
@Configuration
public class AuthServiceClientConfig {

    @Bean
    public RestClient authServiceRestClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        return loadBalancedRestClientBuilder.baseUrl("http://auth-service").build();
    }
}
