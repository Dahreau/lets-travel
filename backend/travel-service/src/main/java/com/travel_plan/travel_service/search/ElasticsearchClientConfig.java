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

// Client bas niveau construit a la main plutot que le starter Spring Data Elasticsearch (voir
// troubleshooting.md #11). Pas de TLS : Elasticsearch reste interne au reseau Docker.
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
