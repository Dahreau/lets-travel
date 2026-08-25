package com.travel_plan.payment_service.client;

import com.travel_plan.payment_service.exception.TravelNotFoundException;
import com.travel_plan.payment_service.exception.TravelPriceNotSetException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.UUID;

// Recupere le prix reel aupres de travel-service plutot que de faire confiance au client
// (voir docs/nouveautes-vs-travel-plan.md), JWT de l'appelant propage tel quel.
@Component
public class TravelServiceClient {

    // Fallback (enonce, section 4) : timeout court (TravelServiceClientConfig) + retry borne ici
    // sur les seules pannes transitoires (5xx, connexion/timeout) - jamais sur un 404 ou un 4xx.
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(200);

    private final RestClient travelServiceRestClient;

    public TravelServiceClient(RestClient travelServiceRestClient) {
        this.travelServiceRestClient = travelServiceRestClient;
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
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return travelServiceRestClient.get()
                        .uri("/api/travels/{id}", travelId)
                        .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                        .retrieve()
                        .body(TravelSummary.class);
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 404) {
                    throw new TravelNotFoundException(travelId);
                }
                if (!isTransient(ex.getStatusCode().value())) {
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
        throw lastError;
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
