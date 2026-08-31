package com.travel_plan.api_gateway.gateway;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

// RestClient.Builder custom requis pour le bundle mTLS "internal-services" (voir troubleshooting.md #11).
// Pas de @LoadBalanced ici : lb() resout deja l'instance concrete avant http() (voir troubleshooting.md #39).
@Configuration
public class GatewayHttpClientConfig {

    // READ_TIMEOUT a 30s (pas 5s) : le gateway route TOUTES les requetes, y compris les plus lourdes
    // (POST /api/travels imbrique), contrairement a un client cible comme TravelServiceClientConfig.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public RestClient.Builder gatewayRestClientBuilder(
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
}
