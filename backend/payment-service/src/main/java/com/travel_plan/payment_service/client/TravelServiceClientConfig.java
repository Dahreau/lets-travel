package com.travel_plan.payment_service.client;

import java.net.http.HttpClient;
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

// Premier appel inter-service du projet : payment-service doit connaitre le prix reel
// d'un voyage (travel-service) pour ne pas faire confiance a un montant fourni par le
// client (voir docs/nouveautes-vs-travel-plan.md). Jusqu'ici, seul api-gateway avait
// besoin de load balancing entre replicas (2 instances par service, voir
// application.properties) ; ce bean reproduit le meme mecanisme pour cet appel precis.
@Configuration
public class TravelServiceClientConfig {

    // payment-service n'a PAS de RestClient.Builder auto-configure par Spring Boot (voir
    // troubleshooting.md #11) : on part donc de RestClient.builder() a la main, comme le fait
    // deja PaymentProviderConfig.paymentRestClient() pour Stripe/PayPal. Difference ici : cet
    // appel va vers travel-service en interne (mTLS avec le bundle "internal-services"), donc
    // il faut configurer explicitement le SSLContext quand ce bundle existe - uniquement en
    // profil docker (spring.http.client.ssl.bundle vide en profil par defaut, voir
    // application.properties/application-docker.properties). @LoadBalanced fait intercepter ce
    // builder par Spring Cloud LoadBalancer : une URL logique "http://travel-service" est alors
    // resolue vers une des 2 instances configurees dans application.properties
    // (spring.cloud.discovery.client.simple.instances.travel-service[..].uri), schema HTTP/HTTPS
    // inclus - inutile d'ecrire "https://" ici meme en profil docker.
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder(
            @Value("${spring.http.client.ssl.bundle:}") String sslBundleName,
            ObjectProvider<SslBundles> sslBundlesProvider) {
        RestClient.Builder builder = RestClient.builder();
        if (StringUtils.hasText(sslBundleName)) {
            SslBundles sslBundles = sslBundlesProvider.getIfAvailable();
            if (sslBundles != null) {
                SslBundle sslBundle = sslBundles.getBundle(sslBundleName);
                HttpClient httpClient = HttpClient.newBuilder()
                        .sslContext(sslBundle.createSslContext())
                        .build();
                builder.requestFactory(new JdkClientHttpRequestFactory(httpClient));
            }
        }
        return builder;
    }

    @Bean
    public RestClient travelServiceRestClient(@LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        return loadBalancedRestClientBuilder.baseUrl("http://travel-service").build();
    }
}
