package com.travel_plan.auth_service.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.domain.Role;
import com.travel_plan.auth_service.exception.InvalidRegistrationTokenException;
import com.travel_plan.auth_service.repository.AccountRepository;
import com.travel_plan.auth_service.security.JwtService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private AccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);

        AuthController controller = new AuthController(accountRepository, passwordEncoder, jwtService, clock);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void loginReturnsTokenForValidCredentials() throws Exception {
        Account account = Account.builder()
                .username("admin")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .createdAt(Instant.now())
                .build();

        when(accountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.generateToken("admin", "ADMIN", null)).thenReturn("a.b.c");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "secret"))))
                .andExpect(status().isOk());
    }

    @Test
    void loginRejectsUnknownUsername() throws Exception {
        when(accountRepository.findByUsername(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("nope", "secret"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        Account account = Account.builder()
                .username("admin")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .createdAt(Instant.now())
                .build();

        when(accountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerCreatesTravelerAccountAndReturnsToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String registrationToken = "valid-registration-token";
        when(jwtService.validateRegistrationToken(registrationToken)).thenReturn(userId);
        when(accountRepository.findByUsername("traveler1")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(jwtService.generateToken("traveler1", "TRAVELER", userId)).thenReturn("a.b.c");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("traveler1", "secret", registrationToken))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("a.b.c"));
    }

    @Test
    void registerReturns409WhenUsernameAlreadyTaken() throws Exception {
        UUID userId = UUID.randomUUID();
        String registrationToken = "valid-registration-token";
        when(jwtService.validateRegistrationToken(registrationToken)).thenReturn(userId);
        Account existing = Account.builder()
                .username("traveler1")
                .passwordHash("hashed")
                .role(Role.TRAVELER)
                .userId(userId)
                .createdAt(Instant.now())
                .build();
        when(accountRepository.findByUsername("traveler1")).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("traveler1", "secret", registrationToken))))
                .andExpect(status().isConflict());
    }

    @Test
    void registerReturns400ForBlankUsername() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("", "secret", "valid-registration-token"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerReturns400ForInvalidRegistrationToken() throws Exception {
        String registrationToken = "forged-or-expired-token";
        when(jwtService.validateRegistrationToken(registrationToken))
                .thenThrow(new InvalidRegistrationTokenException("Jeton d'inscription invalide ou expire"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("traveler1", "secret", registrationToken))))
                .andExpect(status().isBadRequest());
    }
}
