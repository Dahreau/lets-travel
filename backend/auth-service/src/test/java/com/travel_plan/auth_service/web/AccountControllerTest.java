package com.travel_plan.auth_service.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.domain.Role;
import com.travel_plan.auth_service.repository.AccountRepository;
import com.travel_plan.auth_service.security.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountControllerTest {

    private AccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);

        AccountController controller = new AccountController(accountRepository, passwordEncoder);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createsManagerAccountLinkedToAUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/auth/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("manager1", "secret", Role.TRAVEL_MANAGER, userId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("manager1"))
                .andExpect(jsonPath("$.role").value("TRAVEL_MANAGER"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void rejectsManagerAccountWithoutUserId() throws Exception {
        mockMvc.perform(post("/api/auth/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("manager1", "secret", Role.TRAVEL_MANAGER, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAdminAccountWithUserId() throws Exception {
        mockMvc.perform(post("/api/auth/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("admin2", "secret", Role.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsAdminAccountWithoutUserId() throws Exception {
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/auth/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("admin2", "secret", Role.ADMIN, null))))
                .andExpect(status().isCreated());
    }

    // fix/audit-gaps (troubleshooting.md #41) : self-service - un traveler peut supprimer SON
    // PROPRE compte de connexion (appele par user-service/AuthServiceClient, DELETE /api/users/me).
    @Test
    void deleteByUserIdRemovesOwnAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        Account account = Account.builder()
                .username("traveler1")
                .passwordHash("hashed")
                .role(Role.TRAVELER)
                .userId(userId)
                .build();
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));

        mockMvc.perform(delete("/api/auth/accounts/by-user/{userId}", userId).principal(travelerAuth(userId)))
                .andExpect(status().isNoContent());

        verify(accountRepository).delete(account);
    }

    // fix/audit-gaps (troubleshooting.md #41) : admin - meme endpoint, appele quand un ADMIN
    // supprime le profil de quelqu'un d'autre (DELETE /api/users/{id}).
    @Test
    void deleteByUserIdAllowsAdminToRemoveSomeoneElsesAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        Account account = Account.builder()
                .username("traveler1")
                .passwordHash("hashed")
                .role(Role.TRAVELER)
                .userId(userId)
                .build();
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));

        mockMvc.perform(delete("/api/auth/accounts/by-user/{userId}", userId).principal(adminAuth()))
                .andExpect(status().isNoContent());

        verify(accountRepository).delete(account);
    }

    // fix/audit-gaps (troubleshooting.md #41) : meme classe de garde que le fix IDOR #38 - ni
    // ADMIN, ni proprietaire du userId cible -> 403, pas de suppression.
    @Test
    void deleteByUserIdReturns403WhenCallerIsNeitherOwnerNorAdmin() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID someoneElseId = UUID.randomUUID();

        mockMvc.perform(delete("/api/auth/accounts/by-user/{userId}", userId).principal(travelerAuth(someoneElseId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteByUserIdReturns404WhenAccountAlreadyDeleted() throws Exception {
        UUID userId = UUID.randomUUID();
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/auth/accounts/by-user/{userId}", userId).principal(adminAuth()))
                .andExpect(status().isNotFound());
    }

    private Authentication travelerAuth(UUID userId) {
        AuthenticatedUser principal = new AuthenticatedUser("traveler1", "TRAVELER", userId);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_TRAVELER")));
    }

    private Authentication adminAuth() {
        AuthenticatedUser principal = new AuthenticatedUser("admin", "ADMIN", null);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
