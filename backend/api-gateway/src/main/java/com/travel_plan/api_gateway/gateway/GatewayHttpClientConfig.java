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

// RouteConfig proxie chaque service via HandlerFunctions.http(), qui recupere un bean
// RestClient.Builder dans le contexte Spring (spring-cloud-gateway-server-webmvc). Sans ce
// bean, gateway-server-webmvc construit son propre RestClient.Builder par defaut - qui
// n'utilise PAS le bundle mTLS "internal-services" (les proprietes
// spring.ssl.bundle.pem.internal-services.* d'application-docker.properties ne s'appliquent
// qu'aux clients HTTP auto-configures par Spring Boot lui-meme, pas a celui-la) : meme pattern
// que TravelServiceClientConfig de payment-service/user-service (troubleshooting.md #11).
//
// IMPORTANT : pas de @LoadBalanced ici, contrairement a TravelServiceClientConfig. Le filtre
// lb(...) deja present dans RouteConfig resout LUI-MEME le service logique ("auth-service")
// vers une instance concrete (ex. "lets-travel-app-auth-service-2") AVANT que http() n'appelle
// ce RestClient - un bean @LoadBalanced ici tenterait de re-resoudre ce nom d'instance concret
// comme s'il s'agissait encore d'un service logique, et echouerait avec "No instances
// available for lets-travel-app-auth-service-2" (voir troubleshooting.md #39).
@Configuration
public class GatewayHttpClientConfig {

    // 5s etait trop court pour un gateway generique (route TOUTES les requetes, y compris les
    // plus lourdes comme POST /api/travels avec destinations/activites imbriquees) - contrairement
    // a TravelServiceClientConfig qui ne timeout que sur UN appel interne leger precis. Pas de
    // timeout cote nginx (infra/nginx/*.conf) donc 30s reste tres en dessous de son defaut (60s).
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
