package com.travel_plan.payment_service.web;

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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.travel_plan.payment_service.domain.MethodType;
import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.domain.ProviderType;
import com.travel_plan.payment_service.exception.ApiExceptionHandler;
import com.travel_plan.payment_service.exception.PaymentMethodNotFoundException;
import com.travel_plan.payment_service.service.PaymentMethodService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PaymentMethodControllerTest {

    private PaymentMethodService paymentMethodService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        paymentMethodService = mock(PaymentMethodService.class);
        PaymentMethodController controller = new PaymentMethodController(paymentMethodService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void findAllReturnsAllPaymentMethods() throws Exception {
        when(paymentMethodService.findAll()).thenReturn(List.of(newPaymentMethod()));

        mockMvc.perform(get("/api/payment-methods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brand").value("visa"))
                .andExpect(jsonPath("$[0].last4").value("4242"));
    }

    @Test
    void findAllDoesNotExposeProviderToken() throws Exception {
        when(paymentMethodService.findAll()).thenReturn(List.of(newPaymentMethod()));

        mockMvc.perform(get("/api/payment-methods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].providerToken").doesNotExist());
    }

    @Test
    void findByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentMethodService.findById(id)).thenThrow(new PaymentMethodNotFoundException(id));

        mockMvc.perform(get("/api/payment-methods/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void findByIdReturns400ForNonUuidId() throws Exception {
        mockMvc.perform(get("/api/payment-methods/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void createReturns201ForValidRequest() throws Exception {
        when(paymentMethodService.create(any(PaymentMethodRequest.class))).thenReturn(newPaymentMethod());

        mockMvc.perform(post("/api/payment-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brand").value("visa"));
    }

    @Test
    void createReturns400WhenProviderTokenMissing() throws Exception {
        PaymentMethodRequest request = new PaymentMethodRequest(
                UUID.randomUUID(), ProviderType.STRIPE, MethodType.CARD, "", "visa", "4242", true);

        mockMvc.perform(post("/api/payment-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns409WhenDataIntegrityViolation() throws Exception {
        when(paymentMethodService.create(any(PaymentMethodRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        mockMvc.perform(post("/api/payment-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void updateReturns200ForValidRequest() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentMethodService.update(any(UUID.class), any(PaymentMethodRequest.class)))
                .thenReturn(newPaymentMethod());

        mockMvc.perform(put("/api/payment-methods/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("visa"));
    }

    @Test
    void updateReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentMethodService.update(any(UUID.class), any(PaymentMethodRequest.class)))
                .thenThrow(new PaymentMethodNotFoundException(id));

        mockMvc.perform(put("/api/payment-methods/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesExistingPaymentMethod() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/payment-methods/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new PaymentMethodNotFoundException(id)).when(paymentMethodService).delete(id);

        mockMvc.perform(delete("/api/payment-methods/{id}", id)).andExpect(status().isNotFound());
    }

    private PaymentMethod newPaymentMethod() {
        return PaymentMethod.builder()
                .id(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .provider(ProviderType.STRIPE)
                .type(MethodType.CARD)
                .providerToken("pm_card_visa")
                .brand("visa")
                .last4("4242")
                .isDefault(true)
                .createdAt(Instant.parse("2026-07-01T10:00:00Z"))
                .build();
    }

    private PaymentMethodRequest validRequest() {
        return new PaymentMethodRequest(
                UUID.randomUUID(), ProviderType.STRIPE, MethodType.CARD, "pm_card_visa", "visa", "4242", true);
    }
}
