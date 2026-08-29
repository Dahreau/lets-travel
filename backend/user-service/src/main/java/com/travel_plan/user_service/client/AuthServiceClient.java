package com.travel_plan.user_service.client;

import com.travel_plan.user_service.domain.Role;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// voir troubleshooting.md #41 - supprime le compte de connexion associe (evite un compte
// "fantome" apres suppression du profil, self-service ou admin).
@Component
public class AuthServiceClient {

    private final RestClient authServiceRestClient;

    public AuthServiceClient(RestClient authServiceRestClient) {
        this.authServiceRestClient = authServiceRestClient;
    }

    // Volontairement PAS fail-closed comme TravelServiceClient : ici c'est une ecriture destructive,
    // si auth-service est injoignable on arrete la suppression plutot que de creer un compte fantome.
    public void deleteAccountByUserId(UUID userId, String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IllegalStateException("Missing Authorization header for account deletion");
        }
        try {
            authServiceRestClient.delete()
                    .uri("/api/auth/accounts/by-user/{userId}", userId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() != 404) {
                throw ex;
            }
            // 404 : deja supprime cote auth-service (ou jamais cree, cas ADMIN par defaut sans
            // fiche User) - pas bloquant, le profil local peut etre supprime normalement.
        }
    }

    // Cree le compte de connexion associe a un profil (voir UserService.create()) - jamais
    // appele pour ADMIN. Erreur propagee telle quelle pour annuler la creation du profil.
    public void createAccount(String username, String password, Role role, UUID userId, String authorizationHeader) {
        authServiceRestClient.post()
                .uri("/api/auth/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .body(new CreateAccountBody(username, password, role, userId))
                .retrieve()
                .toBodilessEntity();
    }

    private record CreateAccountBody(String username, String password, Role role, UUID userId) {
    }
}
