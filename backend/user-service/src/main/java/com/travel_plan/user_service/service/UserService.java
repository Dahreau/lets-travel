package com.travel_plan.user_service.service;

import com.travel_plan.user_service.client.AuthServiceClient;
import com.travel_plan.user_service.client.TravelServiceClient;
import com.travel_plan.user_service.domain.Address;
import com.travel_plan.user_service.domain.Role;
import com.travel_plan.user_service.domain.User;
import com.travel_plan.user_service.exception.ForbiddenException;
import com.travel_plan.user_service.exception.UserNotFoundException;
import com.travel_plan.user_service.repository.UserRepository;
import com.travel_plan.user_service.security.AuthenticatedUser;
import com.travel_plan.user_service.security.JwtService;
import com.travel_plan.user_service.web.AddressRequest;
import com.travel_plan.user_service.web.RegistrationResponse;
import com.travel_plan.user_service.web.UserRegistrationRequest;
import com.travel_plan.user_service.web.UserRequest;
import com.travel_plan.user_service.web.UserResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final TravelServiceClient travelServiceClient;
    private final AuthServiceClient authServiceClient;
    private final JwtService jwtService;

    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    // fix/audit-gaps : troubleshooting.md #38 - GET /api/users/{id} etait ouvert a tout
    // TRAVEL_MANAGER pour N'IMPORTE QUEL id (IDOR), alors que l'intention (voir SecurityConfig)
    // etait de ne laisser un manager consulter que le profil d'un de ses propres abonnes. Un
    // ADMIN garde un acces total (callerIsAdmin=true, pas d'appel a travel-service).
    public UserResponse findById(UUID id, boolean callerIsAdmin, String authorizationHeader) {
        User user = getOrThrow(id);
        if (!callerIsAdmin && !travelServiceClient.isSubscriberOfCallingManager(id, authorizationHeader)) {
            throw new ForbiddenException("Non autorise a consulter ce profil");
        }
        return UserResponse.from(user);
    }

    // Provisionne aussi le compte de connexion si username/password fournis - jamais pour
    // ADMIN (pas de fiche User liee, voir AuthServiceClient.createAccount).
    public UserResponse create(UserRequest request, String authorizationHeader) {
        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .role(request.role())
                .build();
        attachAddress(user, request.address());
        User saved = userRepository.save(user);

        boolean hasCredentials = request.username() != null && !request.username().isBlank();
        if (hasCredentials && request.role() != Role.ADMIN) {
            authServiceClient.createAccount(
                    request.username(), request.password(), request.role(), saved.getId(), authorizationHeader);
        }

        return UserResponse.from(saved);
    }

    // Role toujours force a TRAVELER (pas de champ role dans UserRegistrationRequest).
    public RegistrationResponse register(UserRegistrationRequest request) {
        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .role(Role.TRAVELER)
                // fix/audit-gaps (troubleshooting.md #41) : @AssertTrue sur acceptedPrivacyPolicy
                // garantit deja que ce booleen vaut true a ce stade (sinon 400 avant d'arriver ici) -
                // on horodate juste le moment du consentement.
                .privacyAcceptedAt(Instant.now())
                .build();
        attachAddress(user, request.address());
        User saved = userRepository.save(user);
        String registrationToken = jwtService.generateRegistrationToken(saved.getId());
        return new RegistrationResponse(UserResponse.from(saved), registrationToken);
    }

    public UserResponse update(UUID id, UserRequest request) {
        User user = getOrThrow(id);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRole(request.role());
        attachAddress(user, request.address());
        // saveAndFlush : @PreUpdate ne s'execute qu'au flush, sinon updatedAt renvoye est perime.
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    // fix/audit-gaps (troubleshooting.md #41) : authorizationHeader propage vers auth-service
    // (AuthServiceClient.deleteAccountByUserId) pour supprimer le compte de connexion associe -
    // sans ca on recree le bug preexistant du compte "fantome" toujours capable de se reconnecter
    // apres suppression du profil. Supprime l'Account AVANT le User (voir AuthServiceClient) :
    // si l'appel a auth-service echoue, l'exception remonte et le profil local n'est PAS supprime,
    // on ne se retrouve jamais avec un profil supprime mais un compte de connexion orphelin.
    public void delete(UUID id, String authorizationHeader) {
        User user = getOrThrow(id);
        authServiceClient.deleteAccountByUserId(id, authorizationHeader);
        userRepository.delete(user);
    }

    // fix/audit-gaps (troubleshooting.md #41) : droit d'acces/portabilite RGPD - export du
    // profil de l'appelant. Reutilise UserResponse (deja tous les champs pertinents, y compris
    // privacyAcceptedAt) plutot que de creer un DTO dedie.
    public UserResponse me(Authentication authentication) {
        UUID userId = requireUserId(authentication);
        return UserResponse.from(getOrThrow(userId));
    }

    // fix/audit-gaps (troubleshooting.md #41) : droit a l'effacement RGPD - suppression
    // self-service, meme chemin que delete(id, header) cote admin (voir sa Javadoc ci-dessus)
    // pour ne pas dupliquer la logique de suppression cross-service.
    public void deleteMe(Authentication authentication, String authorizationHeader) {
        UUID userId = requireUserId(authentication);
        delete(userId, authorizationHeader);
    }

    // Meme pattern de garde que TravelerStatsService.requireTravelerId cote travel-service :
    // le principal doit etre notre AuthenticatedUser (toujours le cas en prod, voir
    // JwtAuthenticationFilter) et porter un userId non-null (jamais le cas pour un compte ADMIN
    // par defaut sans fiche User - /me n'a pas de sens pour ce compte-la).
    private UUID requireUserId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser caller) || caller.userId() == null) {
            throw new ForbiddenException("Aucun profil utilisateur associe a ce compte");
        }
        return caller.userId();
    }

    private User getOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private void attachAddress(User user, AddressRequest request) {
        if (request == null) {
            user.setAddress(null);
            return;
        }

        Address address = user.getAddress();
        if (address == null) {
            address = new Address();
            address.setUser(user);
            user.setAddress(address);
        }
        address.setStreet(request.street());
        address.setCity(request.city());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());
    }
}
