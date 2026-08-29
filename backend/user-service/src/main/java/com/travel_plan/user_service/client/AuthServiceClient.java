package com.travel_plan.user_service.client;

import com.travel_plan.user_service.domain.Role;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// fix/audit-gaps (troubleshooting.md #41) : supprime le compte de connexion (auth-service)
// associe a un profil supprime cote user-service. Sans cet appel, un utilisateur supprime
// (self-service via DELETE /api/users/me, ou par un admin via DELETE /api/users/{id}) gardait
// un compte "fantome" capable de se reconnecter alors que son profil n'existait plus nulle
// part - bug preexistant sur le chemin ADMIN, corrige en meme temps que le self-service.
// Meme pattern d'appel que TravelServiceClient (JWT de l'appelant propage tel quel, mTLS +
// load balancing via le bean @LoadBalanced partage, voir AuthServiceClientConfig).
@Component
public class AuthServiceClient {

    private final RestClient authServiceRestClient;

    public AuthServiceClient(RestClient authServiceRestClient) {
        this.authServiceRestClient = authServiceRestClient;
    }

    // Volontairement PAS fail-closed comme TravelServiceClient.isSubscriberOfCallingManager :
    // ici c'est une ECRITURE destructive dans un flux de suppression, pas une lecture de
    // controle d'acces. Si auth-service est injoignable, on doit arreter la suppression
    // (l'exception remonte, geree par ApiExceptionHandler.handleUnexpected -> 500) plutot que de supprimer
    // le profil en silence et laisser un compte fantome derriere - l'exact bug qu'on corrige.
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
