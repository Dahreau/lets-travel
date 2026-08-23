package com.travel_plan.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.payment_service.client.TravelServiceClient;
import com.travel_plan.payment_service.client.TravelSummary;
import com.travel_plan.payment_service.domain.MethodType;
import com.travel_plan.payment_service.domain.Payment;
import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.domain.PaymentStatus;
import com.travel_plan.payment_service.domain.ProviderType;
import com.travel_plan.payment_service.exception.ForbiddenException;
import com.travel_plan.payment_service.exception.InvalidPaymentRequestException;
import com.travel_plan.payment_service.exception.InvalidRefundException;
import com.travel_plan.payment_service.exception.PaymentMethodNotFoundException;
import com.travel_plan.payment_service.exception.PaymentNotFoundException;
import com.travel_plan.payment_service.provider.ChargeResult;
import com.travel_plan.payment_service.provider.PaymentProvider;
import com.travel_plan.payment_service.provider.PaymentProviderResolver;
import com.travel_plan.payment_service.repository.PaymentMethodRepository;
import com.travel_plan.payment_service.repository.PaymentRepository;
import com.travel_plan.payment_service.security.AuthenticatedUser;
import com.travel_plan.payment_service.web.PaymentRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private static final String AUTH_HEADER = "Bearer test-token";

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentMethodRepository paymentMethodRepository = mock(PaymentMethodRepository.class);
    private final PaymentProviderResolver paymentProviderResolver = mock(PaymentProviderResolver.class);
    private final TravelServiceClient travelServiceClient = mock(TravelServiceClient.class);
    private final PaymentService paymentService = new PaymentService(
            paymentRepository, paymentMethodRepository, paymentProviderResolver, travelServiceClient);

    @Test
    void createChargesResolvedProviderUsingPriceFromTravelService() {
        AuthenticatedUser traveler = traveler();
        UUID methodId = UUID.randomUUID();
        UUID travelId = UUID.randomUUID();
        PaymentMethod method = PaymentMethod.builder()
                .id(methodId)
                .ownerId(traveler.userId())
                .provider(ProviderType.STRIPE)
                .type(MethodType.CARD)
                .providerToken("pm_card_visa")
                .build();
        PaymentProvider stripeProvider = mock(PaymentProvider.class);
        when(paymentMethodRepository.findById(methodId)).thenReturn(Optional.of(method));
        when(travelServiceClient.getPricedTravel(travelId, AUTH_HEADER))
                .thenReturn(new TravelSummary(travelId, new BigDecimal("450.00"), "eur"));
        when(paymentProviderResolver.resolve(ProviderType.STRIPE)).thenReturn(stripeProvider);
        when(stripeProvider.charge(any())).thenReturn(new ChargeResult("pi_123", PaymentStatus.SUCCEEDED));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentRequest request = new PaymentRequest(travelId, null, methodId);
        Payment created = paymentService.create(request, traveler, AUTH_HEADER);

        assertThat(created.getOwnerId()).isEqualTo(traveler.userId());
        assertThat(created.getAmount()).isEqualByComparingTo("450.00");
        assertThat(created.getCurrency()).isEqualTo("EUR");
        assertThat(created.getProvider()).isEqualTo(ProviderType.STRIPE);
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(created.getProviderReference()).isEqualTo("pi_123");
    }

    @Test
    void createThrowsWhenPaymentMethodMissing() {
        UUID methodId = UUID.randomUUID();
        when(paymentMethodRepository.findById(methodId)).thenReturn(Optional.empty());

        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), null, methodId);

        assertThatThrownBy(() -> paymentService.create(request, traveler(), AUTH_HEADER))
                .isInstanceOf(PaymentMethodNotFoundException.class);
    }

    @Test
    void createThrowsForbiddenWhenPaymentMethodNotOwnedByCaller() {
        UUID methodId = UUID.randomUUID();
        PaymentMethod method = PaymentMethod.builder().id(methodId).ownerId(UUID.randomUUID()).build();
        when(paymentMethodRepository.findById(methodId)).thenReturn(Optional.of(method));

        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), null, methodId);

        assertThatThrownBy(() -> paymentService.create(request, traveler(), AUTH_HEADER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createThrowsWhenAdminOmitsOwnerId() {
        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), null, UUID.randomUUID());

        assertThatThrownBy(() -> paymentService.create(request, admin(), AUTH_HEADER))
                .isInstanceOf(InvalidPaymentRequestException.class);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findById(id, admin())).isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void findByIdThrowsForbiddenWhenNotOwner() {
        UUID id = UUID.randomUUID();
        Payment payment = Payment.builder().id(id).ownerId(UUID.randomUUID()).build();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.findById(id, traveler())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void findByIdSucceedsForOwner() {
        AuthenticatedUser traveler = traveler();
        UUID id = UUID.randomUUID();
        Payment payment = Payment.builder().id(id).ownerId(traveler.userId()).build();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        assertThat(paymentService.findById(id, traveler)).isEqualTo(payment);
    }

    @Test
    void refundMarksSucceededPaymentAsRefunded() {
        UUID id = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(id)
                .status(PaymentStatus.SUCCEEDED)
                .provider(ProviderType.STRIPE)
                .providerReference("pi_123")
                .build();
        PaymentProvider stripeProvider = mock(PaymentProvider.class);
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));
        when(paymentProviderResolver.resolve(ProviderType.STRIPE)).thenReturn(stripeProvider);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment refunded = paymentService.refund(id);

        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(stripeProvider).refund("pi_123");
    }

    @Test
    void refundThrowsWhenPaymentNotSucceeded() {
        UUID id = UUID.randomUUID();
        Payment payment = Payment.builder().id(id).status(PaymentStatus.FAILED).build();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refund(id)).isInstanceOf(InvalidRefundException.class);
    }

    @Test
    void refundDoesNotChangeStatusWhenProviderRefundFails() {
        UUID id = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(id)
                .status(PaymentStatus.SUCCEEDED)
                .provider(ProviderType.STRIPE)
                .providerReference("pi_123")
                .build();
        PaymentProvider stripeProvider = mock(PaymentProvider.class);
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));
        when(paymentProviderResolver.resolve(ProviderType.STRIPE)).thenReturn(stripeProvider);
        doThrow(new IllegalStateException("Stripe refund failed")).when(stripeProvider).refund("pi_123");

        assertThatThrownBy(() -> paymentService.refund(id)).isInstanceOf(IllegalStateException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void findAllDelegatesToRepository() {
        when(paymentRepository.findAll()).thenReturn(List.of(Payment.builder().build()));

        assertThat(paymentService.findAll()).hasSize(1);
    }

    private AuthenticatedUser admin() {
        return new AuthenticatedUser("admin@travel-plan.com", "ADMIN", null);
    }

    private AuthenticatedUser traveler() {
        return new AuthenticatedUser("traveler1", "TRAVELER", UUID.randomUUID());
    }
}
