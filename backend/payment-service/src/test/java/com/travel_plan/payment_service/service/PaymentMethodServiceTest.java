package com.travel_plan.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.payment_service.domain.MethodType;
import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.domain.ProviderType;
import com.travel_plan.payment_service.exception.ForbiddenException;
import com.travel_plan.payment_service.exception.InvalidPaymentRequestException;
import com.travel_plan.payment_service.exception.PaymentMethodNotFoundException;
import com.travel_plan.payment_service.repository.PaymentMethodRepository;
import com.travel_plan.payment_service.security.AuthenticatedUser;
import com.travel_plan.payment_service.web.PaymentMethodRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentMethodServiceTest {

    private final PaymentMethodRepository paymentMethodRepository = mock(PaymentMethodRepository.class);
    private final PaymentMethodService paymentMethodService = new PaymentMethodService(paymentMethodRepository);

    @Test
    void findAllReturnsEverythingForAdmin() {
        when(paymentMethodRepository.findAll()).thenReturn(List.of(PaymentMethod.builder().build()));

        assertThat(paymentMethodService.findAll(admin())).hasSize(1);
    }

    @Test
    void findAllReturnsOnlyOwnedForTraveler() {
        AuthenticatedUser traveler = traveler();
        when(paymentMethodRepository.findByOwnerId(traveler.userId()))
                .thenReturn(List.of(PaymentMethod.builder().ownerId(traveler.userId()).build()));

        assertThat(paymentMethodService.findAll(traveler)).hasSize(1);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(paymentMethodRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentMethodService.findById(id, admin()))
                .isInstanceOf(PaymentMethodNotFoundException.class);
    }

    @Test
    void findByIdThrowsForbiddenWhenNotOwner() {
        UUID id = UUID.randomUUID();
        PaymentMethod method = PaymentMethod.builder().id(id).ownerId(UUID.randomUUID()).build();
        when(paymentMethodRepository.findById(id)).thenReturn(Optional.of(method));

        assertThatThrownBy(() -> paymentMethodService.findById(id, traveler()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void findByIdSucceedsForOwner() {
        AuthenticatedUser traveler = traveler();
        UUID id = UUID.randomUUID();
        PaymentMethod method = PaymentMethod.builder().id(id).ownerId(traveler.userId()).build();
        when(paymentMethodRepository.findById(id)).thenReturn(Optional.of(method));

        assertThat(paymentMethodService.findById(id, traveler)).isEqualTo(method);
    }

    @Test
    void createForcesOwnerIdToCallerForTraveler() {
        AuthenticatedUser traveler = traveler();
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethod created = paymentMethodService.create(requestWithOwnerId(null), traveler);

        assertThat(created.getOwnerId()).isEqualTo(traveler.userId());
        assertThat(created.getProvider()).isEqualTo(ProviderType.STRIPE);
        assertThat(created.getType()).isEqualTo(MethodType.CARD);
        assertThat(created.getProviderToken()).isEqualTo("pm_card_visa");
        assertThat(created.getLast4()).isEqualTo("4242");
        assertThat(created.isDefault()).isTrue();
    }

    @Test
    void createThrowsWhenAdminOmitsOwnerId() {
        assertThatThrownBy(() -> paymentMethodService.create(requestWithOwnerId(null), admin()))
                .isInstanceOf(InvalidPaymentRequestException.class);
    }

    @Test
    void createUsesRequestedOwnerIdForAdmin() {
        UUID ownerId = UUID.randomUUID();
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethod created = paymentMethodService.create(requestWithOwnerId(ownerId), admin());

        assertThat(created.getOwnerId()).isEqualTo(ownerId);
    }

    @Test
    void updateReplacesFieldsOnExistingPaymentMethodForOwner() {
        AuthenticatedUser traveler = traveler();
        UUID id = UUID.randomUUID();
        PaymentMethod existing = PaymentMethod.builder()
                .id(id)
                .ownerId(traveler.userId())
                .provider(ProviderType.STRIPE)
                .type(MethodType.CARD)
                .providerToken("pm_old")
                .isDefault(false)
                .build();
        when(paymentMethodRepository.findById(id)).thenReturn(Optional.of(existing));
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentMethod updated = paymentMethodService.update(id, requestWithOwnerId(null), traveler);

        assertThat(updated.getProviderToken()).isEqualTo("pm_card_visa");
        assertThat(updated.isDefault()).isTrue();
        assertThat(updated.getOwnerId()).isEqualTo(traveler.userId());
    }

    @Test
    void updateThrowsForbiddenWhenNotOwner() {
        UUID id = UUID.randomUUID();
        PaymentMethod existing = PaymentMethod.builder().id(id).ownerId(UUID.randomUUID()).build();
        when(paymentMethodRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentMethodService.update(id, requestWithOwnerId(null), traveler()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteRemovesExistingPaymentMethodForOwner() {
        AuthenticatedUser traveler = traveler();
        UUID id = UUID.randomUUID();
        PaymentMethod existing = PaymentMethod.builder().id(id).ownerId(traveler.userId()).build();
        when(paymentMethodRepository.findById(id)).thenReturn(Optional.of(existing));

        paymentMethodService.delete(id, traveler);

        verify(paymentMethodRepository).delete(existing);
    }

    @Test
    void deleteThrowsForbiddenWhenNotOwner() {
        UUID id = UUID.randomUUID();
        PaymentMethod existing = PaymentMethod.builder().id(id).ownerId(UUID.randomUUID()).build();
        when(paymentMethodRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentMethodService.delete(id, traveler()))
                .isInstanceOf(ForbiddenException.class);
    }

    private PaymentMethodRequest requestWithOwnerId(UUID ownerId) {
        return new PaymentMethodRequest(ownerId, ProviderType.STRIPE, MethodType.CARD, "pm_card_visa", "visa", "4242", true);
    }

    private AuthenticatedUser admin() {
        return new AuthenticatedUser("admin@travel-plan.com", "ADMIN", null);
    }

    private AuthenticatedUser traveler() {
        return new AuthenticatedUser("traveler1", "TRAVELER", UUID.randomUUID());
    }
}
