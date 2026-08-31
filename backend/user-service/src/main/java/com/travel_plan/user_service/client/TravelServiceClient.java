package com.travel_plan.user_service.client;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// voir troubleshooting.md #38 - verifie que le traveler est abonne a un voyage du manager
// appelant avant de renvoyer son profil complet (corrige un IDOR).
@Component
public class TravelServiceClient {

    private final RestClient travelServiceRestClient;

    public TravelServiceClient(RestClient travelServiceRestClient) {
        this.travelServiceRestClient = travelServiceRestClient;
    }

    // Fail-closed : header absent, service injoignable ou erreur -> traite comme "pas abonne"
    // (evite de fuiter le profil ou de faire planter la requete).
    public boolean isSubscriberOfCallingManager(UUID travelerId, String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return false;
        }
        try {
            SubscriberCheckResponse response = travelServiceRestClient.get()
                    .uri("/api/travels/managers/me/subscribers/{travelerId}", travelerId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(SubscriberCheckResponse.class);
            return response != null && response.subscriber();
        } catch (RestClientResponseException | ResourceAccessException ex) {
            return false;
        }
    }

    public record SubscriberCheckResponse(boolean subscriber) {
    }
}
