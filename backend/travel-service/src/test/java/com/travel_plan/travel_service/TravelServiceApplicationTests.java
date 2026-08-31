package com.travel_plan.travel_service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.travel_plan.travel_service.graph.PlaceRepository;
import com.travel_plan.travel_service.graph.RecommendationRepository;
import com.travel_plan.travel_service.repository.FeedbackRepository;
import com.travel_plan.travel_service.repository.ReportRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.repository.TravelRepository;
import jakarta.persistence.EntityManagerFactory;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jAutoConfiguration;
import org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.neo4j.autoconfigure.Neo4jAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
		Neo4jAutoConfiguration.class,
		DataNeo4jAutoConfiguration.class,
		DataNeo4jRepositoriesAutoConfiguration.class,
		DataSourceAutoConfiguration.class,
		DataSourceInitializationAutoConfiguration.class,
		DataSourceTransactionManagerAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class
})
class TravelServiceApplicationTests {

	@MockitoBean
	private TravelRepository travelRepository;

	@MockitoBean
	private SubscriptionRepository subscriptionRepository;

	// feat/traveler-experience (troubleshooting.md #9) : tout nouveau repository JPA doit avoir son
	// @MockitoBean ici, sinon le contexte ne demarre plus.
	@MockitoBean
	private FeedbackRepository feedbackRepository;

	@MockitoBean
	private ReportRepository reportRepository;

	@MockitoBean
	private PlaceRepository placeRepository;

	// feat/search-and-recommendations - meme regle : nouveau repository Spring Data Neo4j.
	@MockitoBean
	private RecommendationRepository recommendationRepository;

	// ElasticsearchClient est construit a la main (ElasticsearchClientConfig) : sans mock, le contexte
	// tente une vraie connexion TCP au demarrage (troubleshooting.md #16).
	@MockitoBean
	private ElasticsearchClient elasticsearchClient;

	@MockitoBean
	private SecretKey jwtSigningKey;

	@MockitoBean
	private Driver driver;

	@MockitoBean
	private EntityManagerFactory entityManagerFactory;

	@Test
	void contextLoads() {
	}

}
