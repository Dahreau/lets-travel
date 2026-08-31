package com.travel_plan.user_service.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_plan.user_service.domain.Role;
import com.travel_plan.user_service.exception.ApiExceptionHandler;
import com.travel_plan.user_service.exception.ForbiddenException;
import com.travel_plan.user_service.exception.UserNotFoundException;
import com.travel_plan.user_service.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        UserController controller = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void findAllReturnsAllUsers() throws Exception {
        when(userService.findAll()).thenReturn(List.of(newUserResponse("ada@travel-plan.com")));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ada@travel-plan.com"));
    }

    @Test
    void findByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.findById(eq(id), anyBoolean(), any())).thenThrow(new UserNotFoundException(id));

        mockMvc.perform(get("/api/users/{id}", id).principal(adminAuth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void findByIdReturns400ForNonUuidId() throws Exception {
        mockMvc.perform(get("/api/users/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    // fix/audit-gaps : troubleshooting.md #38 - le controller resout callerIsAdmin depuis les
    // authorities du principal et le transmet a UserService, qui fait l'application reelle.
    @Test
    void findByIdPassesAdminTrueWhenCallerHasAdminRole() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.findById(eq(id), eq(true), any())).thenReturn(newUserResponse("ada@travel-plan.com"));

        mockMvc.perform(get("/api/users/{id}", id)
                        .header("Authorization", "Bearer admin-token")
                        .principal(adminAuth()))
                .andExpect(status().isOk());

        verify(userService).findById(id, true, "Bearer admin-token");
    }

    @Test
    void findByIdPassesAdminFalseWhenCallerHasManagerRole() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.findById(eq(id), eq(false), any())).thenReturn(newUserResponse("ada@travel-plan.com"));

        mockMvc.perform(get("/api/users/{id}", id)
                        .header("Authorization", "Bearer manager-token")
                        .principal(managerAuth()))
                .andExpect(status().isOk());

        verify(userService).findById(id, false, "Bearer manager-token");
    }

    @Test
    void findByIdReturns403WhenManagerIsNotOwner() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.findById(eq(id), eq(false), any()))
                .thenThrow(new ForbiddenException("Non autorise a consulter ce profil"));

        mockMvc.perform(get("/api/users/{id}", id)
                        .header("Authorization", "Bearer manager-token")
                        .principal(managerAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReturns201ForValidRequest() throws Exception {
        when(userService.create(any(UserRequest.class), any())).thenReturn(newUserResponse("ada@travel-plan.com"));

        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null, null, null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@travel-plan.com"));
    }

    @Test
    void createReturns400ForBlankFirstName() throws Exception {
        UserRequest request = new UserRequest("", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null, null, null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // fix/audit-gaps : UserRequest.isCredentialsConsistent - un nom d'utilisateur sans mot de
    // passe (ou l'inverse) est rejete avant meme d'atteindre le service.
    @Test
    void createReturns400WhenUsernameProvidedWithoutPassword() throws Exception {
        UserRequest request = new UserRequest(
                "Marc", "Manager", "marc.manager@travel-plan.com", null, Role.TRAVEL_MANAGER, null,
                "marc.manager", null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns409WhenEmailAlreadyExists() throws Exception {
        when(userService.create(any(UserRequest.class), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null, null, null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void createReturns400ForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns500ForUnexpectedException() throws Exception {
        when(userService.create(any(UserRequest.class), any())).thenThrow(new IllegalStateException("boom"));

        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null, null, null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void registerReturns201AndForcesTravelerRole() throws Exception {
        when(userService.register(any(UserRegistrationRequest.class)))
                .thenReturn(new RegistrationResponse(newUserResponse("ada@travel-plan.com"), "test-registration-token"));

        UserRegistrationRequest request =
                new UserRegistrationRequest("Ada", "Lovelace", "ada@travel-plan.com", null, null, true);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("TRAVELER"))
                .andExpect(jsonPath("$.registrationToken").value("test-registration-token"));
    }

    @Test
    void registerReturns400ForBlankFirstName() throws Exception {
        UserRegistrationRequest request = new UserRegistrationRequest("", "Lovelace", "ada@travel-plan.com", null, null, true);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerReturns409WhenEmailAlreadyExists() throws Exception {
        when(userService.register(any(UserRegistrationRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        UserRegistrationRequest request =
                new UserRegistrationRequest("Ada", "Lovelace", "ada@travel-plan.com", null, null, true);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateReplacesUserFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.update(any(UUID.class), any(UserRequest.class)))
                .thenReturn(newUserResponse("ada.byron@travel-plan.com", "Byron"));

        UserRequest request =
                new UserRequest("Ada", "Byron", "ada.byron@travel-plan.com", "0102030405", Role.TRAVELER, null, null, null);

        mockMvc.perform(put("/api/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Byron"));
    }

    @Test
    void updateReturns404WhenUserMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.update(any(UUID.class), any(UserRequest.class))).thenThrow(new UserNotFoundException(id));

        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null, null, null);

        mockMvc.perform(put("/api/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // fix/audit-gaps (troubleshooting.md #41) : authorizationHeader desormais requis (propage a
    // AuthServiceClient pour supprimer le compte de connexion associe, voir UserService.delete).
    @Test
    void deleteRemovesExistingUser() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/users/{id}", id).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());

        verify(userService).delete(id, "Bearer admin-token");
    }

    @Test
    void deleteReturns404WhenUserMissing() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new UserNotFoundException(id)).when(userService).delete(eq(id), any());

        mockMvc.perform(delete("/api/users/{id}", id).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNotFound());
    }

    // fix/audit-gaps (troubleshooting.md #41) : droit d'acces/portabilite RGPD.
    @Test
    void meReturnsCallerProfile() throws Exception {
        when(userService.me(any())).thenReturn(newUserResponse("ada@travel-plan.com"));

        mockMvc.perform(get("/api/users/me").principal(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@travel-plan.com"));
    }

    // fix/audit-gaps (troubleshooting.md #41) : droit a l'effacement RGPD - suppression
    // self-service, meme mecanique que delete(id) ci-dessus (header propage a UserService).
    @Test
    void deleteMeRemovesCallerAccount() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer traveler-token")
                        .principal(adminAuth()))
                .andExpect(status().isNoContent());

        verify(userService).deleteMe(any(), eq("Bearer traveler-token"));
    }

    // fix/audit-gaps (troubleshooting.md #41) : compte ADMIN par defaut sans fiche User
    // (requireUserId cote UserService) - /me n'a pas de sens pour ce compte-la.
    @Test
    void meReturns403WhenCallerHasNoUserProfile() throws Exception {
        when(userService.me(any())).thenThrow(new ForbiddenException("Aucun profil utilisateur associe a ce compte"));

        mockMvc.perform(get("/api/users/me").principal(adminAuth())).andExpect(status().isForbidden());
    }

    private Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Authentication managerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "manager1", null, List.of(new SimpleGrantedAuthority("ROLE_TRAVEL_MANAGER")));
    }

    private UserResponse newUserResponse(String email) {
        return newUserResponse(email, "Lovelace");
    }

    private UserResponse newUserResponse(String email, String lastName) {
        return new UserResponse(
                UUID.randomUUID(),
                "Ada",
                lastName,
                email,
                null,
                Role.TRAVELER,
                null,
                Instant.now(),
                Instant.now(),
                null);
    }
}
