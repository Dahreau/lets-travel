package com.travel_plan.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TravelServiceClient travelServiceClient = mock(TravelServiceClient.class);
    private final AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final UserService userService =
            new UserService(userRepository, travelServiceClient, authServiceClient, jwtService);

    @Test
    void findAllDelegatesToRepository() {
        when(userRepository.findAll()).thenReturn(List.of(existingUser(null)));

        List<UserResponse> result = userService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).firstName()).isEqualTo("Ada");
    }

    @Test
    void findByIdReturnsUserWhenPresentAndCallerIsAdmin() {
        UUID id = UUID.randomUUID();
        User user = existingUser(null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UserResponse response = userService.findById(id, true, "Bearer admin-token");

        assertThat(response.email()).isEqualTo("ada@travel-plan.com");
        assertThat(response.address()).isNull();
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(id, true, "Bearer admin-token"))
                .isInstanceOf(UserNotFoundException.class);
    }

    // fix/audit-gaps : troubleshooting.md #38 - IDOR sur GET /api/users/{id}.
    @Test
    void findByIdAllowsManagerWhenTravelerIsTheirSubscriber() {
        UUID id = UUID.randomUUID();
        User user = existingUser(null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(travelServiceClient.isSubscriberOfCallingManager(id, "Bearer manager-token")).thenReturn(true);

        UserResponse response = userService.findById(id, false, "Bearer manager-token");

        assertThat(response.email()).isEqualTo("ada@travel-plan.com");
    }

    @Test
    void findByIdRejectsManagerWhenTravelerIsNotTheirSubscriber() {
        UUID id = UUID.randomUUID();
        User user = existingUser(null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(travelServiceClient.isSubscriberOfCallingManager(id, "Bearer manager-token")).thenReturn(false);

        assertThatThrownBy(() -> userService.findById(id, false, "Bearer manager-token"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createBuildsUserWithAddressWhenAddressProvided() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AddressRequest addressRequest = new AddressRequest("10 Downing St", "London", "SW1A 2AA", "UK");
        UserRequest request =
                new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", "0102030405", Role.TRAVELER, addressRequest, null, null);

        UserResponse response = userService.create(request, null);

        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.address()).isNotNull();
        assertThat(response.address().city()).isEqualTo("London");
    }

    @Test
    void createBuildsUserWithoutAddressWhenAddressAbsent() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null, null, null);

        UserResponse response = userService.create(request, null);

        assertThat(response.address()).isNull();
    }

    // fix/audit-gaps : provisionne le compte de connexion quand l'ADMIN fournit des
    // identifiants avec le profil - c'etait jusqu'ici impossible depuis l'UI.
    @Test
    void createProvisionsLoginAccountWhenCredentialsProvided() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserRequest request = new UserRequest(
                "Marc", "Manager", "marc.manager@travel-plan.com", null, Role.TRAVEL_MANAGER, null,
                "marc.manager", "Secret123!");

        userService.create(request, "Bearer admin-token");

        verify(authServiceClient)
                .createAccount(eq("marc.manager"), eq("Secret123!"), eq(Role.TRAVEL_MANAGER), any(), eq("Bearer admin-token"));
    }

    @Test
    void createSkipsAccountProvisioningWhenCredentialsAbsent() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null, null, null);

        userService.create(request, "Bearer admin-token");

        verifyNoInteractions(authServiceClient);
    }

    // Un compte ADMIN n'a pas de fiche User liee (rejete par
    // CreateAccountRequest.isUserIdConsistentWithRole) - on ignore les identifiants fournis.
    @Test
    void createNeverProvisionsAccountForAdminRoleEvenWithCredentials() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserRequest request = new UserRequest(
                "Ada", "Admin", "ada.admin@travel-plan.com", null, Role.ADMIN, null, "ada.admin", "Secret123!");

        userService.create(request, "Bearer admin-token");

        verifyNoInteractions(authServiceClient);
    }

    @Test
    void registerForcesTravelerRole() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateRegistrationToken(any())).thenReturn("test-registration-token");
        UserRegistrationRequest request =
                new UserRegistrationRequest("Ada", "Lovelace", "ada@travel-plan.com", "0102030405", null, true);

        RegistrationResponse response = userService.register(request);

        assertThat(response.user().role()).isEqualTo(Role.TRAVELER);
        assertThat(response.user().email()).isEqualTo("ada@travel-plan.com");
        // fix/audit-gaps (troubleshooting.md #41) : consentement RGPD horodate a l'inscription.
        assertThat(response.user().privacyAcceptedAt()).isNotNull();
        assertThat(response.registrationToken()).isEqualTo("test-registration-token");
    }

    @Test
    void registerAttachesAddressWhenProvided() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateRegistrationToken(any())).thenReturn("test-registration-token");
        AddressRequest addressRequest = new AddressRequest("10 Downing St", "London", "SW1A 2AA", "UK");
        UserRegistrationRequest request =
                new UserRegistrationRequest("Ada", "Lovelace", "ada@travel-plan.com", null, addressRequest, true);

        RegistrationResponse response = userService.register(request);

        assertThat(response.user().address()).isNotNull();
        assertThat(response.user().address().city()).isEqualTo("London");
    }

    @Test
    void updateThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null, null, null);

        assertThatThrownBy(() -> userService.update(id, request)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateAddsAddressWhenNoneExistedBefore() {
        UUID id = UUID.randomUUID();
        User user = existingUser(null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AddressRequest addressRequest = new AddressRequest("221B Baker St", "London", "NW1 6XE", "UK");
        UserRequest request =
                new UserRequest("Ada", "Byron", "ada.byron@travel-plan.com", "0605040302", Role.ADMIN, addressRequest, null, null);

        UserResponse response = userService.update(id, request);

        assertThat(response.lastName()).isEqualTo("Byron");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.address().street()).isEqualTo("221B Baker St");
    }

    @Test
    void updateReplacesFieldsOnExistingAddress() {
        UUID id = UUID.randomUUID();
        User user = existingUser(new Address());
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AddressRequest addressRequest = new AddressRequest("1 Rue de Rivoli", "Paris", "75001", "France");
        UserRequest request =
                new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, addressRequest, null, null);

        UserResponse response = userService.update(id, request);

        assertThat(response.address().city()).isEqualTo("Paris");
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void updateRemovesAddressWhenRequestAddressIsNull() {
        UUID id = UUID.randomUUID();
        User user = existingUser(new Address());
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null, null, null);

        UserResponse response = userService.update(id, request);

        assertThat(response.address()).isNull();
        assertThat(user.getAddress()).isNull();
    }

    // voir troubleshooting.md #41 - le compte de connexion (auth-service) doit etre supprime AVANT
    // le profil local (sinon compte "fantome" si l'appel echoue).
    @Test
    void deleteRemovesExistingUserAndItsAuthAccount() {
        UUID id = UUID.randomUUID();
        User user = existingUser(null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.delete(id, "Bearer admin-token");

        verify(authServiceClient).deleteAccountByUserId(id, "Bearer admin-token");
        verify(userRepository).delete(user);
    }

    @Test
    void deleteThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(id, "Bearer admin-token")).isInstanceOf(UserNotFoundException.class);
    }

    // voir troubleshooting.md #41 - si auth-service echoue, le profil local ne doit PAS etre
    // supprime (sinon compte "fantome").
    @Test
    void deleteDoesNotRemoveLocalUserWhenAuthServiceCallFails() {
        UUID id = UUID.randomUUID();
        User user = existingUser(null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        doThrow(new IllegalStateException("auth-service unreachable"))
                .when(authServiceClient)
                .deleteAccountByUserId(id, "Bearer admin-token");

        assertThatThrownBy(() -> userService.delete(id, "Bearer admin-token"))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).delete(any(User.class));
    }

    // fix/audit-gaps (troubleshooting.md #41) : droit d'acces/portabilite RGPD - l'appelant
    // resout SON PROPRE profil via le principal (userId du JWT), jamais un id fourni.
    @Test
    void meReturnsCallersOwnProfile() {
        UUID userId = UUID.randomUUID();
        User user = existingUser(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = userService.me(authenticationFor(userId));

        assertThat(response.email()).isEqualTo("ada@travel-plan.com");
    }

    // Compte ADMIN par defaut : pas de fiche User associee, /me n'a pas de sens pour ce
    // compte-la (meme garde que TravelerStatsService.requireTravelerId cote travel-service).
    @Test
    void meThrowsForbiddenWhenPrincipalHasNoUserId() {
        Authentication authentication = authenticationFor(null);

        assertThatThrownBy(() -> userService.me(authentication)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void meThrowsForbiddenWhenPrincipalIsNotAuthenticatedUser() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThatThrownBy(() -> userService.me(authentication)).isInstanceOf(ForbiddenException.class);
    }

    // fix/audit-gaps (troubleshooting.md #41) : droit a l'effacement RGPD - meme chemin de
    // suppression que delete(id, header) cote admin, juste resolu depuis le principal.
    @Test
    void deleteMeRemovesCallersOwnAccount() {
        UUID userId = UUID.randomUUID();
        User user = existingUser(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.deleteMe(authenticationFor(userId), "Bearer traveler-token");

        verify(authServiceClient).deleteAccountByUserId(userId, "Bearer traveler-token");
        verify(userRepository).delete(user);
    }

    private Authentication authenticationFor(UUID userId) {
        AuthenticatedUser principal = new AuthenticatedUser("traveler1", "TRAVELER", userId);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_TRAVELER")));
    }

    private User existingUser(Address address) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@travel-plan.com")
                .role(Role.TRAVELER)
                .build();
        if (address != null) {
            address.setStreet("Old street");
            address.setCity("Old city");
            address.setPostalCode("Old postal");
            address.setCountry("Old country");
            address.setUser(user);
            user.setAddress(address);
        }
        return user;
    }
}
