package com.travel_plan.user_service.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
import com.travel_plan.user_service.exception.UserNotFoundException;
import com.travel_plan.user_service.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
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
        when(userService.findById(id)).thenThrow(new UserNotFoundException(id));

        mockMvc.perform(get("/api/users/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void findByIdReturns400ForNonUuidId() throws Exception {
        mockMvc.perform(get("/api/users/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void createReturns201ForValidRequest() throws Exception {
        when(userService.create(any(UserRequest.class))).thenReturn(newUserResponse("ada@travel-plan.com"));

        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@travel-plan.com"));
    }

    @Test
    void createReturns400ForBlankFirstName() throws Exception {
        UserRequest request = new UserRequest("", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns409WhenEmailAlreadyExists() throws Exception {
        when(userService.create(any(UserRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

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
        when(userService.create(any(UserRequest.class))).thenThrow(new IllegalStateException("boom"));

        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void updateReplacesUserFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.update(any(UUID.class), any(UserRequest.class)))
                .thenReturn(newUserResponse("ada.byron@travel-plan.com", "Byron"));

        UserRequest request =
                new UserRequest("Ada", "Byron", "ada.byron@travel-plan.com", "0102030405", Role.TRAVELER, null);

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

        UserRequest request = new UserRequest("Ada", "Lovelace", "ada@travel-plan.com", null, Role.TRAVELER, null);

        mockMvc.perform(put("/api/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesExistingUser() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenUserMissing() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new UserNotFoundException(id)).when(userService).delete(id);

        mockMvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNotFound());
    }

    private UserResponse newUserResponse(String email) {
        return newUserResponse(email, "Lovelace");
    }

    private UserResponse newUserResponse(String email, String lastName) {
        return new UserResponse(
                UUID.randomUUID(), "Ada", lastName, email, null, Role.TRAVELER, null, Instant.now(), Instant.now());
    }
}
