package com.travel_plan.user_service.client;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// fix/audit-gaps : verifie aupres de travel-service qu'un traveler est bien abonne a l'un des
// voyages du Travel Manager appelant, avant de laisser UserService renvoyer son profil complet -
// corrige l'IDOR sur GET /api/users/{id} (troubleshooting.md #38). JWT de l'appelant propage tel
// quel, meme pattern que payment-service -> travel-service (TravelServiceClient).
@Component
public class TravelServiceClient {

    private final RestClient travelServiceRestClient;

    public TravelServiceClient(RestClient travelServiceRestClient) {
        this.travelServiceRestClient = travelServiceRestClient;
    }

    // Fail-closed : pas de header (ne devrait jamais arriver, SecurityConfig exige un appelant
    // authentifie), travel-service injoignable, timeout ou erreur cote serveur -> traite comme
    // "pas abonne" plutot que de laisser fuiter le profil ou de faire planter la requete en 500.
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
