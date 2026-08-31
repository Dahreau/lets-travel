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

    // voir troubleshooting.md #38 - corrige l'IDOR GET /api/users/{id} (TRAVEL_MANAGER ne voit que
    // ses abonnes) ; un ADMIN garde un acces total (callerIsAdmin=true).
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
                // voir troubleshooting.md #41 - @AssertTrue garantit deja acceptedPrivacyPolicy=true ici,
                // on horodate juste le consentement.
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

    // voir troubleshooting.md #41 - supprime l'Account (auth-service) AVANT le User : si l'appel
    // echoue, le profil local n'est pas supprime (pas de compte "fantome" orphelin).
    public void delete(UUID id, String authorizationHeader) {
        User user = getOrThrow(id);
        authServiceClient.deleteAccountByUserId(id, authorizationHeader);
        userRepository.delete(user);
    }

    // voir troubleshooting.md #41 - droit d'acces/portabilite RGPD ; reutilise UserResponse
    // plutot qu'un DTO dedie.
    public UserResponse me(Authentication authentication) {
        UUID userId = requireUserId(authentication);
        return UserResponse.from(getOrThrow(userId));
    }

    // voir troubleshooting.md #41 - droit a l'effacement RGPD, self-service ; reutilise
    // delete(id, header) pour ne pas dupliquer la logique cross-service.
    public void deleteMe(Authentication authentication, String authorizationHeader) {
        UUID userId = requireUserId(authentication);
        delete(userId, authorizationHeader);
    }

    // Meme pattern de garde que TravelerStatsService.requireTravelerId (travel-service) : userId
    // non-null requis ; jamais le cas pour un compte ADMIN par defaut sans fiche User.
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
