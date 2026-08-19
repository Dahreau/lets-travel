package com.travel_plan.payment_service.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.travel_plan.payment_service.domain.MethodType;
import com.travel_plan.payment_service.domain.Payment;
import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.domain.PaymentStatus;
import com.travel_plan.payment_service.domain.ProviderType;
import com.travel_plan.payment_service.exception.ApiExceptionHandler;
import com.travel_plan.payment_service.exception.InvalidRefundException;
import com.travel_plan.payment_service.exception.PaymentMethodNotFoundException;
import com.travel_plan.payment_service.exception.PaymentNotFoundException;
import com.travel_plan.payment_service.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PaymentControllerTest {

    private PaymentService paymentService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        PaymentController controller = new PaymentController(paymentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void findAllReturnsAllPayments() throws Exception {
        when(paymentService.findAll()).thenReturn(List.of(newPayment(PaymentStatus.SUCCEEDED)));

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].currency").value("EUR"));
    }

    @Test
    void findByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.findById(id)).thenThrow(new PaymentNotFoundException(id));

        mockMvc.perform(get("/api/payments/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void findByIdReturns400ForNonUuidId() throws Exception {
        mockMvc.perform(get("/api/payments/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void refundReturns400ForNonUuidId() throws Exception {
        mockMvc.perform(post("/api/payments/{id}/refund", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void createReturns201ForValidRequest() throws Exception {
        when(paymentService.create(any(PaymentRequest.class))).thenReturn(newPayment(PaymentStatus.SUCCEEDED));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void createReturns400WhenAmountNotPositive() throws Exception {
        PaymentRequest request = new PaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("-10.00"), "EUR");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns404WhenPaymentMethodMissing() throws Exception {
        UUID methodId = UUID.randomUUID();
        when(paymentService.create(any(PaymentRequest.class)))
                .thenThrow(new PaymentMethodNotFoundException(methodId));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void refundReturns200ForSucceededPayment() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.refund(id)).thenReturn(newPayment(PaymentStatus.REFUNDED));

        mockMvc.perform(post("/api/payments/{id}/refund", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void refundReturns409WhenPaymentNotSucceeded() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.refund(id)).thenThrow(new InvalidRefundException(id));

        mockMvc.perform(post("/api/payments/{id}/refund", id)).andExpect(status().isConflict());
    }

    private Payment newPayment(PaymentStatus status) {
        PaymentMethod method = PaymentMethod.builder()
                .id(UUID.randomUUID())
                .provider(ProviderType.STRIPE)
                .type(MethodType.CARD)
                .providerToken("pm_card_visa")
                .build();
        return Payment.builder()
                .id(UUID.randomUUID())
                .travelId(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .paymentMethod(method)
                .amount(new BigDecimal("99.90"))
                .currency("EUR")
                .provider(ProviderType.STRIPE)
                .status(status)
                .providerReference("pi_123")
                .createdAt(Instant.parse("2026-07-01T10:00:00Z"))
                .updatedAt(Instant.parse("2026-07-01T10:00:00Z"))
                .build();
    }

    private PaymentRequest validRequest() {
        return new PaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("99.90"), "EUR");
    }
}
