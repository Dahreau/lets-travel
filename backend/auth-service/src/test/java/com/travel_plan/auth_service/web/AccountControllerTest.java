package com.travel_plan.auth_service.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.domain.Role;
import com.travel_plan.auth_service.repository.AccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
}
