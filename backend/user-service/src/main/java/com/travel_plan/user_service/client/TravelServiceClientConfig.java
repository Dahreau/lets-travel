package com.travel_plan.user_service.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

// voir troubleshooting.md #38 - meme pattern mTLS/load-balancing que
// payment-service -> travel-service.
@Configuration
public class TravelServiceClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder(
            @Value("${spring.http.client.ssl.bundle:}") String sslBundleName,
            ObjectProvider<SslBundles> sslBundlesProvider) {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT);
        if (StringUtils.hasText(sslBundleName)) {
            SslBundles sslBundles = sslBundlesProvider.getIfAvailable();
            if (sslBundles != null) {
                SslBundle sslBundle = sslBundles.getBundle(sslBundleName);
                httpClientBuilder.sslContext(sslBundle.createSslContext());
            }
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClientBuilder.build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean
    public RestClient travelServiceRestClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        return loadBalancedRestClientBuilder.baseUrl("http://travel-service").build();
    }
}
