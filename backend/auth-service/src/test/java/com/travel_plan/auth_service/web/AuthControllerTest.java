package com.travel_plan.auth_service.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.domain.Role;
import com.travel_plan.auth_service.repository.AccountRepository;
import com.travel_plan.auth_service.security.JwtService;
import java.time.Instant;
import java.util.Optional;
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

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);

        AuthController controller = new AuthController(accountRepository, passwordEncoder, jwtService);
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
}
