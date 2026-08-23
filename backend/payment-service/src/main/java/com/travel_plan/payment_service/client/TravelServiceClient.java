package com.travel_plan.payment_service.client;

import com.travel_plan.payment_service.exception.TravelNotFoundException;
import com.travel_plan.payment_service.exception.TravelPriceNotSetException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

// Appelle travel-service pour recuperer le prix reel d'un voyage plutot que de faire
// confiance a un montant fourni par le client (voir docs/nouveautes-vs-travel-plan.md).
// Le JWT du traveler appelant est propage tel quel (voir PaymentController) : c'est ce
// meme JWT que travel-service valide deja pour ses propres routes GET /api/travels/**.
@Component
public class TravelServiceClient {

    private final RestClient travelServiceRestClient;

    public TravelServiceClient(RestClient travelServiceRestClient) {
        this.travelServiceRestClient = travelServiceRestClient;
    }

    // Recupere le voyage aupres de travel-service et s'assure qu'il a un prix reel avant
    // de laisser payment-service continuer - un voyage cree avant la migration V4 (prix
    // non renseigne) ne doit jamais generer un paiement a un montant devine.
    public TravelSummary getPricedTravel(UUID travelId, String authorizationHeader) {
        TravelSummary summary = fetchTravelSummary(travelId, authorizationHeader);
        if (summary.price() == null || summary.currency() == null) {
            throw new TravelPriceNotSetException(travelId);
        }
        return summary;
    }

    private TravelSummary fetchTravelSummary(UUID travelId, String authorizationHeader) {
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
            throw ex;
        }
    }
}
