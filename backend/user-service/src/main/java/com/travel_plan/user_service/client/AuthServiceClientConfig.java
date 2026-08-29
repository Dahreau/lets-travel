package com.travel_plan.user_service.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// voir troubleshooting.md #41 - reutilise le bean @LoadBalanced (mTLS + timeouts) de
// TravelServiceClientConfig, seule la base URL differe.
@Configuration
public class AuthServiceClientConfig {

    @Bean
    public RestClient authServiceRestClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        return loadBalancedRestClientBuilder.baseUrl("http://auth-service").build();
    }
}
