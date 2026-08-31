package com.travel_plan.payment_service.client;

import com.travel_plan.payment_service.exception.TravelNotFoundException;
import com.travel_plan.payment_service.exception.TravelPriceNotSetException;
import com.travel_plan.payment_service.exception.TravelServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

// Recupere le prix reel aupres de travel-service plutot que de faire confiance au client
// (voir docs/nouveautes-vs-travel-plan.md), JWT de l'appelant propage tel quel.
@Component
public class TravelServiceClient {

    // Retry borne sur les seules pannes transitoires (5xx, connexion/timeout) - jamais sur un
    // 404 ou un 4xx. Le circuit breaker prend le relais si la panne persiste au-dela des retries.
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(200);
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);

    private final RestClient travelServiceRestClient;
    private final CircuitBreaker circuitBreaker;

    // 2 constructeurs (celui-ci + celui avec CircuitBreaker en injection pour les tests) :
    // @Autowired obligatoire pour lever l'ambiguite, sinon Spring refuse de choisir.
    @Autowired
    public TravelServiceClient(RestClient travelServiceRestClient) {
        this(travelServiceRestClient, new CircuitBreaker(FAILURE_THRESHOLD, OPEN_DURATION, Clock.systemUTC()));
    }

    TravelServiceClient(RestClient travelServiceRestClient, CircuitBreaker circuitBreaker) {
        this.travelServiceRestClient = travelServiceRestClient;
        this.circuitBreaker = circuitBreaker;
    }

    // S'assure que le voyage a un prix reel avant de continuer - un voyage pre-migration V4
    // (prix non renseigne) ne doit jamais generer un paiement a un montant devine.
    public TravelSummary getPricedTravel(UUID travelId, String authorizationHeader) {
        TravelSummary summary = fetchTravelSummary(travelId, authorizationHeader);
        if (summary.price() == null || summary.currency() == null) {
            throw new TravelPriceNotSetException(travelId);
        }
        return summary;
    }

    private TravelSummary fetchTravelSummary(UUID travelId, String authorizationHeader) {
        if (!circuitBreaker.allowsRequest()) {
            throw new TravelServiceUnavailableException();
        }

        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                TravelSummary summary = travelServiceRestClient.get()
                        .uri("/api/travels/{id}", travelId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                        .retrieve()
                        .body(TravelSummary.class);
                circuitBreaker.recordSuccess();
                return summary;
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 404) {
                    circuitBreaker.recordSuccess();
                    throw new TravelNotFoundException(travelId);
                }
                if (!isTransient(ex.getStatusCode().value())) {
                    circuitBreaker.recordSuccess();
                    throw ex;
                }
                lastError = ex;
            } catch (ResourceAccessException ex) {
                lastError = ex;
            }
            if (attempt < MAX_ATTEMPTS) {
                sleep(RETRY_DELAY);
            }
        }
        circuitBreaker.recordFailure();
        throw new TravelServiceUnavailableException(lastError);
    }

    private static boolean isTransient(int status) {
        return status == 502 || status == 503 || status == 504;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying travel-service call", ex);
        }
    }
}
