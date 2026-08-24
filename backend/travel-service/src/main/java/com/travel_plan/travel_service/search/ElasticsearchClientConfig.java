package com.travel_plan.travel_service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Client Elasticsearch bas niveau construit a la main (co.elastic.clients:elasticsearch-java),
// plutot que le starter Spring Data Elasticsearch : evite de devoir deviner les noms de
// classes d'auto-configuration eclatees en modules dans Spring Boot 4.1, sans pouvoir compiler
// localement pour verifier - meme lecon que TravelServiceClientConfig sur payment-service, voir
// troubleshooting.md #11 ("en cas de doute [...] preferer construire le bean explicitement").
// Pas de TLS/authentification ici : Elasticsearch tourne uniquement sur le reseau Docker
// interne (jamais expose a l'host ni a internet, comme Vault/Zipkin actuellement) - compromis
// assume pour tenir le delai, voir docker-compose.yml pour le detail et sa justification.
@Configuration
public class ElasticsearchClientConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient(@Value("${app.elasticsearch.uri}") String uri) {
        URI parsed = URI.create(uri);
        HttpHost host = new HttpHost(parsed.getHost(), parsed.getPort(), parsed.getScheme());
        RestClient restClient = RestClient.builder(host).build();

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));

        return new ElasticsearchClient(transport);
    }
}
