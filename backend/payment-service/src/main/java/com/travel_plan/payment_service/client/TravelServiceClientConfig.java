package com.travel_plan.payment_service.client;

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

// Premier appel inter-service du projet (voir docs/nouveautes-vs-travel-plan.md) : reproduit
// le meme load balancing entre replicas (2 instances) qu'api-gateway, pour cet appel precis.
@Configuration
public class TravelServiceClientConfig {

    // Fallback (enonce, section 4) : desormais toujours borne dans le temps (avant, seul le
    // profil docker en avait) - couple au retry de TravelServiceClient contre un service en panne.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    // payment-service n'a pas de RestClient.Builder auto-configure (voir troubleshooting.md #11) : builder manuel.
    // @LoadBalanced resout "http://travel-service" vers une des 2 instances ; SSLContext mTLS actif seulement en profil docker.
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
